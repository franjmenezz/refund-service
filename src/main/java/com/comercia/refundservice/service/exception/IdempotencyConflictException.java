package com.comercia.refundservice.service.exception;

// Se lanza cuando la misma Idempotency-Key llega con un payload distinto al de la primera vez. → 409 Conflict
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}