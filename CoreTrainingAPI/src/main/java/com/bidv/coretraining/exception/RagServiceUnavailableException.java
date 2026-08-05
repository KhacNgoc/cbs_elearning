package com.bidv.coretraining.exception;

public class RagServiceUnavailableException extends RuntimeException {
    public RagServiceUnavailableException(String message) {
        super(message);
    }

    public RagServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
