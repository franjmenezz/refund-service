# SECURITY_AUDIT.md — Auditoría DevSecOps del Refund Service

## 1. Alcance de la auditoría

Revisión del código del microservicio (`src/main`) frente a los riesgos descritos en el enunciado: dobles reembolsos, fuga de datos PCI en logs/payloads, e inyección/manipulación de estado por APIs mal validadas o autenticadas.

---

## 2. Controles OWASP aplicados

| Riesgo OWASP (API Security Top 10) | Control aplicado en el código |
|---|---|
| **API1: Broken Object Level Authorization** | El `payment_id` del path se resuelve siempre contra la base de datos (`PaymentRepository`); no hay forma de operar sobre un pago sin que exista y sea el indicado en la URL. *(Limitación reconocida: no hay modelo de "propietario" del pago porque no hay multi-tenant en el alcance — ver sección 5.)* |
| **API2: Broken Authentication** | `AuthenticationFilter` bloquea toda petición sin `Authorization: Bearer <token>` válido antes de llegar al controlador. Se usa un token estático de demo, documentado como limitación (ver sección 5). |
| **API3: Broken Object Property Level Authorization / Excessive Data Exposure** | `RefundResponse` es un DTO explícito; nunca se serializa la entidad JPA directamente, evitando exponer campos internos no deseados. |
| **API4: Unrestricted Resource Consumption** | `reason` limitado a 250 caracteres (`@Size`). *(Limitación: no hay rate-limiting a nivel de API gateway — fuera de alcance de un microservicio individual, ver sección 5.)* |
| **API5: Broken Function Level Authorization** | Un único endpoint, un único rol implícito (comercio autenticado). No aplica en este alcance. |
| **API6 / API3 (Mass Assignment)** | El DTO `RefundRequest` expone solo `amount`, `currency`, `reason`; no hay binding directo sobre la entidad `Payment` ni `RefundRecord`, evitando que el cliente pueda inyectar campos como `status` o `refundId`. |
| **API8: Security Misconfiguration** | Logging configurado explícitamente para no volcar SQL en producción (`WARN`), sin stacktraces expuestos al cliente (`GlobalExceptionHandler` devuelve mensajes genéricos en errores 500). |
| **API10: Unsafe Consumption of APIs / Injection** | Toda entrada pasa por Bean Validation (`@NotNull`, `@Pattern`, `@Size`) antes de tocar lógica de negocio. Uso de JPA con parámetros (no hay SQL concatenado), lo que elimina el riesgo de inyección SQL. |
| **A02:2021 Cryptographic Failures (OWASP Top 10 web)** | La `Idempotency-Key` se compara junto a un hash SHA-256 del payload (`RequestHasher`), no el payload en claro, reduciendo superficie de comparación de datos de negocio. |
| **PCI-DSS Req. 3 (no almacenar datos de autenticación sensibles) / Req. 4** | `PciSanitizer` bloquea patrones de PAN/CVV en el campo `reason` **antes** de persistir nada. `RequestLoggingFilter` nunca loguea `Authorization`, el body, ni `reason`. |

---

## 3. Idempotencia — análisis de condiciones de carrera

El caso más peligroso no es el reintento secuencial (ya cubierto por el `UNIQUE` sobre `idempotencyKey`), sino **dos peticiones concurrentes con distinta `Idempotency-Key` sobre el mismo pago**, que podrían leer el mismo `availableAmount` antes de que ninguna escriba, superando el límite entre ambas.

Mitigación aplicada: `PaymentRepository.findWithLockById` usa `PESSIMISTIC_WRITE` dentro de una transacción (`@Transactional` en `RefundService`), serializando el acceso al `Payment` durante la validación + actualización de `refundedAmount`. Además, el `UNIQUE constraint` sobre `idempotencyKey` en base de datos actúa como segunda barrera ante una condición de carrera en la propia tabla de refunds.

---

## 4. Herramientas de análisis estático sugeridas

| Herramienta | Propósito | Cómo se integraría |
|---|---|---|
| **OWASP Dependency-Check** o **Snyk** | Detectar CVEs en dependencias de Maven (Spring Boot, H2, etc.) | Plugin Maven en el `pom.xml` + gate en el pipeline CI. |
| **SpotBugs + find-sec-bugs** | Análisis estático de código Java (SAST) — detecta problemas como logging de datos sensibles, uso incorrecto de criptografía. | `mvn spotbugs:check` como paso obligatorio antes de merge. |
| **Trivy** | Escaneo de la imagen Docker final (si se conteneriza el servicio) en busca de vulnerabilidades del SO base y dependencias. | Paso de CI tras el build de la imagen. |
| **Semgrep** | Reglas custom para detectar, por ejemplo, cualquier `log.info(request)` o similar que loguee un objeto completo en vez de campos concretos. | Pre-commit hook + CI. |
| **GitLeaks / TruffleHog** | Evitar que se commiteen tokens o secretos. | Pre-commit hook. |

---

## 5. Limitaciones conocidas y trabajo futuro (honestidad técnica)

Estas limitaciones son **deliberadas y aceptadas para el alcance de la prueba**, pero se documentan explícitamente porque en un entorno productivo real de Comercia Global Payments serían bloqueantes:

1. **Autenticación simulada con token estático**: en producción se sustituiría por JWT firmado (validación de firma, expiración, `aud`/`iss`) o introspección OAuth2 contra un Identity Provider, y el token identificaría al comercio para poder aplicar autorización a nivel de objeto (que el `payment_id` pertenezca a ese comercio).
2. **Sin rate-limiting ni protección anti-abuso**: se delegaría en un API Gateway (p.ej. Kong, AWS API Gateway) delante del servicio.
3. **Sin cifrado en tránsito configurado explícitamente** (HTTPS/TLS): en este entorno de demo el servicio corre en HTTP plano sobre `localhost`; en producción iría detrás de un balanceador/ingress con TLS terminado ahí o en el propio servicio.
4. **El regex de detección de PAN (`PciSanitizer`) es una heurística**, no una validación Luhn completa. Puede generar falsos positivos o, más raramente, falsos negativos con formatos atípicos. Para un entorno productivo se recomendaría una librería especializada de detección de PII/PAN.
5. **La respuesta cacheada en un reintento idempotente (200 OK) no vuelve a validar que el pago siga en un estado consistente** (p.ej. si por error administrativo se revirtiera manualmente un refund en BD). Es una asunción razonable para el alcance de la prueba, pero se documenta.