# Eventos

Exchange durable: `lendup.domain.events` (topic). DLX: `lendup.domain.events.dlx`. Cola consumidora: `loans.events.v1`; DLQ: `loans.events.v1.dlq`. Envelope: `event_id`, `event_type`, `event_version`, `aggregate_version`, `aggregate_id`, `occurred_at` UTC, `correlation_id`, `payload`. Schemas y ejemplos en `contracts/events/v1`; `EmittedEventsContractTest` comprueba que el código emita exactamente esos campos.

## Publicados

| Evento | Routing key | Cuándo | Quién lo consume |
|---|---|---|---|
| `PrestamoCreado` | `prestamos.creado.v1` | Llega una reserva confirmada | Pagos |
| `ObjetoEntregado` | `prestamos.objeto-entregado.v1` | El prestamista registra la entrega | Notificaciones (informativo) |
| `PrestamoActivado` | `prestamos.activado.v1` | El prestatario confirma la recepción | Pagos (libera la tarifa), Notificaciones |
| `ExtensionSolicitada` | `prestamos.extension-solicitada.v1` | El prestatario pide una extensión | Notificaciones (informativo) |
| `ExtensionAceptada` | `prestamos.extension-aceptada.v1` | El prestamista acepta una extensión | Pagos (habilita el cobro), Notificaciones |
| `ExtensionRechazada` | `prestamos.extension-rechazada.v1` | El prestamista la rechaza | Notificaciones (informativo) |
| `ReprogramacionPropuesta` | `prestamos.reprogramacion-propuesta.v1` | El prestamista propone otra fecha | Notificaciones (informativo) |
| `ReprogramacionRechazada` | `prestamos.reprogramacion-rechazada.v1` | El prestatario la rechaza | Notificaciones (informativo) |
| `FechaDevolucionActualizada` | `prestamos.fecha-devolucion-actualizada.v1` | Extensión pagada o reprogramación aceptada | Notificaciones (mueve el recordatorio); Reservas podrá ampliar el periodo |
| `DevolucionRegistrada` | `prestamos.devolucion-registrada.v1` | El prestatario registra la devolución | Pagos (descarta la extensión sin pagar), Notificaciones (informativo) |
| `PrestamoVencido` | `prestamos.vencido.v1` | Llega la fecha vigente sin devolución | Notificaciones (informativo) |
| `PrestamoFinalizado` | `prestamos.finalizado.v1` | El prestamista confirma la devolución | Pagos (devuelve la garantía), Notificaciones; Reputación recibe su copia |
| `PrestamoCancelado` | `prestamos.cancelado.v1` | Saga de cancelación aceptada | Reservas |
| `CancelacionRechazada` | `prestamos.cancelacion-rechazada.v1` | Saga de cancelación rechazada | Reservas, Notificaciones |
| `PeriodoPrestamoLiberado` | `prestamos.periodo-liberado.v1` | Cierre antes del fin del periodo reservado | Reservas |

Todos los payloads incluyen `prestamo_id`, `reserva_id`, `publicacion_id`, `prestatario_usuario_id` y `prestamista_usuario_id`, para que los consumidores no tengan que consultar a Préstamos. `aggregate_id` es siempre el préstamo.

### Reputación

Su consumidor no lee el envelope estándar: exige los campos en la raíz, rechaza cualquier otro y pide `correlation_id` como UUID. Por eso `PrestamoFinalizado` se envía además en ese formato (mismo `event_id`) con la routing key `REPUTATION_LOAN_FINISHED_ROUTING_KEY` (`loans.loan-finalized.v1`, la que propone su repositorio). Ejemplo: `contracts/events/v1/reputation-loan-finished.example.json`. Reputación solo debe configurar `RABBITMQ_EXCHANGE=lendup.domain.events`, `LOAN_SUBSCRIPTION_ENABLED=true` y, para publicar sus calificaciones, `OUTBOX_PUBLISHER_ENABLED=true`. Cuando lea el envelope estándar, esta copia se desactiva dejando la variable vacía.

### Entrega garantizada

El relay publica con *publisher confirms* y `mandatory=true`. Un evento con consumidores conocidos solo se marca publicado si RabbitMQ lo confirma **y** lo entregó al menos a una cola; si no, queda en `outbox_events` con `last_error` y se reintenta con espera creciente (máximo 5 minutos) sin agotar los intentos: sale en cuanto el consumidor declara su cola. `OUTBOX_MAX_ATTEMPTS` solo limita los errores del broker. Los informativos se dan por publicados aunque nadie escuche, para que Préstamos funcione sin Notificaciones. Los eventos de un mismo préstamo salen en orden: `PrestamoActivado` nunca antes que `PrestamoCreado`. Si uno agota sus intentos deja de bloquear a los siguientes y queda para revisión.

## Consumidos

| Evento | Routing key | Productor | Estado | Uso |
|---|---|---|---|---|
| `ReservaConfirmada` | `reservas.confirmada.v1` | Reservas | Disponible | Crea el préstamo |
| `CancelacionReservaSolicitada` | `reservas.cancelacion-solicitada.v1` | Reservas | Disponible | Decide la cancelación |
| `ReservaCancelada` | `reservas.cancelada.v1` | Reservas | Disponible | Cancela el préstamo si no ha iniciado |
| `PagoConfirmado` | `pagos.pago-confirmado.v1` | Pagos | Disponible | `PAGO_TARIFA` habilita la entrega; `PAGO_EXTENSION` aplica la fecha |
| `PagoRechazado` | `pagos.pago-rechazado.v1` | Pagos | Disponible | Solo auditoría: el préstamo no cambia y se puede reintentar |
| `GarantiaConstituida` | `pagos.garantia-constituida.v1` | Pagos | Disponible | Habilita la entrega |
| `IncidenciaReportada` | `INCIDENT_REPORTED_ROUTING_KEY` (`evidencias.incidencia-reportada.v1`) | Evidencias (sin microservicio) | **Pendiente** | Bloquea el cierre |
| `IncidenciaResuelta` | `INCIDENT_RESOLVED_ROUTING_KEY` (`evidencias.incidencia-resuelta.v1`) | Evidencias (sin microservicio) | **Pendiente** | Libera el cierre |

El consumidor despacha por routing key. Un solo consumidor por instancia conserva el orden de los eventos de una reserva. Un evento sobre un préstamo inexistente se reintenta y termina en la DLQ; se puede reenviar desde la consola de RabbitMQ.

## Contratos pendientes con otros equipos

- **Evidencias (nuevo microservicio):** publicar las dos incidencias y consumir `prestamos.*` para conocer al préstamo, sus participantes y la garantía acordada (viajan en `PrestamoCreado`).
- **Reservas:** no amplía el periodo ocupado cuando se aplica una extensión (ADR 0002). Ya puede consumir `prestamos.fecha-devolucion-actualizada.v1`, que trae `reserva_id` y la nueva fecha.
