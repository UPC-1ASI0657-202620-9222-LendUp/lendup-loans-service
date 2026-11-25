# Migración desde el monolito

No hay lectura cross-schema en runtime. El cutover es un procedimiento separado:

1. Respaldar `prestamos` y `cambios_fecha_prestamo` del monolito.
2. Copiar ambas tablas convirtiendo ids `CHAR(36)` a `BINARY(16)` (`UUID_TO_BIN`). `garantia_monetaria_acordada` sale de `reservas` del monolito y `version` empieza en 1. Las fechas pactadas ya están en hora de Lima y no se convierten.
3. Registrar los préstamos migrados en Pagos (`prestamos_pagables`), porque no pasaron por `PrestamoCreado`.
4. Activar en Reservas `LOAN_SERVICE_BASE_URL`, `LOAN_SERVICE_TOKEN` y `LOAN_CONTACT_ACCESS_URI_TEMPLATE`.
5. Configurar `LOANS_SERVICE_URL` en el gateway. Desde ese momento el monolito no recibe `/prestamos` ni `/calendario`.
6. Apagar en el monolito el módulo `prestamos` y la creación de préstamos de `ReservasApplicationService`.

## Qué necesita Evidencias (sigue en el monolito)

`IncidentsService` y las evidencias leen `prestamos` y `reservas` de la base del monolito, que dejan de llenarse. Antes del cutover el monolito debe:

- Mantener `prestamos` como **proyección de solo lectura**, alimentada por `prestamos.*.v1`: `PrestamoCreado` trae participantes, fechas, garantía y moneda; los demás traen el cambio de estado. Es el mismo enfoque que ya usa la rama `feature/sprint2-payments-notifications-extraction` para los eventos de Pagos.
- Publicar `evidencias.incidencia-reportada.v1` (en `report`) y `evidencias.incidencia-resuelta.v1` (en `resolve`), con `incidencia_id`, `prestamo_id`, participantes y, en la resolución, `decision_garantia` y `monto_garantia_afectado`. Los consumen Préstamos, Pagos y Notificaciones.
- Dejar de actualizar `prestamos.incidencias_pendientes`: ese contador ahora es de Préstamos.
- Exponer la lectura de evidencias de un préstamo (configurable aquí con `EVIDENCE_LOAN_EVIDENCE_URI_TEMPLATE`).

Diferencias de comportamiento a comunicar al frontend: ver "Cambios respecto al monolito" en [api-contract.md](api-contract.md).
