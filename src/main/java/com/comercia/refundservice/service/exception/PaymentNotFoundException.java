package com.comercia.refundservice.service.exception;

import java.util.UUID;

// Se lanza cuando el payment_id del path no existe en la base de datos. → 404 Not Found
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID paymentId) {
        super("No existe ningún pago con id " + paymentId);
    }
}