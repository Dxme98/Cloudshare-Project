package com.example.demo.exceptions;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("The provided token is invalid for this operation.");
    }
}
