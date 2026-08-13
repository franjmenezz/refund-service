package com.comercia.refundservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logging estructurado y deliberadamente "ciego" a datos sensibles:
 * - NUNCA loguea el header Authorization.
 * - NUNCA loguea el body de la petición (podría contener 'reason' con datos sensibles
 *   aunque hayan pasado la validación, o el 'amount' en payloads futuros más ricos).
 * - Solo registra: método HTTP, path, código de respuesta y la Idempotency-Key
 *   (no sensible: es un identificador de operación, no un dato de tarjeta ni credencial).
 */
// @Order(1) fuerza a que este filtro se ejecute ANTES que AuthenticationFilter (que no tiene @Order explícito,
// así que usa la prioridad por defecto, más baja). Queremos loguear la petición incluso si luego se rechaza
// por falta de autenticación, para tener trazabilidad de intentos de acceso no autorizados.
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Guardamos la Idempotency-Key ANTES de continuar, porque la necesitaremos para el log de después.
        String idempotencyKey = request.getHeader("Idempotency-Key");

        // Dejamos que la petición siga su curso (por los demás filtros, el controller, etc.)
        // ANTES de loguear, para poder incluir en el log el código de estado final de la respuesta.
        filterChain.doFilter(request, response);

        // Este log se escribe DESPUÉS de que toda la petición se ha procesado.
        // Fíjate en qué NO aparece aquí: ni el header Authorization, ni el body, ni el campo 'reason'.
        // Solo datos que identifican la operación sin ser sensibles.
        log.info("method={} path={} status={} idempotencyKey={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                idempotencyKey != null ? idempotencyKey : "N/A");
    }
}