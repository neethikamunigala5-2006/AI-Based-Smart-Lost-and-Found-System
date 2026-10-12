package com.example.backend.exception;

import org.springframework.http.HttpStatus;

public class ImageUploadException extends RuntimeException {

    private final HttpStatus status;

    public ImageUploadException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
