/*
 *   Copyright (C) 2016  Philip Standt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.ocarthon.libArcus;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * ArcusSocket creates a socket in a thread to send and receive message based on the
 * <a target="_blank" href="https://developers.google.com/protocol-buffers/">Protocol
 * Buffers</a> library developed by Google. This implementation is based on
 * <a target="_blank" href="https://github.com/Ultimaker/libArcus">libArcus</a> by
 * ltimaker and resembles the original implementation as close as possible.
 * <p>
 * <h1>Protocol</h1> The protocol used consists of four parts: <ul> <li><b>Header</b>:
 * {@code 0x28BAD0100} (32 bits) containing the signature ({@code 0x2BAD}), major protocol
 * version ({@code 0x01}) and minor protocol version ({@code 0x00}).</li> <li><b>Size</b>:
 * 32 bits containing the size of the serialized protobuf message</li> <li><b>Type</b>: 3
 * 2 bits containing the
 * <a target="_blank" href="https://tools.ietf.org/html/draft-eastlake-fnv-11">FNV-1a</a>
 * hash of the full protobuf message typename. This typename consists of the protobuf
 * package (defined in the protobuf file) and the name of the message concatenated
 * together.</li> <li><b>Message</b>: the serialized message. Number of bytes is equal to
 * the defined size</li> </ul>
 * <p>
 * <p>There are also two special headers. A keep-alive header ({@code 0x00000000}) is
 * being sent every 250 ms to check if the connection is still alive and a close header
 * ({@code 0xF0F0F0F0}) is sent when the connection is closing.
 * <p>
 * <h1>SocketListener</h1> A SocketListener can be added via
 * {@link #addListener(SocketListener)} and removed by calling
 * {@link #removeListener(SocketListener)}. All registered listeners get notified when:
 * <ul> <li>a new message has been received:
 * {@link SocketListener#messageReceived(ArcusSocket)}</li> <li>the socket's state
 * changes: {@link SocketListener#stateChanged(ArcusSocket, SocketState)}</li> <li>an
 * error occurs: {@link SocketListener#error(ArcusSocket, Error)}</li> </ul>
 * SocketListeners can only be added or removed when the socket is in it's initial state.
 * <p>
 * <h1>Registering message types</h1> New message types can be registered as long as the
 * socket is in it's initial state. By calling {@link #registerMessageType(Message)} the
 * message type (more particularly the parser) will be stored to identify and parse
 * incoming messages. This method returns a boolean value indicating if the type has been
 * added.
 * <p>
 * <h1>Using the socket</h1> The socket can either be started as a server or a client. By
 * calling {@link #listen(int)}, it listens on the given port and waits for a client to
 * connect. If the port is set to {@code 0}, a port will automatically be allocated. With
 * {@link #connect(String, int)}, a connection to the given address will be established.
 * All other methods affect the socket in both cases in the same way.
 * <p>
 * <p>Once the socket is connected it will send and receive message on another thread.
 * Messages can be sent by calling {@link #sendMessage(Message)}. They will be added to an
 * internal Queue. The separate Thread will check for messages in the Queue and send them
 * to the other side. After all messages are sent it tries to read incoming messages. If a
 * valid message has been received and successfully parsed, all registered
 * {@link SocketListener}s will getnotified and the message gets added to the internal
 * Queue. Messages can be chronically obtainedby successive calls to
 * {@link #takeNextMessage()}.
 * <p>
 * <h1>Possible socket states</h1> In the life-cycle of an ArcusSocket, it can go through
 * the following states: <ul> <li><b>Initial</b>: The socket has not yet been started. At
 * this point SocketListeners can be modified and message types can be added</li> <li>
 * <b>Connecting</b>: The socket has been started with a call to
 * {@link #connect(String, int)} and is not connecting to the given address
 * </li> <li><b>Opening</b>: The socket has been started with a call to
 * {@link #listen(int)} and is starting a internal {@link ServerSocket}</li> <li>
 * <b>Listening</b>: The internal ServerSocket is waiting for a client to connect
 * </li> <li><b>Connected</b>: The socket is connected and messages can be sent and
 * received</li> <li><b>Closing</b>: The socket is closing the connection. Before actually
 * closing the connection, all messages in the queue are being sent and the socket waits
 * for a response from the other side</li> <li><b>Closed</b>: The socket has successfully
 * closed the connection</li> <li><b>Error</b>: An error occurred and the socket got shut
 * down</li> </ul>
 * <p>
 * <h1>Errors</h1> If an error occurs, all registered listeners get notified. In case of a
 * fatal error, the socket will be forcefully closed. Every error contains a
 * {@link Error.ErrorCode ErrorCode} and a message describing what happened.
 * <p>
 * <h1>Reusing the socket</h1> If the socket has been closed or was forcefully closed and
 * in now in an error state, it can be reset by calling {@link #reset()}. The socket will
 * be reset to default values and can now be used as if it was newly constructed.
 * SocketListeners and message types will not be reset and remain the same.
 *
 * @author Philip Standt
 */
public class ArcusSocket {

    /**
     * Signature of ArcusSocket used in the header of the protocol
     */
    private static final int ARCUS_SIGNATURE = 0x2BAD;

    /**
     * Major protocol version
     */
    private static final int MAJOR_VERSION = 0x01;

    /**
     * Minor protocol version
     */
    private static final int MINOR_VERSION = 0x00;

    /**
     * Queue that contains all messages that have not yet been sent
     */
    private final Queue<Message> sendQueue = new LinkedList<>();

    /**
     * Queue that stores received messages
     */
    private final Queue<Message> receiveQueue = new LinkedList<>();
    /**
     * Storage for message types and their corresponding parser
     */
    private MessageTypeStore messageTypeStore = new MessageTypeStore();

    /**
     * List of listeners to notify them if needed
     */
    private List<SocketListener> listeners = new ArrayList<>();

    /**
     * Internal thread used by the socket to send and receive messages
     */
    private Thread thread;

    /**
     * Target address the sockets connects to if started via {@link #connect(String, int)}
     */
    private String address;

    /**
     * Port the sockets connects to or listens on
     */
    private int port;

    /**
     * The actual socket used for communication
     */
    private volatile Socket socket;

    /**
     * ServerSocket that listens for an incoming connection
     */
    private volatile ServerSocket serverSocket;

    /**
     * current state of the socket
     */
    private volatile SocketState state = SocketState.Initial;

    /**
     * State that the socket will be in when the current operations regarding the current
     * {@link #state} are finished
     */
    private volatile SocketState nextState;

    public ArcusSocket() {
    }

    /**
     * Registers a new message type to receive and parse messages of the same type later.
     * <p>
     * <p>Although you can use any instance of a message type, it is recommended for
     * readability and null-safety purposes to use the default instance
     * ({@link Message#getDefaultInstanceForType()}) of the message type.
     *
     * @param message the message whose type will be registered
     * @return True if the message type got registered. False if message is {@code null},
     * the socket is not in it's initial state or the type has already been registered.
     */
    public boolean registerMessageType(Message message) {
        if (message == null) {
            return false;
        }

        if (state != SocketState.Initial) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return false;
        }

        return messageTypeStore.registerType(message);
    }

    /**
     * Adds a listener to the internal List. If events like state changes occur, the
     * corresponding methods will be called. This will only work if the socket is in the
     * initial state. Otherwise an error will occur.
     *
     * @param listener SocketListener that will be added
     */
    public void addListener(SocketListener listener) {
        if (state != SocketState.Initial) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return;
        }

        listeners.add(listener);
    }

    /**
     * Removes a listener from the internal list. This will only work if the socket is in
     * the initial state. Otherwise an error will occur.
     *
     * @param listener SocketListener that will be removed
     */
    public void removeListener(SocketListener listener) {
        if (state != SocketState.Initial) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return;
        }

        listeners.remove(listener);
    }

    /**
     * Removes all listeners from the internal list. This will only work if the socket is
     * in the initial state. Otherwise an error will occur.
     */
    public void removeAllListeners() {
        if (state != SocketState.Initial) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return;
        }

        listeners.clear();
    }

    /**
     * Connects to a remote address. This is only possible if the socket is in the initial
     * state. Otherwise an will occur. Additional checks are in place to ensure the
     * arguments are valid. The address must not be null and the port must be valid
     * (0 &le; port &le; 65535).
     *
     * @param address remote address
     * @param port    remote port
     */
    public void connect(String address, int port) {
        if (this.state != SocketState.Initial || this.thread != null) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return;
        }

        if (address == null) {
            error(Error.ErrorCode.InvalidArgumentError, "Address is null");
            return;
        }

        if (port < 0 || port > 65535) {
            error(Error.ErrorCode.InvalidArgumentError, "Port is out of range");
            return;
        }

        this.address = address;
        this.port = port;
        this.nextState = SocketState.Connecting;

        this.thread = new Thread(new InternalRunnable());
        this.thread.start();
    }

    /**
     * Listens for a new incoming connection on the specified port. This is only possible
     * if the socket is in the initial state and the port is valid (0 &le; port &le;
     * 65535). Otherwise an will occur. The socket will go into the
     * {@link SocketState#Opening Opening} state.
     * <p>
     * <p>If the specified port is 0, the port number is automatically allocated,
     * typically from an ephermeral port range. This port number can then be retrieved by
     * calling {@link #getPort}.
     *
     * @param port port to listen on. If 0, the port is automatically allocated.
     */
    public void listen(int port) {
        if (this.state != SocketState.Initial || this.thread != null) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in initial state");
            return;
        }

        if (port < 0 || port > 65535) {
            error(Error.ErrorCode.InvalidArgumentError, "Port is out of range");
            return;
        }

        this.port = port;
        this.nextState = SocketState.Opening;

        this.thread = new Thread(new InternalRunnable());
        this.thread.start();
    }

    /**
     * Adds a message to the internal send queue.
     *
     * @param message message to be sent
     */
    public void sendMessage(Message message) {
        if (message == null) {
            error(Error.ErrorCode.InvalidMessageError, "Message cannot be null");
            return;
        }

        synchronized (sendQueue) {
            sendQueue.offer(message);
        }
    }

    /**
     * Polls the first message in the receive queue. This message is then no longer in the
     * queue.
     * <p>
     * <p>Returns null if there are no new messages
     *
     * @return message from receive queue. Null if no new messages are present
     */
    public Message takeNextMessage() {
        synchronized (receiveQueue) {
            if (receiveQueue.size() > 0) {
                return receiveQueue.poll();
            } else {
                return null;
            }
        }
    }

    /**
     * Resets the socket and the socket's state to use it again. This is only possible if
     * the socket is closed or a fatal error occurred. SocketListeners and message types
     * will not be reset and remain the same.
     */
    public void reset() {
        if (this.state != SocketState.Closed && this.state != SocketState.Error) {
            error(Error.ErrorCode.InvalidStateError, "Socket is not in closed or error state");
            return;
        }

        if (this.thread != null) {
            try {
                this.thread.join();
                this.thread = null;
            } catch (InterruptedException e) {
                error(Error.ErrorCode.ThreadInterruptError, "Thread has been interrupted");
            }
        }

        this.state = SocketState.Initial;
        this.state = SocketState.Initial;
    }

    /**
     * Closes the socket. All messages in the send queue will be sent before finally
     * sending a closing message.
     */
    public void close() {
        if (this.state == SocketState.Initial) {
            error(Error.ErrorCode.InvalidStateError, "Cannot close a socket in initial state");
        }

        if (this.state == SocketState.Closed || this.state == SocketState.Error) {
            return;
        }

        if (this.state == SocketState.Connected) {
            this.nextState = SocketState.Closing;

            while (this.state != SocketState.Closed) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    error(Error.ErrorCode.ThreadInterruptError, "Thread has been interrupted");
                }
            }
        } else {
            closeSocket();

            this.nextState = SocketState.Closed;
        }
        if (thread != null) {
            try {
                thread.join();
                thread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Closes the {@link #socket Socket} and {@link #serverSocket ServerSocket}.
     */
    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            fatalError(Error.ErrorCode.SocketCloseError, "Error while closing socket");
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                fatalError(Error.ErrorCode.SocketCloseError, "Error while closing socket");
            }
        }
    }

    /**
     * Returns the current state the socket is in
     *
     * @return current state of the socket
     * @see ArcusSocket
     */
    public SocketState getState() {
        return this.state;
    }

    /**
     * This will return the ports specified by calls to {@link #listen(int)} or
     * {@link #connect(String, int)}. If none ob them have been called, {@code 0} will be
     * returned.
     * <p>
     * <p>If {@link #listen(int)} has been called with the port {@code 0}, this will
     * return the automatically allocated port
     *
     * @return port that is used for the connection.
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    /**
     * Creates a error for the given parameters and notifies all registered listeners
     *
     * @param errorCode    type of error
     * @param errorMessage message of the error (e.g. detailed information of the cause)
     */
    private void error(Error.ErrorCode errorCode, String errorMessage) {
        Error error = new Error(errorCode, errorMessage);

        // Notify all registered listeners about this error
        for (SocketListener listener : listeners) {
            listener.error(this, error);
        }
    }

    /**
     * Creates a <b>fatal</b> error for the given parameters. The socket will be
     * forcefully closed and the internal thread stopped. All registered listeners will
     * be notified.
     *
     * @param errorCode    type of error
     * @param errorMessage message of the error (e.g. detailed information of the cause)
     */
    private void fatalError(Error.ErrorCode errorCode, String errorMessage) {
        Error error = new Error(errorCode, errorMessage, true);

        if (state != SocketState.Error && nextState != SocketState.Error) {
            nextState = SocketState.Error;
            try {
                // Stop Socket and (if created) ServerSocket. The thread will
                // automatically stop after the next iteration
                socket.close();
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                // Ignore, at this point it does not matter
            }
        }

        // Notify all registered listeners about this error
        for (SocketListener listener : listeners) {
            listener.error(this, error);
        }
    }

    private class InternalRunnable implements Runnable {
        /**
         * Rate at which keep-alive headers are sent (in ms).
         */
        private static final int keepAliveRate = 250;
        /**
         * Socket closes header (0xF0F0F0F0)
         */
        private final byte[] SOCKET_CLOSE = new byte[]{(byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0};
        /**
         * Protocol header, constructed out of
         * {@link ArcusSocket#ARCUS_SIGNATURE ARCUS_SIGNATURE},
         * {@link ArcusSocket#MAJOR_VERSION MAJOR_VERSION} and
         * {@link ArcusSocket#MINOR_VERSION MINOR_VERSION}.
         */
        private final byte[] ARCUS_HEADER = new byte[]{(ARCUS_SIGNATURE >> 8) & 0xFF, (byte) (ARCUS_SIGNATURE & 0xFF), MAJOR_VERSION, MINOR_VERSION};
        /**
         * Keep-alive header (0x00000000)
         */
        private final byte[] KEEP_ALIVE = new byte[]{0x00, 0x00, 0x00, 0x00};
        /**
         * timestamp of the last keep-alive message that has been sent
         */
        private long lastKeepAlive = 0;

        /**
         * OutputStream of the socket to send data
         */
        private OutputStream out;

        /**
         * InputStream of the socket to receive data
         */
        private InputStream in;

        /**
         * If the other side sent a close request. This is used if the socket is in the
         * {@link SocketState#Closing Closing} state to determine what to do
         */
        private boolean receivedClose = false;


        // The following fields are used to receive a message. Since only one message is
        // received at a time, we can just use global variables instead of a new object.
        /**
         * Current state of the received message.<br/>
         * 0 - ARCUS_HEADER<br/>
         * 1 - SIZE<br/>
         * 2 - TYPE<br/>
         * 3 - MESSAGE<br/>
         */
        private int messageState = 0;

        /**
         * Offset / bytes written of the byte array that is currently used
         */
        private int currentOffset = 0;

        /**
         * holds the header of a new received message. Fixed length of 4 (32 bits)
         */
        private byte[] header = new byte[4];

        /**
         * holds the length of a new received message. Fixed length of 4 (32 bits)
         */
        private byte[] length = new byte[4];

        /**
         * holds the type of a new received message. Fixed length of 4 (32 bits)
         */
        private byte[] type = new byte[4];

        /**
         * holds the actual data of a new received message. Variable length depending on
         * the {@link InternalRunnable#length length} of the message.
         */
        private byte[] message;

        /**
         * Internal loop that handles creation of sockets, connecting, sending / receiving
         * messages and closing the socket
         */
        @Override
        public void run() {
            // Run as long as the socket is not closed and no fatal error occurred
            while (state != SocketState.Closed && state != SocketState.Error) {
                switch (state) {
                    case Connecting:
                        try {
                            // Create socket and set Input and OutputStream to send and receive messages
                            socket = new Socket(address, port);
                            out = socket.getOutputStream();
                            in = socket.getInputStream();
                        } catch (UnknownHostException e) {
                            fatalError(Error.ErrorCode.ConnectFailedError, "Could not connect to the given address");
                            continue;
                        } catch (IOException e) {
                            fatalError(Error.ErrorCode.CreationError, "Could not create a socket");
                            continue;
                        }

                        // Set timeout time for socket to 250ms. This way a read operation
                        // can at max take 250ms before returning. This the thread does not
                        // get blocked.
                        try {
                            socket.setSoTimeout(250);
                        } catch (SocketException e) {
                            fatalError(Error.ErrorCode.ConnectFailedError, "Failed to set socket receive timeout");
                            continue;
                        }

                        nextState = SocketState.Connected;
                        break;

                    case Opening:
                        // Created the ServerSocket
                        try {
                            serverSocket = new ServerSocket(port);
                        } catch (BindException e) {
                            fatalError(Error.ErrorCode.BindFailedError, "Could not bind to the given port");
                        } catch (IOException e) {
                            fatalError(Error.ErrorCode.CreationError, "Could not create a socket. Maybe the port is already in use");
                            continue;
                        }

                        // Set timeout time for ServerSocket. That's needed to stop the thread
                        // if no client has connected.
                        try {
                            serverSocket.setSoTimeout(250);
                        } catch (SocketException e) {
                            fatalError(Error.ErrorCode.ConnectFailedError, "Failed to set socket receive timeout");
                            continue;
                        }

                        nextState = SocketState.Listening;
                        break;

                    case Listening:
                        // Listen for an incoming connection
                        try {
                            socket = serverSocket.accept();
                            out = socket.getOutputStream();
                            in = socket.getInputStream();
                        } catch (SocketTimeoutException e) {
                            continue;
                        } catch (IOException e) {
                            fatalError(Error.ErrorCode.AcceptFailedError, "Could not accept the incoming connection");
                            continue;
                        }

                        // See above
                        try {
                            socket.setSoTimeout(250);
                        } catch (SocketException e) {
                            fatalError(Error.ErrorCode.AcceptFailedError, "Failed to set socket receive timeout");
                            continue;
                        }

                        nextState = SocketState.Connected;
                        break;

                    case Connected:
                        // Send messages in queue

                        Message[] messages;

                        // Put all messages from the queue in a thread local array to not
                        // block access to the queue
                        synchronized (sendQueue) {
                            messages = new Message[sendQueue.size()];
                            messages = sendQueue.toArray(messages);
                            sendQueue.clear();
                        }

                        // Send every message that was in the queue
                        for (Message message : messages) {
                            this.sendMessage(message);
                        }

                        // Receive the next message
                        receiveNextMessage();

                        // check if we have to send a keep-alive message
                        if (nextState != SocketState.Error) {
                            checkConnectionState();
                        }

                        break;

                    case Closing:
                        // Did we instantiate the close
                        if (!receivedClose) {
                            // If so, first send all messages in the queue
                            synchronized (sendQueue) {
                                messages = new Message[sendQueue.size()];
                                messages = sendQueue.toArray(messages);
                                sendQueue.clear();
                            }

                            for (Message message : messages) {
                                this.sendMessage(message);
                            }

                            // Then send a close connection message
                            try {
                                out.write(SOCKET_CLOSE);
                                out.flush();
                                socket.shutdownOutput();

                                // And wait for a response from the other side with the
                                // same message
                                byte[] data = new byte[4];
                                int i = 0;
                                while (!Arrays.equals(data, SOCKET_CLOSE) && nextState == SocketState.Closing) {
                                    if (i == data.length) {
                                        i = 0;
                                    }

                                    i += in.read(data, i, data.length - i);
                                }
                            } catch (IOException e) {
                                break;
                            }
                        } else {
                            // If the other side wants to close, all messages in the queue
                            // get cleared
                            synchronized (sendQueue) {
                                sendQueue.clear();
                            }

                            // And we send a close connection message back
                            writeBytes(SOCKET_CLOSE);
                        }

                        // Finally the socket gets closed
                        closeSocket();

                        nextState = SocketState.Closed;
                        break;

                    default:
                        break;
                }

                // Update state variable and notify listeners about the state change
                if (nextState != state) {
                    state = nextState;

                    for (SocketListener listener : listeners) {
                        listener.stateChanged(ArcusSocket.this, state);
                    }
                }
            }
        }

        /**
         * Sends the message to the other side
         *
         * @param message message to be sent
         */
        private void sendMessage(Message message) {
            // Write header
            try {
                out.write(ARCUS_HEADER);
            } catch (IOException e) {
                error(Error.ErrorCode.SendFailedError, "Could not send message header");
                return;
            }

            // Write message size
            try {
                writeInt(message.getSerializedSize());
            } catch (IOException e) {
                error(Error.ErrorCode.SendFailedError, "Could not send message size");
                return;
            }

            // Write hash of message type
            try {
                writeInt(messageTypeStore.getMessageTypeId(message));
            } catch (IOException e) {
                error(Error.ErrorCode.SendFailedError, "Could not send message type");
                return;
            }

            // Write the actual message
            try {
                out.write(message.toByteArray());
            } catch (IOException e) {
                error(Error.ErrorCode.SendFailedError, "Could not send message data");
            }
        }

        /**
         * Receives the next message. If no new data is available, this method will return
         * and continue reading if called again.
         */
        private void receiveNextMessage() {
            // HEADER
            if (messageState == 0) {
                // Read until we have all 4 bytes
                if ((currentOffset = read(header, currentOffset)) != header.length) {
                    return;
                }
                if (Arrays.equals(header, KEEP_ALIVE)) {
                    // Just a keep-alive message
                    return;
                } else if (Arrays.equals(header, SOCKET_CLOSE)) {
                    // The other side wants to close the connection
                    nextState = SocketState.Closing;
                    receivedClose = true;
                    return;
                }

                if (!Arrays.equals(header, ARCUS_HEADER)) {
                    // Invalid header
                    error(Error.ErrorCode.ReceiveFailedError, "Header mismatch");
                    currentOffset = 0;
                    return;
                }

                currentOffset = 0;
                messageState = 1;
            }

            // SIZE
            if (messageState == 1) {
                // Read until we have all 4 bytes
                if ((currentOffset = read(length, currentOffset)) != length.length) {
                    return;
                }

                // convert byte[] to int
                int size = bytesToInt(length);

                if (size < 0) {
                    error(Error.ErrorCode.ReceiveFailedError, "Size invalid");
                    currentOffset = 0;
                    messageState = 0;
                    return;
                }

                // allocate array for the following message data
                message = new byte[size];
                currentOffset = 0;
                messageState = 2;
            }

            // TYPE
            if (messageState == 2) {
                if ((currentOffset = read(type, currentOffset)) != type.length) {
                    return;
                }

                // Checking for a valid type will be done in the next step. Even if the
                // type is unknown, the message data was sent and must now be received.

                currentOffset = 0;
                messageState = 3;
            }

            // MESSAGE DATA
            if (messageState == 3) {
                if ((currentOffset = read(message, currentOffset)) != message.length) {
                    return;
                }

                currentOffset = 0;
                messageState = 0;

                // parse message and notify listeners
                handleMessage(type, message);
            }
        }

        /**
         * Parses new incoming messages and notifies all registered listeners
         *
         * @param type type of the message
         * @param data data of the message
         */
        private void handleMessage(byte[] type, byte[] data) {
            int typeHash = bytesToInt(type);

            if (!messageTypeStore.hasType(typeHash)) {
                error(Error.ErrorCode.UnknownMessageTypeError, "Unknown message type");
            }

            try {
                // Parse message and add it to the receive queue
                Message obj = messageTypeStore.parse(typeHash, data);
                synchronized (receiveQueue) {
                    receiveQueue.offer(obj);
                }

                // notify all registered handler about the new message
                for (SocketListener listener : listeners) {
                    listener.messageReceived(ArcusSocket.this);
                }
            } catch (InvalidProtocolBufferException e) {
                // sent type does not correspond to the message that has been sent
                error(Error.ErrorCode.ParseFailedError, "Type mismatch");
            } catch (Exception e) {
                error(Error.ErrorCode.ParseFailedError, "Failed to parse message");
            }
        }

        /**
         * Sends a keep-alive message of needed. An connection reset is assumed if an
         * exception occurs while sending the message.
         */
        private void checkConnectionState() {
            long now = System.currentTimeMillis();
            long diff = now - lastKeepAlive;

            if (diff > keepAliveRate) {
                try {
                    out.write(KEEP_ALIVE);
                    out.flush();
                } catch (IOException e) {
                    error(Error.ErrorCode.ConnectionResetError, "Connection reset by peer");
                    // Close sockets directly. This way the loop won't try to send a close
                    // message
                    closeSocket();
                    nextState = SocketState.Closed;
                }

                lastKeepAlive = now;
            }
        }

        /**
         * Reads an 32-bit integer from a byte array
         *
         * @param in byte array
         * @return integer
         */
        private int bytesToInt(byte[] in) {
            return ((in[0] & 0xFF) << 24) | ((in[1] & 0xFF) << 16) | ((in[2] & 0xFF) << 8) | (in[3] & 0xFF);
        }

        private void writeInt(int in) throws IOException {
            out.write(new byte[]{(byte) ((in >> 24) & 0xFF), (byte) ((in >> 16) & 0xFF), (byte) ((in >> 8) & 0xFF), (byte) (in & 0xFF)});
        }

        private void writeBytes(byte[] data) {
            try {
                out.write(data);
            } catch (IOException e) {
                error(Error.ErrorCode.ConnectionResetError, "Connection reset by peer");
                closeSocket();
                nextState = SocketState.Closed;
            }
        }

        /**
         * Reads data from the {@link #in InputStream} to the {@code byte[]} at the given
         * offset. This method will read between 0 and {@code data.length - offset} bytes.
         *
         * @param data data array that the bytes will be written to
         * @param off  offset at which new data will be written
         * @return new offset
         */
        private int read(byte[] data, int off) {
            try {
                return off + in.read(data, off, data.length - off);
            } catch (IOException e) {
                fatalError(Error.ErrorCode.UnknownError, "Error while reading from socket");
                return -1;
            }
        }
    }
}
