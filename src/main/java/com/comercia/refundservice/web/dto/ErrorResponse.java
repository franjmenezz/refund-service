package com.comercia.refundservice.web.dto;

import java.time.Instant;

// Mismo formato de error para TODOS los casos (401, 404, 409, 422...) — así el cliente de la API
// siempre sabe qué estructura esperar, sin tener que manejar formatos distintos según el código HTTP.
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error; // Código corto tipo "AMOUNT_EXCEEDED" — pensado para que el CLIENTE lo use programáticamente.
    private final String message; // Texto en español, pensado para mostrarse a un humano o loguearse.

    public ErrorResponse(int status, String error, String message) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}