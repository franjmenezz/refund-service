package com.comercia.refundservice.service;

import com.comercia.refundservice.domain.Payment;
import com.comercia.refundservice.domain.RefundRecord;
import com.comercia.refundservice.repository.PaymentRepository;
import com.comercia.refundservice.repository.RefundRecordRepository;
import com.comercia.refundservice.service.exception.*;
import com.comercia.refundservice.util.PciSanitizer;
import com.comercia.refundservice.util.RequestHasher;
import com.comercia.refundservice.web.dto.RefundRequest;
import com.comercia.refundservice.web.dto.RefundResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RefundService {

    // Regex de UUID v4: el "4" fijo en la tercera sección y el "8/9/a/b" al principio de la cuarta
    // son marcas de posición específicas de la versión 4 del estándar UUID (no cualquier UUID vale).
    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final PaymentRepository paymentRepository;
    private final RefundRecordRepository refundRecordRepository;

    // Inyección de dependencias por constructor: Spring nos pasa automáticamente instancias
    // de estos dos repositorios al crear el RefundService. Es el patrón recomendado en Spring
    // (mejor que @Autowired en los campos), porque hace explícito qué necesita esta clase para funcionar.
    public RefundService(PaymentRepository paymentRepository, RefundRecordRepository refundRecordRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRecordRepository = refundRecordRepository;
    }

    /**
     * Procesa una solicitud de devolución de forma idempotente.
     * Ver SPEC.md, sección 4, para el algoritmo completo en formato documentación.
     */
    // @Transactional: todo lo que ocurre dentro de este método (leer, bloquear, escribir) se ejecuta
    // como una única unidad atómica. Si algo falla a mitad (una excepción), Spring deshace TODO
    // (rollback), así nunca queda el Payment actualizado sin su RefundRecord correspondiente, o viceversa.
    @Transactional
    public RefundOutcome processRefund(UUID paymentId, String idempotencyKey, RefundRequest request) {

        // Paso 0: validaciones que no dependen de consultar la base de datos (formato de la key, PCI).
        // Se hacen las primeras porque son las más "baratas" y evitan tocar la BD si van a fallar igualmente.
        validateIdempotencyKeyFormat(idempotencyKey);
        validateNoSensitiveData(request.getReason());

        // Calculamos el hash del payload UNA VEZ, lo reutilizamos tanto para comparar como para guardar.
        String requestHash = RequestHasher.hash(paymentId, request.getAmount(), request.getCurrency(), request.getReason());

        // Paso 1: ¿ya existe un registro para esta Idempotency-Key? (algoritmo de SPEC.md sección 4)
        var existing = refundRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            RefundRecord record = existing.get();
            if (record.getRequestHash().equals(requestHash)) {
                // Mismo payload -> devolvemos la respuesta que ya se generó la primera vez.
                // OJO: no se vuelve a tocar Payment ni a validar nada; es una simple lectura.
                return new RefundOutcome(toResponse(record), false);
            } else {
                // Misma key, payload distinto -> conflicto. No se modifica ningún estado.
                throw new IdempotencyConflictException(
                        "La Idempotency-Key '" + idempotencyKey + "' ya fue utilizada con un payload diferente.");
            }
        }

        // Paso 2: primera vez que vemos esta key. Bloqueamos el Payment (ver PaymentRepository) para que
        // ninguna otra petición concurrente pueda leer/escribir su saldo mientras nosotros lo procesamos.
        Payment payment = paymentRepository.findWithLockById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Regla de negocio 1: la moneda tiene que coincidir exactamente con la del pago original.
        if (!payment.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new CurrencyMismatchException(
                    "La moneda de la devolución (" + request.getCurrency() +
                            ") no coincide con la del pago original (" + payment.getCurrency() + ").");
        }

        // Regla de negocio 2: no se puede devolver más de lo que queda disponible.
        if (request.getAmount().compareTo(payment.getAvailableAmount()) > 0) {
            throw new AmountExceededException(
                    "El importe solicitado (" + request.getAmount() +
                            ") supera el saldo disponible para devolución (" + payment.getAvailableAmount() + ").");
        }

        // Todas las validaciones pasaron: actualizamos el saldo del pago...
        payment.applyRefund(request.getAmount());
        paymentRepository.save(payment);

        // ...y creamos el registro permanente de este refund, que es lo que hará posible detectar
        // futuros reintentos con esta misma Idempotency-Key.
        RefundRecord record = new RefundRecord(
                UUID.randomUUID(),
                idempotencyKey,
                paymentId,
                requestHash,
                request.getAmount(),
                request.getCurrency(),
                "COMPLETED",
                Instant.now()
        );
        refundRecordRepository.save(record);

        // "true" indica que esto es una CREACIÓN nueva -> el controller lo traducirá a 201 Created.
        return new RefundOutcome(toResponse(record), true);
    }

    private void validateIdempotencyKeyFormat(String idempotencyKey) {
        if (idempotencyKey == null || !UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException(
                    "El header 'Idempotency-Key' debe ser un UUID v4 válido.");
        }
    }

    private void validateNoSensitiveData(String reason) {
        if (PciSanitizer.containsSensitiveCardData(reason)) {
            // Importante: NO incluimos el valor de 'reason' en el mensaje de la excepción.
            // Si lo hiciéramos, el propio mensaje de error acabaría filtrando el dato sensible
            // que estamos intentando bloquear (por ejemplo, si el mensaje se llega a loguear en algún punto).
            throw new SensitiveDataDetectedException(
                    "El campo 'reason' contiene un patrón que parece un número de tarjeta o CVV. No está permitido.");
        }
    }

    // Convierte la entidad interna (RefundRecord) al DTO que sí es seguro exponer al cliente (RefundResponse).
    // Esta separación es la que evita el problema OWASP "Excessive Data Exposure" que mencionamos en los DTOs.
    private RefundResponse toResponse(RefundRecord record) {
        return new RefundResponse(
                record.getRefundId(),
                record.getPaymentId(),
                record.getAmount(),
                record.getCurrency(),
                record.getStatus(),
                record.getCreatedAt()
        );
    }

    /**
     * Envuelve la respuesta junto con si fue una creación nueva (201) o un replay idempotente (200).
     * Es un "record" (característica de Java moderno): una clase inmutable de solo-datos, sin necesidad
     * de escribir a mano constructor/getters/equals/hashCode - Java los genera automáticamente.
     */
    public record RefundOutcome(RefundResponse response, boolean created) {
    }
}