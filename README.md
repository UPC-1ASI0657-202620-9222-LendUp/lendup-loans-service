# LendUp Loans Service

Microservicio Java 21/Spring Boot 4 para el bounded context de Préstamos. Gestiona el préstamo desde que se confirma la reserva hasta su cierre: entrega, recepción, extensiones, reprogramaciones, vencimiento y devolución. Conserva las rutas `/prestamos` y `/calendario` del monolito que consume el frontend, usa MySQL/InnoDB con Flyway, Firebase ID tokens y RabbitMQ con transactional outbox e inbox.

## Estado y límites

- **Ciclo completo:** el préstamo nace de `reservas.confirmada.v1`, se habilita con los eventos de Pagos y publica los eventos que Pagos, Notificaciones y Reservas esperaban. Probado de punta a punta con esos tres servicios reales (ver [pruebas](docs/testing.md)).
- **Saga de cancelación:** responde a `CancelacionReservaSolicitada` con `PrestamoCancelado` o `CancelacionRechazada`.
- **Extensiones:** el costo lo calcula el servidor por bloques iniciados de 24 horas y la fecha cambia cuando Pagos confirma el cobro.
- **Contratos pendientes:**
  - Evidencias e Incidencias aún no tiene microservicio y no publica `evidencias.incidencia-reportada.v1` ni `evidencias.incidencia-resuelta.v1`. Las routing keys son configurables.
  - Evidencias tampoco tiene una ruta para leer las evidencias de un préstamo. Mientras tanto el detalle devuelve `evidencias: []`.
  - Identidad no tiene a Préstamos entre los servicios autorizados de `/internal/v1/identidad`. Mientras tanto se usa `GET /api/v1/estudiantes/me` con el token del usuario.
  - Reputación recibe una copia de `PrestamoFinalizado` en su propio formato; solo debe configurar `RABBITMQ_EXCHANGE=lendup.domain.events` y activar su suscripción (ver [eventos](docs/events.md)).
  - Reservas no tiene un contrato para ampliar el periodo ocupado cuando se extiende un préstamo (ver [ADR 0002](docs/decisions/0002-overlap-check.md)).

Detalles: [arquitectura](docs/architecture.md), [API](docs/api-contract.md), [eventos](docs/events.md), [pruebas](docs/testing.md), [AWS](docs/aws-handoff.md), [migración](docs/migration-from-monolith.md).

## Inicio local

Requisitos: JDK 21 o superior, Docker con Compose v2 y la credencial Firebase de desarrollo (proyecto `lendup-desarrollo`).

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force .secrets
# Colocar una credencial de desarrollo en .secrets/firebase-service-account.json; nunca versionarla.
.\mvnw.cmd clean verify
docker compose up --build
```

API: `http://localhost:8087/api/v1`; readiness: `http://localhost:8087/actuator/health/readiness`. MySQL queda en `127.0.0.1:3313`, RabbitMQ en `127.0.0.1:5677` y su consola en `http://localhost:15677`. Se cambian con `MYSQL_HOST_PORT`, `RABBITMQ_HOST_PORT` y `RABBITMQ_MANAGEMENT_HOST_PORT`.

Swagger está deshabilitado por defecto; para desarrollo local: `SPRINGDOC_ENABLED=true` (requiere Firebase salvo `SWAGGER_PUBLIC=true`, que no debe usarse en producción). El contrato también está en [`contracts/openapi/loans-v1.yaml`](contracts/openapi/loans-v1.yaml).

### De dónde sale un préstamo

No hay `POST /prestamos`: un préstamo se crea solo cuando llega `reservas.confirmada.v1`. Para probar el servicio solo, la carpeta *1. Eventos simulados* de la colección Postman publica ese evento y los de Pagos en RabbitMQ. Cada petición debe responder `routed=true`.

### Broker compartido (integración con el equipo)

Cada `compose.yaml` levanta su propio RabbitMQ para probar el servicio solo, pero dos brokers distintos nunca intercambian eventos. Para integrar, todos los servicios deben apuntar al mismo broker. En `.env`:

```
RABBITMQ_HOST=host.docker.internal
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<usuario de ese broker>
RABBITMQ_PASSWORD=<contraseña de ese broker>
```

### Integración con Reservas

Reservas consulta si el teléfono de la contraparte sigue visible. Configurar en Reservas:

```
LOAN_SERVICE_BASE_URL=http://<préstamos>:8087
LOAN_SERVICE_TOKEN=<mismo valor que LOANS_RESERVATIONS_SERVICE_TOKEN>
LOAN_CONTACT_ACCESS_URI_TEMPLATE=/internal/v1/prestamos/reservas/{reserva_id}/participantes/{usuario_id}/contacto
```

### API Gateway

`LOANS_SERVICE_URL` en el gateway envía `/prestamos/**` y `/calendario` a este servicio. Las rutas de Pagos (`/transacciones`), Reputación (`/calificaciones`) y Evidencias (`/evidencias`, `/analisis-evidencias`) que empiezan con `/prestamos/{id}/` van antes. `/internal/**` nunca se publica.

Para ejecutar solo el artefacto:

```powershell
.\mvnw.cmd clean package
java -jar target\lendup-loans-service-0.1.0-SNAPSHOT.jar
```

Las fechas pactadas (`entrega_programada_en`, `devolucion_original_en`, `devolucion_vigente_en` y `fecha_*_en`) son hora de negocio `America/Lima` sin offset, como en Reservas. El resto de timestamps `_en`, la auditoría y los eventos se emiten en UTC.

## Imagen

```powershell
docker build --platform linux/amd64 -t lendup-loans-service:local .
docker run --rm -p 8087:8087 --env-file .env -v "${PWD}\.secrets\firebase-service-account.json:/run/secrets/firebase-service-account.json:ro" lendup-loans-service:local
```

La publicación normal se realiza por tag `v*.*.*` mediante GitHub Actions (`.github/workflows/docker-publish.yml`, requiere la variable `DOCKERHUB_USERNAME` y el secreto `DOCKERHUB_TOKEN`).
