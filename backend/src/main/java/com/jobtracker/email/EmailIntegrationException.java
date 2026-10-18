package com.jobtracker.email;

import org.springframework.http.HttpStatus;

public class EmailIntegrationException extends RuntimeException {
    private final HttpStatus status;

    public EmailIntegrationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
