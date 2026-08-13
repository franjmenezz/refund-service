package com.comercia.refundservice.web;

import com.comercia.refundservice.service.RefundService;
import com.comercia.refundservice.web.dto.RefundRequest;
import com.comercia.refundservice.web.dto.RefundResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
// @RequestMapping a nivel de clase: la URL base ya incluye el path variable {payment_id},
// así el único método de este controller no tiene que repetirla.
@RequestMapping("/api/v1/payments/{payment_id}/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<RefundResponse> createRefund(
            // @PathVariable: extrae {payment_id} de la URL y lo convierte directamente a UUID
            // (si no fuera un UUID válido, Spring lanza automáticamente un error 400 antes de llegar aquí).
            @PathVariable("payment_id") UUID paymentId,

            // @RequestHeader: si el header Idempotency-Key no viene en la petición, Spring responde
            // 400 Bad Request automáticamente, sin que tengamos que comprobarlo a mano.
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            // @Valid: dispara las validaciones (@NotNull, @Pattern, @Size...) que pusimos en RefundRequest.
            // Si alguna falla, Spring lanza MethodArgumentNotValidException ANTES de ejecutar este método,
            // y es GlobalExceptionHandler quien la captura y la convierte en un 400 con el mensaje adecuado.
            @Valid @RequestBody RefundRequest request) {

        RefundService.RefundOutcome outcome = refundService.processRefund(paymentId, idempotencyKey, request);

        // Aquí es donde se decide 201 vs 200, según lo que decidió el service (ver RefundOutcome.created()).
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.response());
    }
}