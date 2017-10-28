/*
 * Copyright (C) 2017  Philip Standt
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


public class Error {
    private final ErrorCode errorCode;
    private final String errorMessage;
    private final boolean fatal;

    public Error(ErrorCode errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.fatal = false;
    }

    public Error(ErrorCode errorCode, String errorMessage, boolean fatal) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.fatal = fatal;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isFatal() {
        return fatal;
    }

    public enum ErrorCode {
        UnknownError,
        CreationError,
        ConnectFailedError,
        InvalidArgumentError,
        BindFailedError,
        AcceptFailedError,
        SendFailedError,
        ReceiveFailedError,
        UnknownMessageTypeError,
        ParseFailedError,
        ConnectionResetError,
        InvalidStateError,
        InvalidMessageError,
        ThreadInterruptError,
        SocketCloseError
    }
}
