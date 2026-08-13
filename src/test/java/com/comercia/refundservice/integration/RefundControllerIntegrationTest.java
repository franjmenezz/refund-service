package com.comercia.refundservice.integration;

import com.comercia.refundservice.config.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración end-to-end sobre el endpoint real (MockMvc + contexto Spring completo,
 * incluyendo filtros de seguridad y base de datos H2 en memoria).
 */
// @SpringBootTest: arranca TODO el contexto de Spring (igual que "mvn spring-boot:run", pero en memoria,
// sin abrir un puerto real). Es lo que permite que los filtros de seguridad, el service, y la BD H2
// se comporten exactamente igual que en producción durante estos tests.
@SpringBootTest
// @AutoConfigureMockMvc: nos da el objeto "mockMvc" para simular peticiones HTTP sin necesidad de un
// cliente HTTP real ni de un puerto abierto — más rápido y más fiable que usar curl contra localhost.
@AutoConfigureMockMvc
// @ActiveProfiles("test"): activa application-test.yml en vez de application.yml, así estos tests
// usan su propia BD H2 y su propio token, sin depender de (ni interferir con) tu entorno de desarrollo.
@ActiveProfiles("test")
class RefundControllerIntegrationTest {

    private static final String VALID_TOKEN = "Bearer test-token";
    // DataSeeder.PAYMENT_1_ID: reutilizamos la MISMA constante que usa la aplicación real al arrancar,
    // en vez de escribir el UUID a mano aquí. Si el ID cambiara algún día en DataSeeder, este test
    // seguiría funcionando sin tocar nada.
    private static final String BASE_PATH = "/api/v1/payments/" + DataSeeder.PAYMENT_1_ID + "/refunds";

    @Autowired
    private MockMvc mockMvc;

    // ---------- Flujo feliz ----------

    @Test
    void deberiaCrearUnRefundCorrectamente() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(validPayload("25.00", "EUR", "Producto defectuoso")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.refundId").exists());
    }

    // ---------- Idempotencia: mismo payload -> 200 ----------

    // Este es EL test más importante de todo el proyecto: prueba automáticamente lo mismo que
    // comprobamos a mano con Invoke-RestMethod (mismo refundId en la segunda llamada).
   // Este es EL test más importante de todo el proyecto: prueba automáticamente lo mismo que
    // comprobamos a mano con Invoke-RestMethod (mismo refundId en la segunda llamada).
    @Test
    void reintentoConMismaKeyYMismoPayloadDevuelve200ConLaMismaRespuesta() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = validPayload("10.00", "EUR", "Cliente insatisfecho");

        // Primera llamada: debe crear (201). Extraemos el refundId, que es el dato que de verdad
        // demuestra que es "el mismo" refund en ambas llamadas.
        String firstResponse = mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstRefundId = com.jayway.jsonpath.JsonPath.read(firstResponse, "$.refundId");

        // Segunda llamada, MISMA key y MISMO payload: debe devolver 200 (no 201) y el MISMO refundId.
        // No comparamos el JSON completo carácter a carácter porque 'createdAt' puede perder precisión
        // de nanosegundos al releerse desde la base de datos (H2 redondea a microsegundos), aunque
        // represente exactamente el mismo instante. Comparar campo a campo evita ese falso negativo.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(firstRefundId))
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ---------- Idempotencia: misma key, payload distinto -> 409 ----------

    @Test
    void reintentoConMismaKeyYPayloadDistintoDevuelve409() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Motivo original")))
                .andExpect(status().isCreated());

        // Misma key, pero cambiamos el importe (5.00 -> 6.00): debe rechazarse con 409, no procesarse.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(validPayload("6.00", "EUR", "Motivo distinto")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
    }

    // ---------- Exceso de importe -> 422 ----------

    @Test
    void solicitarMasImporteQueElDisponibleDevuelve422() throws Exception {
        // El pago sembrado (DataSeeder) tiene 100.00 EUR de originalAmount; pedimos muchísimo más.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("999999.00", "EUR", "Importe excesivo")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("AMOUNT_EXCEEDED"));
    }

    // ---------- Moneda distinta a la del pago original -> 422 ----------

    @Test
    void monedaDistintaALaDelPagoOriginalDevuelve422() throws Exception {
        // PAYMENT_1_ID está sembrado en EUR; pedimos el refund en USD -> debe fallar.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("10.00", "USD", "Moneda incorrecta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CURRENCY_MISMATCH"));
    }

    // ---------- Pago inexistente -> 404 ----------

    @Test
    void pagoInexistenteDevuelve404() throws Exception {
        // UUID totalmente aleatorio: casi con toda seguridad no existe en la BD sembrada.
        String randomPaymentId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/payments/" + randomPaymentId + "/refunds")
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Pago no existente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"));
    }

    // ---------- Seguridad: sin Authorization -> 401 ----------

    @Test
    void sinHeaderAuthorizationDevuelve401() throws Exception {
        // Fíjate: NO añadimos .header("Authorization", ...) en absoluto. AuthenticationFilter
        // debe cortar la petición ANTES de que llegue siquiera al controller.
        mockMvc.perform(post(BASE_PATH)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Sin auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", "Bearer token-incorrecto")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Token inválido")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Idempotency-Key con formato inválido -> 400 ----------

    @Test
    void idempotencyKeyConFormatoInvalidoDevuelve400() throws Exception {
        // "no-es-un-uuid" no cumple el patrón UUIDv4 -> InvalidIdempotencyKeyException -> 400.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", "no-es-un-uuid")
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Key inválida")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ---------- PCI-DSS: PAN en el campo reason -> 400 ----------

    // Este test es doblemente importante: no solo comprueba el 400, sino que verifica que el
    // número de tarjeta "4111" NO aparece en ningún sitio de la respuesta -- confirmando la regla
    // de PciSanitizer de "nunca ecoar el valor sensible detectado".
    @Test
    void reasonConNumeroDeTarjetaDevuelve400YNoLoEcoa() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "Tarjeta 4111 1111 1111 1111 no funcionó")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SENSITIVE_DATA_DETECTED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("4111"))));
    }

    @Test
    void reasonConCvvDevuelve400() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("5.00", "EUR", "El CVV 123 no era válido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SENSITIVE_DATA_DETECTED"));
    }

    // ---------- Validación de amount negativo/cero -> 400 ----------

    @Test
    void amountNegativoDevuelve400() throws Exception {
        // Esto lo bloquea @DecimalMin("0.01") en RefundRequest, ANTES de llegar a RefundService.
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", VALID_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(validPayload("-5.00", "EUR", "Importe negativo")))
                .andExpect(status().isBadRequest());
    }

    // Método auxiliar para no repetir la construcción del JSON en cada uno de los 13 tests de arriba.
    // Usa un "text block" de Java (las comillas triples """) para escribir JSON multilínea legible.
    private String validPayload(String amount, String currency, String reason) {
        return """
                {
                  "amount": %s,
                  "currency": "%s",
                  "reason": "%s"
                }
                """.formatted(amount, currency, reason);
    }
}