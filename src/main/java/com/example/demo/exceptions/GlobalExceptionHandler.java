package com.example.demo.exceptions;

import com.example.demo.exceptions.custom.*;
import io.awspring.cloud.s3.S3Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // --- Business Exceptions ---

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        return buildResponse("INVALID_TOKEN", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFolderNotFound(FolderNotFoundException ex) {
        return buildResponse("FOLDER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FileMetadataNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(FileMetadataNotFoundException ex) {
        return buildResponse("FILE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StorageLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleStorageLimitExceeded(StorageLimitExceededException ex) {
        return buildResponse("STORAGE_LIMIT_EXCEEDED", ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE); // 413 statt 400
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse("USER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponse> handleFileUpload(FileUploadException ex) {
        log.error("File upload failed", ex); // Wichtig für Debugging
        return buildResponse("FILE_UPLOAD_FAILED", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Spring Security Exception ---

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse("ACCESS_DENIED", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    // --- Spring Framework Exceptions ---

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse("FILE_TOO_LARGE", "Die Datei ist zu groß. Maximal erlaubte Größe überschritten.", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse("INVALID_INPUT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", fieldErrors); // WARN statt INFO

        ErrorResponse error = new ErrorResponse(
                "VALIDATION_ERROR",
                "Validierung fehlgeschlagen",
                LocalDateTime.now(),
                fieldErrors
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // --- Catch-All für unerwartete Fehler ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex); // WICHTIG: Stacktrace loggen
        return buildResponse(
                "INTERNAL_ERROR",
                "Ein unerwarteter Fehler ist aufgetreten. Bitte versuche es später erneut.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<ErrorResponse> handleDynamoDb(DynamoDbException ex) {
        log.error("DynamoDB error: {}", ex.getMessage(), ex);
        return buildResponse("DATABASE_ERROR", "Datenbank vorübergehend nicht verfügbar", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<ErrorResponse> handleS3Error(S3Exception ex) {
        log.error("S3 error: {}", ex.getMessage(), ex);
        return buildResponse("STORAGE_ERROR", "Speicher vorübergehend nicht verfügbar", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // --- Helper ---

    private ResponseEntity<ErrorResponse> buildResponse(String errorCode, String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(errorCode, message, LocalDateTime.now(), null);
        return new ResponseEntity<>(error, status);
    }
}