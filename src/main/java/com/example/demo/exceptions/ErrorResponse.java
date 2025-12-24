package com.example.demo.exceptions;

import java.time.LocalDateTime;


public record ErrorResponse(String errorCode, String message, LocalDateTime timestamp) {
}
