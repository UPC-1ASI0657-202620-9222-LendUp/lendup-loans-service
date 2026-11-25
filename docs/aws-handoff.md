# Entrega a AWS

## Artefacto

Imagen: `DOCKERHUB_USERNAME/lendup-loans-service:<semver|sha>`. Plataforma `linux/amd64`; puerto 8087; liveness `/actuator/health/liveness`; readiness `/actuator/health/readiness`. Registrar tag y digest inmutable después de publicar.

## Configuración

Obligatorias: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `FIREBASE_PROJECT_ID`, ADC Firebase externo, `CORS_ORIGINS`, `IDENTITY_SERVICE_BASE_URL`, `IDENTITY_FIREBASE_USER_URI_TEMPLATE` y `LOANS_RESERVATIONS_SERVICE_TOKEN` (32 caracteres o más; el mismo valor va en `LOAN_SERVICE_TOKEN` de Reservas).

Opcionales: `IDENTITY_SERVICE_TOKEN` (cuando Identidad registre a Préstamos), `EVIDENCE_SERVICE_BASE_URL`, `EVIDENCE_LOAN_EVIDENCE_URI_TEMPLATE`, `INCIDENT_*_ROUTING_KEY`, `REPUTATION_LOAN_FINISHED_ROUTING_KEY`, `SERVER_PORT=8087`, `DB_POOL_MAX_SIZE=20`, `OUTBOX_RELAY_DELAY_MS=1000`, `OUTBOX_MAX_ATTEMPTS=10`, `OVERDUE_CHECK_DELAY_MS=60000`, `OVERDUE_BATCH_SIZE=100`. Mantener `SPRINGDOC_ENABLED=false` y `SWAGGER_PUBLIC=false`. No definir `FIREBASE_AUTH_EMULATOR_HOST` en producción: acepta tokens sin firma.

Secretos en Secrets Manager/Parameter Store; nunca en la imagen, Git ni logs.

## Base de datos

Base `lendup_prestamos`, InnoDB. Usuario de runtime limitado a ese esquema. Para que la auditoría sea solo de inserción (RNF21), otorgar por tabla:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON lendup_prestamos.prestamos TO 'lendup_prestamos'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON lendup_prestamos.cambios_fecha_prestamo TO 'lendup_prestamos'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON lendup_prestamos.outbox_events TO 'lendup_prestamos'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON lendup_prestamos.processed_events TO 'lendup_prestamos'@'%';
GRANT SELECT, INSERT ON lendup_prestamos.registro_auditoria TO 'lendup_prestamos'@'%';
GRANT SELECT ON lendup_prestamos.flyway_schema_history TO 'lendup_prestamos'@'%';
```

Flyway debe correr con un usuario DDL en una única tarea antes de escalar.

## Red

- Gateway: `LOANS_SERVICE_URL` para `/api/v1/prestamos/**` y `/api/v1/calendario`, después de las rutas específicas de Pagos, Reputación y Evidencias. No publicar `/internal/**`.
- Reservas debe alcanzar `/internal/v1/prestamos/**` por red privada.
- MySQL y RabbitMQ en subredes privadas; RabbitMQ con TLS.

La imagen corre como UID/GID 10001 con filesystem de solo lectura y `/tmp` temporal.

## Réplicas

El relay usa `SKIP LOCKED`, el scheduler vuelve a comprobar el estado con la fila bloqueada y las operaciones sobre un préstamo se serializan, así que admite varias réplicas. Con más de un consumidor, los eventos de una misma reserva pueden procesarse en paralelo: si un evento llega antes de que exista el préstamo, se reintenta. Conviene iniciar con una réplica.

## Despliegue y rollback

1. Aprovisionar BD, broker y secretos. Ejecutar Flyway y desplegar una instancia; comprobar readiness.
2. Desplegar Pagos y Notificaciones antes de abrir tráfico: si no existen sus colas, `PrestamoCreado` queda pendiente en el outbox.
3. Configurar Reservas (`LOAN_*`) y luego el gateway.
4. Vigilar `outbox_events` (filas con `last_error`), la DLQ `loans.events.v1.dlq` y la métrica de vencidos.

Rollback: quitar `LOANS_SERVICE_URL` del gateway devuelve el tráfico al monolito (los préstamos creados aquí no existen allí). No revertir Flyway.
