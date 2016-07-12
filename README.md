libArcus-Java
=============

libArcus is a lightweight, easy to use library to send and receive messages between two programs based on the [Protocol Buffers](https://developers.google.com/protocol-buffers/) library by Google. It was originally developed by Ultimaker and is primarily used to communicate with their CuraEngine. This is a port of the [original library](https://github.com/Ultimaker/libArcus/) to the programming language Java. The API is almost identical to make working with both libraries more easy.

Usage
-----
```java
// Create a new instance
ArcusSocket socket = new ArcusSocket();

// Add a listener
socket.addListener(...);

// Register a message type
socket.registerMessageType(MyType.getDefaultInstanceForType());

// Start listening on the specified port
socket.listen(...);

// Send a messages
socket.sendMessage(...);

// Retrieve a received message
Message message = socket.takeNextMessage();

// Check of message type
if (message != null && message instanceof MyType) {
    doStuff((MyType) message);
}

// Close the connection
socket.close();
```