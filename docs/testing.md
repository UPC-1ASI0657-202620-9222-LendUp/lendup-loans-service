# Pruebas

`mvnw.cmd clean verify` ejecuta:

- **Dominio:** `PrestamoStateTest` recorre la tabla completa de transiciones (cada acción en los 8 estados); `PoliticaDeCostoTest` cubre los bloques iniciados de 24 horas (R26), los mismos casos que `extensionCost` del frontend.
- **Aplicación:** `LoanLifecycleTest` (entrega, recepción, vencimiento en hora de Lima, devolución, incidencias, cancelación, idempotencia, auditoría y versiones), `DateChangeTest` (extensiones y reprogramaciones, superposición, pago tardío, pago sin efecto) y `LoanQueryServiceTest` (listados, calendario, cotizaciones y contacto).
- **Seguridad:** `SecurityFiltersTest`: token inválido, correo no verificado, roles por header ignorados, credencial de servicio ausente, distinta o débil, y errores en UTF-8.
- **Contrato con el frontend:** `FrontendContractTest` (MockMvc): rutas, cuerpos, códigos y los campos que lee `mapLoan`.
- **Contrato de eventos:** `EmittedEventsContractTest` compara lo que emite el código con `contracts/events/v1` y con lo que exigen Pagos, Notificaciones, Reservas y Reputación; `LoanEventConsumerTest` usa los mismos ejemplos que documentan los productores.
- **Mensajería:** `OutboxRelayTest`: routing key de cada evento, broker caído, evento sin suscriptores (obligatorio, informativo y que nunca se agota), copia para Reputación y tipo desconocido.
- **Clientes HTTP:** `RestAdaptersTest` con WireMock (Identidad interna y `/estudiantes/me`, Evidencias opcional, errores y timeout).
- **Aceptación (Cucumber, Gherkin en español):** US18 a US25 y US42 a US45. Reporte HTML: `target/cucumber-report.html`.
- **Integración `*IT` (failsafe, Testcontainers):**
  - `MySqlLoansIT`: Flyway + `ddl-auto=validate`, ciclo completo por la API, fechas en hora de Lima sin corrimiento, inbox, rollback conjunto, scheduler, entrega y cancelación simultáneas, orden del outbox por préstamo y seguridad.
  - `RabbitMessagingIT`: de `ReservaConfirmada` a `PrestamoCreado`, saga de cancelación, evento que nadie recibe queda pendiente, `PrestamoFinalizado` en el formato estándar y en el de Reputación, y mensaje inválido en la DLQ.

Las pruebas Testcontainers llevan `disabledWithoutDocker=true`: sin Docker se marcan omitidas, y una omisión no equivale a éxito.

## Postman

`docs/postman/lendup-loans.postman_collection.json` prueba el servicio solo. Orden:

1. **0. Preparación:** tokens del prestamista y del prestatario, sus `usuario.id` y la cola espía `debug.lendup.eventos`.
2. **1. Eventos simulados:** `ReservaConfirmada` (crea el préstamo), `PagoConfirmado` y `GarantiaConstituida`. Cada uno debe responder `routed=true`.
3. **2. Ciclo del préstamo:** entrega, recepción, extensión, devolución y cierre, con sus errores esperados (403, 404, 409).
4. **3. Broker:** ver los eventos `prestamos.*` en la cola espía.

## De punta a punta

El entorno `e2e` del equipo levanta los siete microservicios que existen (Identidad, Catálogo, Reservas, Préstamos, Pagos, Notificaciones y Reputación) y el API Gateway, reales, con un solo RabbitMQ y un esquema de MySQL por servicio. Firebase es el emulador oficial y Mercado Pago el modo simulado de Pagos. Solo los dos eventos de Evidencias se publican a mano.

Recorre: registro y publicación, solicitudes rechazadas y canceladas, ciclo completo con extensión y calificaciones, saga de cancelación con reembolsos, cancelación rechazada, reprogramaciones, vencimiento, incidencia con afectación parcial, servicios caídos, independencia de datos y todas las rutas que usa el frontend.

Para RNF15 (500 usuarios concurrentes) debe medirse en un entorno equivalente a producción; no se incluyen resultados de carga.
