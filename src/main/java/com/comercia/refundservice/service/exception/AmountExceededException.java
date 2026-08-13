package com.comercia.refundservice.service.exception;

// Se lanza cuando el importe solicitado supera el saldo disponible del pago. → 422 Unprocessable Entity
public class AmountExceededException extends RuntimeException {
    public AmountExceededException(String message) {
        super(message);
    }
}