package com.example.demo.exceptions;

import java.time.LocalDateTime;
import java.util.Map;


public record ErrorResponse(String errorCode, String message, LocalDateTime timestamp, Map<String, String> validationErrors) {
}
