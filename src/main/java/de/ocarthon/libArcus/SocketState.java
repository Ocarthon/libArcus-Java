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
 * Represents all possible states a {@link ArcusSocket} can be in.
 * The state of a created ArcusSocket can be obtained by calling
 * {@link ArcusSocket#getState()}.
 * <p>
 * <p>If a {@link SocketListener} has been added by calling
 * {@link ArcusSocket#addListener(SocketListener)},
 * {@link SocketListener#error(ArcusSocket, de.ocarthon.libArcus.Error)}
 * will be called when the state of this socket changes
 * <p>
 * <p>More detailed documentation to the different states can be found in {@link ArcusSocket}.
 */
public enum SocketState {
    Initial,
    Connecting,
    Connected,
    Opening,
    Listening,
    Closing,
    Closed,
    Error
}
