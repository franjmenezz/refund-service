package com.comercia.refundservice.repository;

import com.comercia.refundservice.domain.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    // Este método es el corazón de la comprobación de idempotencia: RefundService lo llama
    // al principio de cada petición para saber si esa Idempotency-Key ya se procesó antes.
    Optional<RefundRecord> findByIdempotencyKey(String idempotencyKey);
}