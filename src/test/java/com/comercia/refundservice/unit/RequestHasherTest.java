package com.comercia.refundservice.unit;

import com.comercia.refundservice.util.RequestHasher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RequestHasherTest {

    // Este test confirma la propiedad más básica de un hash determinista: mismos datos de entrada
    // -> mismo hash de salida, siempre. Es la base de todo el mecanismo de idempotencia.
    @Test
    void mismoPayloadProduceElMismoHash() {
        UUID paymentId = UUID.randomUUID();
        String h1 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo");
        String h2 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo");
        assertEquals(h1, h2);
    }

    // Si esto fallara (dos motivos distintos dieran el mismo hash), significaría que RefundService
    // podría confundir dos peticiones DIFERENTES como si fueran la misma -> fallo de seguridad grave.
    @Test
    void payloadsDistintosProducenHashesDistintos() {
        UUID paymentId = UUID.randomUUID();
        String h1 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo A");
        String h2 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo B");
        assertNotEquals(h1, h2);
    }

    @Test
    void diferenciaEnElImporteProduceHashDistinto() {
        UUID paymentId = UUID.randomUUID();
        String h1 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo");
        String h2 = RequestHasher.hash(paymentId, new BigDecimal("10.01"), "EUR", "motivo");
        assertNotEquals(h1, h2);
    }

    // Este es el test que "protege" la corrección que hicimos con stripTrailingZeros() en RequestHasher.
    // Sin esa normalización, este test fallaría, y eso significaría reintentos legítimos con formato
    // ligeramente distinto (10.0 vs 10.00) se detectarían erróneamente como conflicto (409).
    @Test
    void formatosEquivalentesDeImporteProducenElMismoHash() {
        // 10.0 y 10.00 representan la misma cantidad -> no deberían generar falsos conflictos
        UUID paymentId = UUID.randomUUID();
        String h1 = RequestHasher.hash(paymentId, new BigDecimal("10.0"), "EUR", "motivo");
        String h2 = RequestHasher.hash(paymentId, new BigDecimal("10.00"), "EUR", "motivo");
        assertEquals(h1, h2);
    }
}