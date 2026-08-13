package com.comercia.refundservice.service.exception;

// Se lanza cuando el header Idempotency-Key no tiene formato UUIDv4 válido. → 400 Bad Request
public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}