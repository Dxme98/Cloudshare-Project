package com.example.demo.exceptions.custom;

public class UserAlreadyHasAccessException extends RuntimeException {
    public UserAlreadyHasAccessException(String email) {
        super("User with email " + email + " hat bereits access.");
    }
}
