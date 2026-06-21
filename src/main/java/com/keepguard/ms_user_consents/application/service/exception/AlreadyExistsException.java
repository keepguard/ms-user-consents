package com.keepguard.ms_user_consents.application.service.exception;

public class AlreadyExistsException extends RuntimeException {
    
    public AlreadyExistsException(String message) {
        super(message);
    }
    
    public AlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}

