package com.comercia.refundservice.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representa un pago ya procesado sobre el cual pueden solicitarse devoluciones.
 * En este servicio de demo, los pagos se siembran al arrancar (Ver DataSeeder.java),
 * porque crear pagos esta fuera del alcance de esta prueba (ver SPEC.md, seccion 6)
 */

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;

    // Importe total que se pagó originalmente. Es un valor fijo, nunca cambia tras crearse el pago.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    // Código de moneda ISO 4217 (ej. "EUR", "USD"). Toda devolución sobre este pago debe usar esta misma moneda.
    @Column(nullable = false, length = 3)
    private String currency;

    // Suma acumulada de todas las devoluciones ya aplicadas a este pago. Empieza en 0 y solo puede crecer.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // Constructor vacío obligatorio para que Hibernate pueda reconstruir el objeto por reflexión al leer de BD.
    // Lo dejamos "protected" (no "public") para que nadie fuera de este paquete cree un Payment sin datos.
    protected Payment() {
    }

    // Este es el único constructor "real": obliga a proporcionar los 3 datos que definen un pago válido.
    public Payment(UUID id, BigDecimal originalAmount, String currency) {
        this.id = id;
        this.originalAmount = originalAmount;
        this.currency = currency;
        this.refundedAmount = BigDecimal.ZERO;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    // Cuánto queda todavía disponible para devolver. Es lo que consulta RefundService antes de aprobar un refund.
    public BigDecimal getAvailableAmount() {
        return originalAmount.subtract(refundedAmount);
    }

    // Suma el importe de un nuevo refund al acumulado. Se llama solo DESPUÉS de validar que hay saldo suficiente;
    // este método en sí no valida nada, confía en que quien lo invoca (RefundService) ya comprobó las reglas.
    public void applyRefund(BigDecimal amount) {
        this.refundedAmount = this.refundedAmount.add(amount);
    }
}