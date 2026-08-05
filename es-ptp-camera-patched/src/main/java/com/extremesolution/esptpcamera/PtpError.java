/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.extremesolution.esptpcamera;

/**
 * Error type hierarchy for PTP layer errors.
 * Provides typed error handling instead of string-based error messages.
 */
public abstract class PtpError {
    private final String message;
    private final Exception cause;

    protected PtpError(String message) {
        this(message, null);
    }

    protected PtpError(String message, Exception cause) {
        this.message = message;
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public Exception getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + message;
    }

    // --- Specific error types ---

    public static final class UsbDisconnected extends PtpError {
        public UsbDisconnected() {
            super("USB device disconnected");
        }

        public UsbDisconnected(String details) {
            super("USB device disconnected: " + details);
        }
    }

    public static final class UsbPermissionDenied extends PtpError {
        public UsbPermissionDenied() {
            super("USB permission denied");
        }
    }

    public static final class DeviceBusy extends PtpError {
        public DeviceBusy() {
            super("Camera is busy");
        }
    }

    public static final class ProtocolError extends PtpError {
        private final int responseCode;

        public ProtocolError(int responseCode) {
            super("PTP error: " + PtpConstants.responseToString(responseCode));
            this.responseCode = responseCode;
        }

        public ProtocolError(int responseCode, String details) {
            super("PTP error: " + PtpConstants.responseToString(responseCode) + " - " + details);
            this.responseCode = responseCode;
        }

        public int getResponseCode() {
            return responseCode;
        }
    }

    public static final class OutOfMemory extends PtpError {
        public OutOfMemory() {
            super("Out of memory decoding image");
        }
    }

    public static final class Timeout extends PtpError {
        public Timeout() {
            super("USB communication timed out");
        }

        public Timeout(String operation) {
            super("USB communication timed out during: " + operation);
        }
    }

    public static final class CommandFailed extends PtpError {
        public CommandFailed(String commandName, Exception cause) {
            super("Command failed: " + commandName, cause);
        }
    }

    public static final class ImageRetrievalFailed extends PtpError {
        private final int objectHandle;

        public ImageRetrievalFailed(int objectHandle, String reason) {
            super(reason);
            this.objectHandle = objectHandle;
        }

        public int getObjectHandle() {
            return objectHandle;
        }
    }
}
