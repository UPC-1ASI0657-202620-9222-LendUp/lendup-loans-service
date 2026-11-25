# Contrato API

Base externa: `/api/v1`. Todos los endpoints requieren `Authorization: Bearer <Firebase ID token>` y usan JSON `snake_case`. Contrato formal: [`contracts/openapi/loans-v1.yaml`](../contracts/openapi/loans-v1.yaml).

| Método | Ruta | Quién | Resultado |
|---|---|---|---|
| GET | `/prestamos?estado=` | Participante | 200, mis préstamos |
| GET | `/prestamos/{id}` | Participante | 200 con `cambios_fecha` y `evidencias` |
| GET | `/calendario` | Cualquiera | 200, reservas y préstamos sin cancelados, por fecha de entrega |
| POST | `/prestamos/{id}/entrega` | Prestamista | 200 `ENTREGA_REGISTRADA`; 409 si falta el pago o la garantía |
| POST | `/prestamos/{id}/recepcion` | Prestatario | 200 `ACTIVO` |
| POST | `/prestamos/{id}/devolucion` | Prestatario | 200 `DEVOLUCION_REGISTRADA` |
| POST | `/prestamos/{id}/confirmacion-devolucion` | Prestamista | 200 `FINALIZADO`; 409 con incidencias pendientes |
| POST | `/prestamos/{id}/extensiones` | Prestatario | 201 `PENDIENTE` |
| POST | `/prestamos/{id}/extensiones/{subid}/respuesta` | Prestamista | 200 `ACEPTADA_PENDIENTE_PAGO` o `RECHAZADA` |
| POST | `/prestamos/{id}/extensiones/cotizacion` | Participante | 200; con `fecha_propuesta_en` incluye `costo_adicional` |
| POST | `/prestamos/{id}/reprogramaciones` | Prestamista | 201 `PENDIENTE`, sin costo |
| POST | `/prestamos/{id}/reprogramaciones/{subid}/respuesta` | Prestatario | 200 `APLICADA` o `RECHAZADA` |
| GET | `/prestamos/{id}/cambios-fecha` | Participante | 200, historial (RF47) |
| GET | `/prestamos/{id}/cotizacion-pago` | Participante | 200, tarifa, garantía e `importe_tarifa` |

Errores: `{ "status": 409, "error": "CONFLICTO_NEGOCIO", "message": "...", "timestamp": "...Z" }`. Códigos: 400 `SOLICITUD_INVALIDA`, 401 `AUTENTICACION_REQUERIDA`/`TOKEN_INVALIDO`, 403 `NO_AUTORIZADO`/`EMAIL_NOT_VERIFIED`, 404 `NO_ENCONTRADO`, 409 `CONFLICTO_NEGOCIO`/`CONFLICTO_DATOS`, 503 `DEPENDENCIA_NO_DISPONIBLE`.

## Reglas

- **Entrega:** solo `RESERVADO`, con `pago_tarifa_confirmado_en` y, si la reserva pide garantía, `garantia_constituida_en`.
- **Extensión:** préstamo `ACTIVO` y antes de la fecha vigente; la nueva fecha debe ser posterior y no superponerse con otro préstamo en curso del mismo objeto; solo un cambio en curso a la vez. Costo = tarifa diaria × bloques iniciados de 24 h del periodo adicional (igual que `extensionCost` del frontend). Si el cliente envía `costo_adicional` y no coincide: 409 con el monto correcto.
- **Reprogramación:** la propone el prestamista; puede adelantar o atrasar la devolución, nunca a una fecha pasada.
- **Fechas:** se aceptan sin offset (hora de Lima) o con offset/`Z`, que se convierte a Lima.
- **Participación:** un usuario ajeno recibe 403; el participante con el rol contextual equivocado también.

## Cambios respecto al monolito (para el frontend)

- Campos nuevos, todos opcionales de usar: `estado_detalle`, `garantia_monetaria_acordada`, `cambios_fecha`, `importe_tarifa` y `costo_adicional` en la cotización.
- `cambios_fecha` permite mostrar el historial de extensiones y reprogramaciones, que el frontend hoy deja vacío (`extensions: []`).
- `costo_adicional` en la solicitud de extensión pasa a ser opcional: manda el cálculo del servidor.
- La reprogramación solo la propone el prestamista (RF25); el monolito la aceptaba de cualquiera.
- El calendario ya no incluye préstamos cancelados.
- `evidencias` llega vacío hasta que Evidencias publique su ruta de lectura.

## Rutas internas

`GET /internal/v1/prestamos/reservas/{reserva_id}/participantes/{usuario_id}/contacto` → `{"contacto_vigente": true|false}`. Requiere `Authorization: Bearer <LOANS_RESERVATIONS_SERVICE_TOKEN>`. Es `true` solo para un participante y mientras el préstamo no esté `FINALIZADO` ni `CANCELADO` (RF52). 404 si la reserva todavía no tiene préstamo. No se publica en el API Gateway.
