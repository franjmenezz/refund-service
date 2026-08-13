package com.comercia.refundservice.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Calcula un hash SHA-256 determinista del payload lógico de una petición de refund,
 * usado para decidir si un reintento con la misma Idempotency-Key es realmente
 * el mismo payload (200 OK) o uno distinto (409 Conflict).
 */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(UUID paymentId, BigDecimal amount, String currency, String reason) {
        // "Canonicalizamos" los datos antes de hashear: normalizamos formato (mayúsculas, sin espacios extra,
        // sin ceros decimales sobrantes) para que 10.0 y 10.00, o "eur" y "EUR", generen EXACTAMENTE el mismo
        // hash. Si no hiciéramos esto, un reintento legítimo con formato ligeramente distinto se detectaría
        // erróneamente como un conflicto (409) en vez de como el mismo payload (200).
        String canonical = paymentId + "|" +
                amount.stripTrailingZeros().toPlainString() + "|" +
                currency.trim().toUpperCase() + "|" +
                (reason == null ? "" : reason.trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            // Convertimos los bytes del hash a una cadena hexadecimal legible (64 caracteres), que es
            // lo que se guarda en la columna requestHash de RefundRecord.
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en la JVM estándar; si no lo estuviera, es un fallo de entorno crítico.
            throw new IllegalStateException("Algoritmo de hashing no disponible", e);
        }
    }
}