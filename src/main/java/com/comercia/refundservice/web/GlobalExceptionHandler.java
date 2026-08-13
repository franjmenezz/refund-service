package com.comercia.refundservice.web;

import com.comercia.refundservice.service.exception.*;
import com.comercia.refundservice.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce excepciones de negocio a respuestas HTTP consistentes.
 * Ninguno de estos handlers loguea el body de la petición ni el detalle de datos sensibles;
 * solo el tipo de error y un mensaje seguro para el cliente.
 */
// @RestControllerAdvice: aplica estos manejadores a TODOS los controllers de la aplicación (aquí solo
// tenemos uno, pero si añadieras más controllers en el futuro, seguirían usando estos mismos handlers).
// Así centralizamos en un único sitio la traducción "excepción -> código HTTP", en vez de repetir
// try/catch en cada método del controller.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Cada @ExceptionHandler "escucha" un tipo de excepción concreto. Cuando RefundService lanza
    // esa excepción, Spring la intercepta automáticamente y ejecuta el método correspondiente,
    // sin que el controller necesite ningún try/catch explícito.

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IdempotencyConflictException ex) {
        return build(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(AmountExceededException.class)
    public ResponseEntity<ErrorResponse> handleAmountExceeded(AmountExceededException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(CurrencyMismatchException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(SensitiveDataDetectedException.class)
    public ResponseEntity<ErrorResponse> handleSensitiveData(SensitiveDataDetectedException ex) {
        // Se loguea la detección (sin el valor) porque es un evento de seguridad relevante para auditoría.
        log.warn("Intento de envío de datos sensibles detectado y bloqueado en campo 'reason'.");
        return build(HttpStatus.BAD_REQUEST, "SENSITIVE_DATA_DETECTED", ex.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidKey(InvalidIdempotencyKeyException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    // Este handler captura los fallos de @Valid en RefundRequest (@NotNull, @Pattern, @Size...).
    // Cogemos solo el PRIMER error de campo para no devolver un mensaje demasiado largo/confuso.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Petición inválida.");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    // Se dispara si falta el header Idempotency-Key en la petición (Spring lo detecta automáticamente
    // gracias a @RequestHeader en el controller, antes incluso de llegar a nuestro código).
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Falta el header obligatorio: " + ex.getHeaderName());
    }

    // Se dispara si, por ejemplo, el payment_id de la URL no es un UUID válido
    // (Spring intenta convertirlo a tipo UUID y falla).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido.");
    }

    // Se dispara si el body de la petición ni siquiera es un JSON válido (llaves mal cerradas, etc.).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El cuerpo de la petición no es un JSON válido.");
    }

    // Red de seguridad final: captura CUALQUIER excepción no prevista explícitamente arriba.
    // Es importante que exista, para que un fallo inesperado nunca devuelva al cliente
    // un stacktrace completo (que podría filtrar detalles internos de la implementación).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // No se expone el detalle interno de la excepción al cliente ni se loguea el body de la petición.
        log.error("Error inesperado procesando la solicitud: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ha ocurrido un error inesperado.");
    }

    // Método auxiliar para no repetir "ResponseEntity.status(...).body(new ErrorResponse(...))"
    // en cada uno de los handlers de arriba.
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), error, message));
    }
}