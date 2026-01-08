package com.example.demo.exceptions.custom;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("Kein User mit Email " + email + " gefunden.");
    }
}