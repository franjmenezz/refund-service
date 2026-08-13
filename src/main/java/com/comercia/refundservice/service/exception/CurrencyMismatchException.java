package com.comercia.refundservice.service.exception;

// Se lanza cuando la moneda del refund no coincide con la del pago original. → 422 Unprocessable Entity
public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}