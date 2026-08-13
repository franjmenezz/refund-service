package com.comercia.refundservice.service.exception;

// Se lanza cuando PciSanitizer detecta un patrón de PAN/CVV en el campo 'reason'. → 400 Bad Request
public class SensitiveDataDetectedException extends RuntimeException {
    public SensitiveDataDetectedException(String message) {
        super(message);
    }
}