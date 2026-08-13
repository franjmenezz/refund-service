package com.comercia.refundservice.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class RefundRequest {

    // @NotNull: el campo tiene que existir en el JSON. @DecimalMin: además, tiene que ser mayor que 0.
    // Estas anotaciones las procesa Spring automáticamente gracias a @Valid en el controller,
    // ANTES de que el código llegue a RefundService — así el service nunca recibe datos a medio validar.
    @NotNull(message = "El campo 'amount' es obligatorio.")
    @DecimalMin(value = "0.01", message = "El campo 'amount' debe ser mayor que 0.")
    private BigDecimal amount;

    // El regex ^[A-Z]{3}$ exige exactamente 3 letras mayúsculas (formato ISO 4217, ej. "EUR", "USD").
    @NotBlank(message = "El campo 'currency' es obligatorio.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "El campo 'currency' debe ser un código ISO 4217 de 3 letras mayúsculas.")
    private String currency;

    // Nota importante: aquí SOLO se valida longitud/presencia. La detección de PAN/CVV (PciSanitizer)
    // se hace más adelante, dentro de RefundService, porque es una regla de negocio/seguridad,
    // no una simple validación de formato.
    @NotBlank(message = "El campo 'reason' es obligatorio.")
    @Size(max = 250, message = "El campo 'reason' no puede superar los 250 caracteres.")
    private String reason;

    // Constructor vacío: Jackson (la librería que convierte JSON <-> Java) lo necesita para
    // crear el objeto y luego rellenarlo campo a campo con los setters.
    public RefundRequest() {
    }

    public RefundRequest(BigDecimal amount, String currency, String reason) {
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}