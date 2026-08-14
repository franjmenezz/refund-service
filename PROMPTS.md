# PROMPTS.md — Bitácora de interacción con IA

**Herramienta usada:** Claude (Anthropic), en flujo conversacional dirigiendo la generación de código.
**Enfoque:** Spec-Driven Development — se generó primero `SPEC.md` y `openapi.yaml`, y toda la implementación posterior se pidió explícitamente "basándote solo en la especificación", para evitar que el modelo improvisara reglas de negocio no documentadas.

---

## 1. Prompts clave (resumidos, en orden de uso)

1. **Especificación inicial**
   > "Antes de escribir código, define SPEC.md para un servicio de refunds con idempotencia estricta (Idempotency-Key UUIDv4), reglas de negocio de importe máximo, y controles PCI-DSS para evitar PAN/CVV en el campo reason. Incluye tabla de códigos HTTP por caso."

2. **Contrato OpenAPI**
   > "Convierte esa spec en un openapi.yaml formal, con schemas para request, response de éxito y de error."

3. **Estructura del proyecto e infraestructura base**
   > "Genera el pom.xml y la estructura de un proyecto Spring Boot 3 con Web, Data JPA, Validation y H2, siguiendo exactamente el contrato anterior."

4. **Modelo de dominio e idempotencia**
   > "Implementa las entidades Payment y RefundRecord, y un RefundService que implemente el algoritmo de idempotencia descrito en SPEC.md sección 4: mismo payload -> 200 con la respuesta cacheada, payload distinto con la misma key -> 409, usando un hash del payload para la comparación."

5. **Seguridad**
   > "Añade un filtro de autenticación Bearer que corte la petición antes del controlador, y un filtro de logging que documente explícitamente qué campos NUNCA se loguean (Authorization, body, reason) y por qué."

6. **PCI-DSS en el campo reason**
   > "Implementa una utilidad que detecte patrones de número de tarjeta (13-19 dígitos) o CVV en un texto libre, sin loguear el valor evaluado en ningún caso, ni siquiera en el mensaje de error."

7. **Tests**
   > "Genera tests de integración con MockMvc que cubran: flujo feliz, reintento idempotente exitoso, conflicto por payload distinto con misma key, exceso de importe, moneda distinta, pago inexistente, sin autenticación, token inválido, Idempotency-Key con formato inválido, y PAN/CVV en reason verificando que la respuesta de error NO contiene el número detectado."

8. **Auditoría**
   > "Revisa el servicio generado y documenta en SECURITY_AUDIT.md los controles OWASP aplicados, un análisis específico de condiciones de carrera en la idempotencia, y las limitaciones conocidas que aceptaríamos solo por el alcance de esta prueba."

---

## 2. Error / limitación detectada en el código generado por la IA

**Lo que ocurrió:** en la primera versión que la IA propuso para `RefundService`, la comprobación de saldo disponible (`amount <= availableAmount`) se hacía leyendo el `Payment` con un `findById` normal (sin bloqueo), y solo después se guardaba el importe actualizado.

**Por qué es un problema:** con un `findById` sin bloqueo, dos peticiones concurrentes con **distinta** `Idempotency-Key` sobre el **mismo pago** podrían leer el mismo `availableAmount` antes de que ninguna de las dos escribiera su actualización. Ambas pasarían la validación de "no superar el importe disponible" de forma individual, pero juntas sí podrían superarlo.

**Cómo se detectó:** al pedirle explícitamente a la IA que revisara su propia propuesta buscando condiciones de carrera ("¿qué pasa si dos requests con distinta Idempotency-Key llegan al mismo tiempo sobre el mismo payment_id?"), identificó el hueco.

**Cómo se corrigió:** se sustituyó el `findById` por `findWithLockById`, con `@Lock(LockModeType.PESSIMISTIC_WRITE)`, envuelto en la transacción `@Transactional` de `processRefund`. Documentado también en `SECURITY_AUDIT.md` sección 3.

**Aprendizaje:** la IA, por defecto, tiende a generar el "camino feliz" del acceso a datos y no introduce bloqueos pesimistas ni piensa en concurrencia salvo que se le pregunte explícitamente.

---

## 3. Segundo caso: falso negativo en un test por precisión de timestamp

**Lo que ocurrió:** el test de idempotencia comparaba el JSON completo de la primera y la segunda respuesta carácter a carácter. Al ejecutarlo, falló con una diferencia mínima en el campo `createdAt`, aunque representaban el mismo instante.

**Por qué ocurría:** la primera respuesta se construye en memoria justo tras crear el `RefundRecord` (precisión de nanosegundo). La segunda respuesta relee ese mismo registro desde H2, que redondea a microsegundos al persistir.

**Cómo se corrigió:** el test ahora compara campo a campo (`refundId`, `amount`, `currency`, `status`) en vez de el JSON completo.

**Aprendizaje:** los tests que comparan JSON completo son frágiles frente a campos de alta precisión que pueden perder exactitud al pasar por una base de datos real.

---

## 4. Criterio aplicado sobre el código generado

Ningún fragmento generado se aceptó sin revisión de: (a) que correspondiera exactamente a lo escrito en `SPEC.md` — no más, no menos —, y (b) que no introdujera logging de datos sensibles, que fue el punto que se revisó con más insistencia dado el peso de DevSecOps en la evaluación (30%).