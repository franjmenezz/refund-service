# SPEC.md — Secure Idempotent Refund Service

## 1. Propósito

Microservicio que gestiona devoluciones (refunds) de pagos previamente procesados, garantizando:

1. **Idempotencia estricta** ante reintentos de red (no se generan dobles reembolsos).
2. **Ausencia de datos sensibles** (PAN/CVV) en payloads, respuestas y logs (alineado con PCI-DSS).
3. **Autenticación y validación estricta** de toda petición entrante.

Este documento se escribe **antes** de la implementación (Spec-Driven Development) y es la única fuente de verdad para el código generado en la Fase 2.

---

## 2. Contrato del endpoint

```
POST /api/v1/payments/{payment_id}/refunds
```

### 2.1 Headers obligatorios

| Header | Formato | Obligatorio | Descripción |
|---|---|---|---|
| `Authorization` | `Bearer <token>` | Sí | Token de autenticación del cliente/comercio. |
| `Idempotency-Key` | UUID v4 | Sí | Identificador único de la operación de reintento. |
| `Content-Type` | `application/json` | Sí | |

### 2.2 Path params

| Campo | Tipo | Descripción |
|---|---|---|
| `payment_id` | UUID | Identificador del pago original sobre el que se solicita la devolución. |

### 2.3 Request Body

```json
{
  "amount": 25.50,
  "currency": "EUR",
  "reason": "Producto defectuoso"
}
```

| Campo | Tipo | Reglas |
|---|---|---|
| `amount` | decimal (>0, 2 decimales) | Obligatorio. No puede superar el importe disponible a devolver del pago original. |
| `currency` | string (ISO 4217, 3 letras) | Obligatorio. Debe coincidir con la moneda del pago original. |
| `reason` | string (máx. 250 caracteres) | Obligatorio. **No puede contener secuencias que parezcan un PAN (13-19 dígitos) ni un CVV** (ver §5). |

### 2.4 Respuestas

| Código | Caso | Body |
|---|---|---|
| `201 Created` | Primera vez que se procesa correctamente esta `Idempotency-Key`. | Objeto `RefundResponse`. |
| `200 OK` | Reintento con la **misma** `Idempotency-Key` y el **mismo** payload (byte a byte a nivel semántico). | Mismo objeto `RefundResponse` devuelto la primera vez (respuesta cacheada, no se reprocesa el negocio). |
| `400 Bad Request` | Validación de formato: `Idempotency-Key` no es UUIDv4, campos ausentes/mal tipados, `reason` contiene un patrón tipo PAN/CVV. | `ErrorResponse` |
| `401 Unauthorized` | `Authorization` ausente, mal formado o token inválido. | `ErrorResponse` |
| `404 Not Found` | `payment_id` no existe. | `ErrorResponse` |
| `409 Conflict` | Misma `Idempotency-Key`, pero **payload distinto** al de la petición original. | `ErrorResponse` |
| `422 Unprocessable Entity` | Regla de negocio violada: `amount` supera el importe disponible a devolver, o `currency` no coincide con el pago original. | `ErrorResponse` |

`RefundResponse`:
```json
{
  "refundId": "b3f1...",
  "paymentId": "a1e2...",
  "amount": 25.50,
  "currency": "EUR",
  "status": "COMPLETED",
  "createdAt": "2026-08-13T10:15:30Z"
}
```

`ErrorResponse`:
```json
{
  "timestamp": "2026-08-13T10:15:30Z",
  "status": 409,
  "error": "IDEMPOTENCY_CONFLICT",
  "message": "La Idempotency-Key ya fue utilizada con un payload diferente."
}
```

---

## 3. Reglas de negocio

1. Un pago (`Payment`) tiene un `originalAmount` y una `currency` fijados en el momento de su creación.
2. El importe acumulado de devoluciones sobre un pago **nunca** puede superar `originalAmount` (se permiten devoluciones parciales múltiples mientras quede saldo disponible).
3. La `currency` del refund debe coincidir exactamente con la del pago original (no se hace conversión de divisa).

## 4. Idempotencia — algoritmo

1. Al recibir la petición, se calcula un hash (SHA-256) del payload canonicalizado (`payment_id` + `amount` + `currency` + `reason`).
2. Se busca si ya existe un registro para esa `Idempotency-Key`:
   - **No existe** → se valida y procesa el refund normalmente. Se persiste `(idempotencyKey, requestHash, response)`. Se devuelve `201 Created`.
   - **Existe y `requestHash` coincide** → se devuelve la respuesta almacenada, sin reprocesar reglas de negocio ni persistir nada nuevo. `200 OK`.
   - **Existe y `requestHash` NO coincide** → `409 Conflict`. No se modifica ningún estado.
3. La combinación `(Idempotency-Key)` es única a nivel de base de datos (constraint), lo que además protege ante condiciones de carrera en peticiones concurrentes.

## 5. Especificación de seguridad / PCI-DSS

- El campo `reason` se valida contra un patrón que detecta:
  - Secuencias de 13 a 19 dígitos consecutivos (con o sin espacios/guiones cada 4) → posible PAN.
  - Secuencias de 3-4 dígitos inmediatamente precedidas de palabras como `cvv`, `cvc`, `cv2` (case-insensitive) → posible CVV.
- Si se detecta un patrón sospechoso, la API responde `400 Bad Request` con `error: "SENSITIVE_DATA_DETECTED"` **sin ecoar el valor recibido** en la respuesta ni en los logs.
- Ningún log de aplicación incluye: el header `Authorization`, el body crudo de la petición, ni el contenido del campo `reason`. Solo se loguean identificadores no sensibles (`paymentId`, `idempotencyKey`, `refundId`, `status`, código HTTP).
- La autenticación (`Bearer <token>`) se valida en un filtro previo al controlador; un token ausente o inválido corta la petición antes de tocar lógica de negocio.

## 6. Fuera de alcance (explícito)

- Gestión de usuarios/comercios reales o emisión de tokens (se simula con un token estático configurable, ver `README.md`).
- Integración con procesador de pagos real / pasarela externa.
- Conversión de divisas.
- Persistencia productiva (se usa H2 en memoria).
