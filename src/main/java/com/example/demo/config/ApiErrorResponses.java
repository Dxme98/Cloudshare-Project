package com.example.demo.config;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class ApiErrorResponses {


    /**
     * Standard-Fehler, die fast jeder Endpoint werfen kann (400, 500).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Ungültige Eingabedaten oder fehlende Parameter",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error - Unerwarteter Fehler auf dem Server",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface StandardErrors {}



    /**
     * Fehler für geschützte Endpoints (401, 403).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Token fehlt oder ist ungültig",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Zugriff verweigert (z.B. falsche Rolle)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface SecuredEndpoints {}


    /**
     * Wenn eine Ressource (Ordner, Datei) nicht gefunden wurde (404).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Ordner, Datei, User existiert nicht oder Token passt nicht",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface NotFound {}

    /**
     * Wenn der Speicherplatz voll ist oder die Datei zu groß ist (413).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "413",
                    description = "Payload Too Large - Speicherplatz voll oder Datei zu groß",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface StorageFull {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "503",
                    description = "Service Unavailable - Datenbank/S3 temporär nicht erreichbar",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface DatabaseError {}

    /**
     * Wenn der Share-Token ungültig, manipuliert oder abgelaufen ist (403).
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Der übergebene Token ist ungültig, abgelaufen oder hat nicht die ausreichenden Rechte",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public @interface InvalidToken {}
}