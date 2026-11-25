package com.lendup.prestamos.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.prestamos.config.EventRoutingProperties;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OutboxRelayTest {
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private final OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);

  private OutboxEventEntity evento(String tipo) { return new OutboxEventEntity(UUID.randomUUID(), "PRESTAMO", UUID.randomUUID(), tipo, 1, 1, "{}", "corr", NOW); }

  private static final String REPUTACION = "loans.loan-finalized.v1";
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  private void publicar(OutboxEventEntity evento) { publicar(evento, REPUTACION); }

  private void publicar(OutboxEventEntity evento, String rutaReputacion) {
    when(repository.pending(any(Instant.class), anyInt(), any(Pageable.class))).thenReturn(List.of(evento));
    new OutboxRelay(repository, rabbit, json, Clock.fixed(NOW, ZoneOffset.UTC), new EventRoutingProperties(null, null, rutaReputacion), 10).relay();
  }

  private OutboxEventEntity finalizado(String correlacion) {
    return new OutboxEventEntity(UUID.randomUUID(), "PRESTAMO", UUID.fromString("20000000-0000-4000-8000-000000000001"), "PrestamoFinalizado", 1, 7, """
        {"prestamo_id":"20000000-0000-4000-8000-000000000001","reserva_id":"60000000-0000-4000-8000-000000000001","publicacion_id":"70000000-0000-4000-8000-000000000001",
         "prestamista_usuario_id":"30000000-0000-4000-8000-000000000001","prestatario_usuario_id":"40000000-0000-4000-8000-000000000001","finalizado_en":"2026-11-02T12:00:00Z"}""", correlacion, NOW);
  }

  private JsonNode enviadoA(String routingKey) {
    ArgumentCaptor<byte[]> cuerpo = ArgumentCaptor.forClass(byte[].class);
    verify(rabbit).convertAndSend(eq("lendup.domain.events"), eq(routingKey), cuerpo.capture(), any(MessagePostProcessor.class), any(CorrelationData.class));
    return json.readTree(cuerpo.getValue());
  }

  private void brokerResponde(boolean devuelto) {
    doAnswer(inv -> {
      CorrelationData cd = inv.getArgument(4);
      if (devuelto) cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "lendup.domain.events", inv.getArgument(1)));
      cd.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
  }

  @ParameterizedTest @EnumSource(EventRoute.class)
  void cadaEventoSePublicaConSuRoutingKey(EventRoute ruta) {
    brokerResponde(false);
    OutboxEventEntity e = evento(ruta.eventType());
    publicar(e);
    verify(rabbit).convertAndSend(eq("lendup.domain.events"), eq(ruta.routingKey()), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    assertEquals(NOW, e.getPublishedAt());
    assertTrue(ruta.routingKey().matches("prestamos\\.[a-z-]+\\.v1"));
  }

  @Test void brokerCaidoDejaElEventoPendienteConEspera() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("broker caido"))).when(rabbit)
        .convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    OutboxEventEntity e = evento("PrestamoCreado");
    publicar(e);
    assertNull(e.getPublishedAt()); assertEquals(1, e.getAttempts()); assertTrue(e.getNextAttemptAt().isAfter(NOW)); verify(repository).save(e);
  }

  @Test void siNadieRecibeUnEventoConConsumidoresQuedaPendiente() {
    brokerResponde(true);
    OutboxEventEntity e = evento("PrestamoCreado");
    publicar(e);
    assertNull(e.getPublishedAt());
    assertTrue(e.getLastError().contains("prestamos.creado.v1"));
  }

  @Test void unEventoInformativoSinSuscriptoresSeDaPorPublicado() {
    brokerResponde(true);
    for (String tipo : List.of("ObjetoEntregado", "DevolucionRegistrada", "PrestamoVencido")) {
      OutboxEventEntity e = evento(tipo);
      publicar(e);
      assertEquals(NOW, e.getPublishedAt(), tipo);
    }
  }

  // ---- Compatibilidad con Reputación, que exige un mensaje plano con exactamente estos campos (extra="forbid") ----

  @Test void prestamoFinalizadoTambienSaleEnElFormatoPlanoQueExigeReputacion() {
    brokerResponde(false);
    OutboxEventEntity e = finalizado("50000000-0000-4000-8000-000000000001");
    publicar(e);
    JsonNode plano = enviadoA(REPUTACION);
    assertEquals(Set.of("event_id", "event_type", "event_version", "prestamo_id", "prestamista_usuario_id", "prestatario_usuario_id", "finalizado_en", "correlation_id", "aggregate_version"),
        new HashSet<>(plano.propertyNames()));
    assertEquals(e.getId().toString(), plano.get("event_id").asString());
    assertEquals("PrestamoFinalizado", plano.get("event_type").asString());
    assertEquals(1, plano.get("event_version").asInt());
    assertEquals(7, plano.get("aggregate_version").asLong());
    assertEquals("30000000-0000-4000-8000-000000000001", plano.get("prestamista_usuario_id").asString());
    assertEquals("2026-11-02T12:00:00Z", plano.get("finalizado_en").asString());
    assertEquals("50000000-0000-4000-8000-000000000001", plano.get("correlation_id").asString());
    assertNotNull(enviadoA("prestamos.finalizado.v1").get("payload"), "el evento estándar no cambia");
    assertEquals(NOW, e.getPublishedAt());
  }

  @Test void unCorrelationIdDeTextoSeConvierteEnUnUuidEstable() {
    brokerResponde(false);
    publicar(finalizado("req-123"));
    String uuid = enviadoA(REPUTACION).get("correlation_id").asString();
    assertEquals(UUID.nameUUIDFromBytes("req-123".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), uuid);
  }

  @Test void siReputacionNoEscuchaElPrestamoFinalizadoIgualSePublica() {
    doAnswer(inv -> {
      CorrelationData cd = inv.getArgument(4);
      if (REPUTACION.equals(inv.getArgument(1))) cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "lendup.domain.events", REPUTACION));
      cd.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbit).convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    OutboxEventEntity e = finalizado("c");
    publicar(e);
    assertEquals(NOW, e.getPublishedAt());
  }

  @Test void laCompatibilidadSePuedeDesactivar() {
    brokerResponde(false);
    publicar(finalizado("c"), "");
    verify(rabbit, times(1)).convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
  }

  @Test void sinSuscriptoresElEventoNuncaSeAgota() {
    brokerResponde(true);
    OutboxEventEntity e = evento("PrestamoCreado");
    for (int i = 0; i < 30; i++) publicar(e);
    assertEquals(9, e.getAttempts(), "queda por debajo del máximo: sale en cuanto alguien se suscriba");
    assertFalse(e.getNextAttemptAt().isAfter(NOW.plusSeconds(300)));
  }

  @Test void unErrorDelBrokerSiAgotaLosIntentos() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("broker caido"))).when(rabbit)
        .convertAndSend(anyString(), anyString(), any(byte[].class), any(MessagePostProcessor.class), any(CorrelationData.class));
    OutboxEventEntity e = evento("PrestamoCreado");
    for (int i = 0; i < 10; i++) publicar(e);
    assertEquals(10, e.getAttempts());
  }

  @Test void unTipoDesconocidoNoSePublica() {
    OutboxEventEntity e = evento("Desconocido");
    publicar(e);
    assertNull(e.getPublishedAt()); assertEquals(1, e.getAttempts()); verifyNoInteractions(rabbit);
  }
}
