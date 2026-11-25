# Changelog

## Unreleased

- Servicio inicial de Préstamos extraído del monolito con arquitectura hexagonal y los componentes de la Fig. 16.
- Base de datos propia `lendup_prestamos` (Flyway): `prestamos` y `cambios_fecha_prestamo` (Fig. 9), outbox, inbox y auditoría de solo inserción.
- Agregado `Prestamo` con patrón State (8 estados, incluido `PENDIENTE_INCIDENCIA`) y `PoliticaDeCosto` como Strategy (bloques iniciados de 24 horas).
- Endpoints compatibles con el frontend: entrega, recepción, devolución y su confirmación, extensiones, reprogramaciones, cotizaciones, listado, detalle y calendario. Nueva ruta `GET /prestamos/{id}/cambios-fecha` (RF47).
- Vencimiento automático con `@Scheduled` en hora de Lima (RF53).
- Saga de cancelación con Reservas y contrato interno de vigencia del contacto (RF52).
- Eventos `prestamos.*.v1` por transactional outbox con publisher confirms, `mandatory`, orden por préstamo y reintentos con espera creciente; consumo con inbox, reintentos y DLQ; contratos en `contracts/events/v1`.
- Eventos de cambios de fecha (`ExtensionSolicitada`, `ExtensionRechazada`, `ReprogramacionPropuesta`, `ReprogramacionRechazada`, `FechaDevolucionActualizada`) para avisar a la contraparte y mover los recordatorios (US21, US22, US43).
- Copia de `PrestamoFinalizado` en el formato que exige Reputación (`REPUTATION_LOAN_FINISHED_ROUTING_KEY`).
- Pruebas unitarias, de contrato (frontend y eventos), WireMock, Testcontainers (MySQL, RabbitMQ) y escenarios Cucumber en español (US18 a US24, US42 a US45).
- Colección Postman, OpenAPI, ADRs y workflows de CI y publicación en Docker Hub.
