/*
 * Copyright (C) 2018  Philip Standt
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

/**
 * To get notified about state changes, error or new messages of a {@link ArcusSocket},
 * a <b>SocketListener</b> can be added by calling
 * {@link ArcusSocket#addListener(SocketListener)}.
 * <p>
 * <p>If added to a socket, the declared functions get triggered when the
 * corresponding event occurs.
 */
public interface SocketListener {

    /**
     * Fires when the state of this {@link ArcusSocket} changes
     *
     * @param socket   socket, whose state changes
     * @param newState new state of this socket
     */
    void stateChanged(ArcusSocket socket, SocketState newState);

    /**
     * Fired when this socket received and correctly parsed
     * a new message.
     * <p>
     * <p>The received message is explicitly not passed. It can instead
     * be obtained by calling {@link ArcusSocket#takeNextMessage()}.
     *
     * @param socket socket, that received the message
     */
    void messageReceived(ArcusSocket socket);

    /**
     * Fired when an error occurs on this socket
     *
     * @param socket socket that the error occurred on
     * @param error  the error that occurred
     */
    void error(ArcusSocket socket, Error error);
}
