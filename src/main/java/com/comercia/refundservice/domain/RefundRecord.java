package com.comercia.refundservice.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro persistido de una operación de devolución, indexado por Idempotency-Key.
 * requestHash permite detectar si un reintento con la misma key trae un payload distinto
 * (409 Conflict) o idéntico (200 OK, respuesta cacheada). Ver SPEC.md, sección 4, para el algoritmo completo.
 */
@Entity
@Table(name = "refund_records", uniqueConstraints = {
        // Esta constraint es la última barrera de seguridad contra dobles reembolsos: aunque hubiera un fallo
        // de lógica en el servicio, la propia base de datos rechazaría dos filas con la misma idempotencyKey.
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotencyKey")
})

public class RefundRecord {

    // Identificador propio del refund (lo que se devuelve al cliente como "refundId"), distinto de la key.
    @Id
    private UUID refundId;

    // La Idempotency-Key que envió el cliente en el header. Es el campo por el que se busca en cada reintento.
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID paymentId;

    // Hash SHA-256 del payload (amount + currency + reason), calculado por RequestHasher.
    // Comparar este hash es lo que distingue un reintento legítimo (200) de un conflicto (409).
    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // De momento solo existe el estado "COMPLETED" (no hay reversión ni estados intermedios en este alcance).
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    // Constructor vacío para Hibernate (ver la misma explicación que en Payment.java).
    protected RefundRecord() {
    }

    public RefundRecord(UUID refundId, String idempotencyKey, UUID paymentId, String requestHash,
                         BigDecimal amount, String currency, String status, Instant createdAt) {
        this.refundId = refundId;
        this.idempotencyKey = idempotencyKey;
        this.paymentId = paymentId;
        this.requestHash = requestHash;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getRequestHash() {
        return requestHash;
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