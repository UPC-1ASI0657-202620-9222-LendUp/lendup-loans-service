package com.lendup.prestamos.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.port.in.LoanEventUseCases;
import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.config.RabbitConfiguration;
import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.infrastructure.messaging.OutboxRelay;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.*;
import com.lendup.prestamos.support.TestAuthConfiguration;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** RabbitMQ real: consumo con inbox, publicación confirmada y enrutada, orden por préstamo y DLQ. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"firebase.project-id=test", "lendup.outbox.relay-delay-ms=3600000", "lendup.overdue.check-delay-ms=3600000"})
class RabbitMessagingIT {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("lendup_prestamos").withUsername("test").withPassword("test");
  @Container static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.2-management-alpine");

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.rabbitmq.host", rabbit::getHost); r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    r.add("spring.rabbitmq.username", rabbit::getAdminUsername); r.add("spring.rabbitmq.password", rabbit::getAdminPassword);
  }

  @Autowired RabbitTemplate template; @Autowired RabbitAdmin admin; @Autowired OutboxRelay relay; @Autowired OutboxJpaRepository outbox; @Autowired ProcessedEventJpaRepository processed;
  @Autowired PrestamoJpaRepository prestamos; @Autowired PrestamoRepository repository; @Autowired LoanEventUseCases loans; @Autowired JsonMapper json;
  final UUID reserva = UUID.randomUUID(), prestamista = UUID.randomUUID(), prestatario = UUID.randomUUID();
  String captura;

  @BeforeEach void setup() {
    outbox.deleteAll(); processed.deleteAll(); prestamos.deleteAll();
    captura = "capture-" + UUID.randomUUID();
    admin.declareQueue(new org.springframework.amqp.core.Queue(captura, false, false, false));
  }

  @AfterEach void borrarCaptura() { admin.deleteQueue(captura); }

  private void escuchar(String routingKey) { admin.declareBinding(new Binding(captura, Binding.DestinationType.QUEUE, RabbitConfiguration.EVENTS_EXCHANGE, routingKey, null)); }
  private void enviar(String routingKey, String tipo, Map<String, Object> payload) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("event_id", UUID.randomUUID()); e.put("event_type", tipo); e.put("event_version", 1); e.put("aggregate_version", 1); e.put("aggregate_id", reserva);
    e.put("occurred_at", Instant.now()); e.put("correlation_id", "it-rabbit"); e.put("payload", payload);
    template.convertAndSend(RabbitConfiguration.EVENTS_EXCHANGE, routingKey, json.writeValueAsBytes(e));
  }
  private void reservaConfirmada() {
    enviar("reservas.confirmada.v1", "ReservaConfirmada", Map.of("reserva_id", reserva, "publicacion_id", UUID.randomUUID(), "prestamista_usuario_id", prestamista,
        "prestatario_usuario_id", prestatario, "desde", "2026-12-10T09:00:00", "hasta", "2026-12-12T09:00:00", "tarifa_diaria_acordada", 20.0, "moneda_acordada", "PEN"));
    await().atMost(Duration.ofSeconds(15)).until(() -> repository.findByReserva(reserva).isPresent());
  }

  @Test void deReservaConfirmadaAPrestamoCreado() {
    escuchar("prestamos.creado.v1");
    reservaConfirmada();
    relay.relay();
    Message recibido = template.receive(captura, 5000);
    assertNotNull(recibido);
    JsonNode e = json.readTree(recibido.getBody());
    assertEquals("PrestamoCreado", e.get("event_type").asString());
    assertEquals("it-rabbit", e.get("correlation_id").asString());
    assertEquals(reserva.toString(), e.get("payload").get("reserva_id").asString());
    assertEquals("2026-12-10T09:00:00", e.get("payload").get("entrega_programada_en").asString());
    assertTrue(outbox.findAll().stream().allMatch(o -> o.getPublishedAt() != null));
  }

  @Test void sagaDeCancelacionRespondeAReservas() {
    escuchar("prestamos.#");
    reservaConfirmada();
    enviar("reservas.cancelacion-solicitada.v1", "CancelacionReservaSolicitada", Map.of("reserva_id", reserva, "solicitud_id", UUID.randomUUID(), "solicitada_por_usuario_id", prestatario, "motivo_cancelacion", "x"));
    await().atMost(Duration.ofSeconds(15)).until(() -> repository.findByReserva(reserva).orElseThrow().estado() == EstadoPrestamo.CANCELADO);
    relay.relay(); relay.relay();
    List<String> tipos = new ArrayList<>();
    for (Message m = template.receive(captura, 3000); m != null; m = template.receive(captura, 500)) tipos.add(json.readTree(m.getBody()).get("event_type").asString());
    assertEquals(List.of("PrestamoCreado", "PrestamoCancelado"), tipos);
  }

  @Test void sinNadieEscuchandoElEventoQuedaPendienteYNoSePierde() {
    reservaConfirmada();
    relay.relay();
    OutboxEventEntity e = outbox.findAll().getFirst();
    assertNull(e.getPublishedAt());
    assertTrue(e.getLastError().contains("Ninguna cola suscrita a prestamos.creado.v1"), e.getLastError());
    assertEquals(1, e.getAttempts());
  }

  @Test void prestamoFinalizadoLlegaAlEstandarYAReputacionConElMismoEventId() {
    escuchar("prestamos.finalizado.v1"); escuchar("loans.loan-finalized.v1");
    UUID prestamo = UUID.randomUUID();
    outbox.save(new OutboxEventEntity(UUID.randomUUID(), "PRESTAMO", prestamo, "PrestamoFinalizado", 1, 9, json.writeValueAsString(Map.of("prestamo_id", prestamo, "reserva_id", reserva,
        "publicacion_id", UUID.randomUUID(), "prestamista_usuario_id", prestamista, "prestatario_usuario_id", prestatario, "finalizado_en", "2026-12-12T14:00:00Z")), "texto-libre", Instant.now()));
    relay.relay();
    Map<String, JsonNode> recibidos = new HashMap<>();
    for (Message m = template.receive(captura, 3000); m != null; m = template.receive(captura, 500)) recibidos.put(m.getMessageProperties().getReceivedRoutingKey(), json.readTree(m.getBody()));
    assertEquals(Set.of("prestamos.finalizado.v1", "loans.loan-finalized.v1"), recibidos.keySet());
    JsonNode plano = recibidos.get("loans.loan-finalized.v1");
    assertFalse(plano.has("payload"));
    assertEquals(prestamo.toString(), plano.get("prestamo_id").asString());
    assertEquals(recibidos.get("prestamos.finalizado.v1").get("event_id").asString(), plano.get("event_id").asString());
    assertDoesNotThrow(() -> UUID.fromString(plano.get("correlation_id").asString()));
    assertNotNull(outbox.findAll().getFirst().getPublishedAt());
  }

  @Test void unMensajeInvalidoTerminaEnLaDlq() {
    admin.purgeQueue(RabbitConfiguration.DLQ);
    enviar("pagos.pago-confirmado.v1", "PagoConfirmado", Map.of("prestamo_id", UUID.randomUUID(), "tipo", "PAGO_TARIFA", "confirmada_en", Instant.now()));
    await().atMost(Duration.ofSeconds(20)).until(() -> admin.getQueueInfo(RabbitConfiguration.DLQ).getMessageCount() == 1);
    assertEquals(0, processed.count());
  }
}
