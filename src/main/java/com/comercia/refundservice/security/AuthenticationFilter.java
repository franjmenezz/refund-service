package com.comercia.refundservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Valida el header Authorization ANTES de que la petición llegue a cualquier lógica de negocio.
 *
 * Nota de diseño: en esta prueba técnica se simula la validación con un token estático
 * configurable (app.security.demo-token). En producción se sustituiría por validación
 * de JWT firmado / introspección OAuth2 contra un Identity Provider (ver SECURITY_AUDIT.md).
 *
 * Regla PCI-DSS: el valor del header Authorization NUNCA se escribe en logs, ni siquiera
 * en caso de error.
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    // El token de demo se inyecta desde application.yml (app.security.demo-token), no está hardcodeado aquí.
    private final String demoToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthenticationFilter(@Value("${app.security.demo-token}") String demoToken) {
        this.demoToken = demoToken;
    }

    // OncePerRequestFilter garantiza que este código se ejecuta una única vez por petición HTTP
    // (evita el problema de que un filtro se dispare varias veces en ciertos escenarios de forwarding interno).
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Caso 1: no hay header, o no empieza por "Bearer ", o el token está vacío tras quitar el prefijo.
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.substring(7).isBlank()) {
            writeUnauthorized(response, "Header Authorization ausente o mal formado. Se espera 'Bearer <token>'.");
            return; // El "return" es clave: al NO llamar a filterChain.doFilter(), la petición se corta aquí.
        }

        // substring(7) quita el prefijo "Bearer " (7 caracteres, incluyendo el espacio) para quedarnos solo con el token.
        String token = authHeader.substring(7).trim();
        if (!demoToken.equals(token)) {
            writeUnauthorized(response, "Token de autenticación inválido.");
            return;
        }

        // Todo correcto: dejamos que la petición continúe su camino hacia el controller.
        filterChain.doFilter(request, response);
    }

    // Construye a mano la respuesta 401 en JSON, porque en este punto (filtro, no controller) no podemos
    // usar el mecanismo normal de Spring MVC (@ExceptionHandler) para dar formato a la respuesta de error.
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 401);
        body.put("error", "UNAUTHORIZED");
        body.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}