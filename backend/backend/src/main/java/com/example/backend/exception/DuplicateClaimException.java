package com.example.backend.exception;

public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(String message) {
        super(message);
    }
}
