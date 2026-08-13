package com.comercia.refundservice.repository;
import com.comercia.refundservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

// JpaRepository<Payment, UUID> ya nos da gratis: save(), findById(), delete(), etc.
// No hace falta implementar nada de eso a mano; Spring Data genera la implementación en tiempo de ejecución.

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Bloqueo pesimista (PESSIMISTIC_WRITE): mientras una transacción tiene el Payment bloqueado con este
     * método, cualquier OTRA transacción que intente leerlo con findWithLockById se queda esperando
     * hasta que la primera termine (commit o rollback).
     *
     * Por qué hace falta: sin esto, dos peticiones de refund concurrentes sobre el MISMO pago (con distinta
     * Idempotency-Key cada una) podrían leer el mismo "saldo disponible" antes de que ninguna escribiera su
     * actualización, y las dos pasarían la validación individualmente aunque juntas superen el importe original.
     * Con el bloqueo, la segunda transacción espera a que la primera actualice refundedAmount antes de leer,
     * así que ve el saldo ya actualizado y la validación es correcta.
     *
     * Se usa solo aquí, no en un findById normal, porque bloquear SIEMPRE que se lee un Payment sería un coste
     * de rendimiento innecesario en operaciones que no van a modificar el saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockById(UUID id);
}