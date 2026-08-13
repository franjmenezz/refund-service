package com.comercia.refundservice.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Este DTO es "inmutable" (todos los campos son final, sin setters): una vez construido, no cambia.
// Es intencionado: una respuesta ya enviada no debería poder mutarse por accidente en ningún punto del código.
// También es una barrera contra "Excessive Data Exposure" (OWASP API3): solo se serializan estos 6 campos,
// nunca la entidad RefundRecord completa (que podría tener en el futuro campos internos que no queremos exponer).
public class RefundResponse {

    private final UUID refundId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final String status;
    private final Instant createdAt;

    public RefundResponse(UUID refundId, UUID paymentId, BigDecimal amount, String currency, String status, Instant createdAt) {
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}