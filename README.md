# Secure Idempotent Refund Service

Microservicio de devoluciones de pagos con idempotencia estricta y controles PCI-DSS, desarrollado para la prueba técnica de **Comercia Global Payments**.

- Especificación funcional/técnica: [`SPEC.md`](./SPEC.md)
- Contrato OpenAPI: [`openapi.yaml`](./openapi.yaml)
- Auditoría de seguridad: [`SECURITY_AUDIT.md`](./SECURITY_AUDIT.md)
- Bitácora de prompts: [`PROMPTS.md`](./PROMPTS.md)

## Stack

- Java 17
- Spring Boot 3.3 (Web, Data JPA, Validation)
- H2 (Base de datos en memoria)
- JUnit 5 + MockMvc

## Requisitos

- JDK 17+
- Maven 3.9+

## Nota sobre la estructura de carpetas

El enunciado de la prueba sugiere una estructura con `src/` y `tests/` como carpetas hermanas al mismo nivel. Este proyecto usa en su lugar la convención estándar de Maven: `src/main/java` para el código de producción y `src/test/java` para los tests, ambos bajo `src/`. Es la estructura que Maven, Gradle y prácticamente cualquier proyecto Java del mundo real esperan encontrar (herramientas de build, IDEs y pipelines de CI la asumen por defecto), así que se ha priorizado seguir esa convención en vez de una carpeta `tests/` separada, que rompería las herramientas estándar del ecosistema.

## Cómo ejecutar

```bash
mvn spring-boot:run
```

El servicio arranca en `http://localhost:8080`. Al arrancar, se siembran automáticamente **dos pagos de ejemplo** (ver `DataSeeder`), necesarios porque el alcance de la prueba no incluye un endpoint de creación de pagos:

| payment_id | originalAmount | currency |
|---|---|---|
| `11111111-1111-4111-8111-111111111111` | 100.00 | EUR |
| `22222222-2222-4222-8222-222222222222` | 50.00 | USD |

El token Bearer de demo está en `application.yml` (`app.security.demo-token`): **`demo-secret-token-2026`**.

## Cómo ejecutar los tests

```bash
mvn test
```

Cubre: flujo feliz, reintento idempotente (200), conflicto por payload distinto con misma key (409), exceso de importe (422), moneda distinta (422), pago inexistente (404), autenticación ausente/inválida (401), `Idempotency-Key` con formato inválido (400), y detección de PAN/CVV en el campo `reason` (400), además de tests unitarios del sanitizador PCI y del hasher de idempotencia.

**22/22 tests pasan** (12 de integración + 10 unitarios), verificado ejecutando `mvn test` de principio a fin.

## Ejemplo de uso

**En Linux/macOS (curl estándar):**
```bash
curl -X POST http://localhost:8080/api/v1/payments/11111111-1111-4111-8111-111111111111/refunds \
  -H "Authorization: Bearer demo-secret-token-2026" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"amount": 25.50, "currency": "EUR", "reason": "Producto defectuoso"}'
```

**En Windows (PowerShell):** el alias `curl` de PowerShell no es compatible con la sintaxis de headers de curl real; usa `Invoke-RestMethod`:
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/payments/11111111-1111-4111-8111-111111111111/refunds" -Headers @{"Authorization"="Bearer demo-secret-token-2026"; "Idempotency-Key"="3fa85f64-5717-4562-b3fc-2c963f66afa6"} -ContentType "application/json" -Body '{"amount": 25.50, "currency": "EUR", "reason": "Producto defectuoso"}'
```

**Reintento idempotente** (relanzar el mismo comando, misma Idempotency-Key y mismo body) → `200 OK` con el mismo `refundId`.

**Reutilizar la misma Idempotency-Key con un body distinto** → `409 Conflict`.

## Consola H2 (debug local)

Disponible en `http://localhost:8080/h2-console` con JDBC URL `jdbc:h2:mem:refunddb`, usuario `sa`, sin contraseña. Solo para depuración local — deshabilitar en cualquier entorno real (ver `SECURITY_AUDIT.md`).

## Estructura del proyecto

```
├── SPEC.md
├── openapi.yaml
├── PROMPTS.md
├── SECURITY_AUDIT.md
├── README.md
├── pom.xml
├── src/main/java/com/comercia/refundservice/
│   ├── config/DataSeeder.java
│   ├── security/AuthenticationFilter.java
│   ├── security/RequestLoggingFilter.java
│   ├── web/RefundController.java
│   ├── web/GlobalExceptionHandler.java
│   ├── web/dto/
│   ├── service/RefundService.java
│   ├── service/exception/
│   ├── domain/Payment.java
│   ├── domain/RefundRecord.java
│   ├── repository/
│   └── util/PciSanitizer.java, RequestHasher.java
└── src/test/java/com/comercia/refundservice/
    ├── integration/RefundControllerIntegrationTest.java
    └── unit/PciSanitizerTest.java, RequestHasherTest.java
```

