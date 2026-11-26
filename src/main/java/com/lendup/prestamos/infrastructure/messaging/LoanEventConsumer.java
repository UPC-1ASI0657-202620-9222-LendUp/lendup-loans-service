package com.lendup.prestamos.infrastructure.messaging;

import com.lendup.prestamos.application.port.in.LoanEventUseCases;
import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.config.EventRoutingProperties;
import com.lendup.prestamos.config.RabbitConfiguration;
import com.lendup.prestamos.infrastructure.persistence.entity.ProcessedEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Préstamo Event Consumer. Inbox: el event_id se registra en processed_events en la misma transacción que el cambio
 * del préstamo, así un mensaje repetido no produce dos veces el mismo efecto. Los errores se reintentan y luego van
 * a la DLQ.
 */
@Component
public class LoanEventConsumer {
  @org.springframework.beans.factory.annotation.Value("${lendup.guarantees.deferred:false}")
  private boolean deferredGuarantees;
  private final JsonMapper json;
  private final ProcessedEventJpaRepository processed;
  private final LoanEventUseCases loans;
  private final EventRoutingProperties routes;
  private final Clock clock;

  public LoanEventConsumer(JsonMapper json, ProcessedEventJpaRepository processed, LoanEventUseCases loans, EventRoutingProperties routes, Clock clock) {
    this.json = json; this.processed = processed; this.loans = loans; this.routes = routes; this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfiguration.QUEUE)
  @Transactional
  public void consume(Message message) { handle(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()); }

  @Transactional
  public void handle(String routingKey, byte[] body) {
    Map<String, Object> event = json.readValue(body, new TypeReference<Map<String, Object>>() {});
    UUID eventId = uuid(event, "event_id");
    if (processed.existsById(eventId)) return;
    if (!(event.get("payload") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Evento sin payload");
    Map<String, Object> payload = new LinkedHashMap<>();
    raw.forEach((k, v) -> payload.put(String.valueOf(k), v));
    dispatch(routingKey, payload, event);
    long version = event.get("aggregate_version") instanceof Number n ? n.longValue() : 0L;
    UUID aggregateId = event.get("aggregate_id") == null ? new UUID(0, 0) : UUID.fromString(event.get("aggregate_id").toString());
    processed.save(new ProcessedEventEntity(eventId, RabbitConfiguration.QUEUE, aggregateId, version, clock.instant()));
  }

  private void dispatch(String routingKey, Map<String, Object> p, Map<String, Object> event) {
    String correlationId = Objects.toString(event.getOrDefault("correlation_id", ""), "");
    if (RabbitConfiguration.RESERVATION_CONFIRMED.equals(routingKey)) {
      loans.crearDesdeReserva(new ReservaConfirmadaCommand(uuid(p, "reserva_id"), uuid(p, "publicacion_id"), uuid(p, "prestamista_usuario_id"),
          uuid(p, "prestatario_usuario_id"), LocalDateTime.parse(text(p, "desde")), LocalDateTime.parse(text(p, "hasta")), new BigDecimal(text(p, "tarifa_diaria_acordada")),
          p.get("garantia_monetaria_acordada") == null ? null : new BigDecimal(p.get("garantia_monetaria_acordada").toString()), text(p, "moneda_acordada")), correlationId);
    } else if (RabbitConfiguration.CANCELLATION_REQUESTED.equals(routingKey)) {
      loans.decidirCancelacion(uuid(p, "reserva_id"), correlationId);
    } else if (RabbitConfiguration.RESERVATION_CANCELLED.equals(routingKey)) {
      loans.registrarReservaCancelada(uuid(p, "reserva_id"), correlationId);
    } else if (RabbitConfiguration.PAYMENT_CONFIRMED.equals(routingKey)) {
      pagoConfirmado(p, instant(p, "confirmada_en", event), correlationId);
    } else if (RabbitConfiguration.PAYMENT_REJECTED.equals(routingKey)) {
      loans.registrarPagoRechazado(uuid(p, "prestamo_id"), Objects.toString(p.get("tipo"), "PAGO"), correlationId);
    } else if (RabbitConfiguration.HOLD_CREATED.equals(routingKey)) {
      loans.registrarResultadoRetencion(uuid(p, "prestamo_id"), uuid(p, "cambio_fecha_prestamo_id"), "RETENIDA", text(p, "expira_en"), correlationId);
    } else if (RabbitConfiguration.HOLD_CONFIRMED.equals(routingKey)) {
      loans.registrarResultadoRetencion(uuid(p, "prestamo_id"), uuid(p, "cambio_fecha_prestamo_id"), "CONFIRMADA", "", correlationId);
    } else if (RabbitConfiguration.HOLD_REJECTED.equals(routingKey)) {
      loans.registrarResultadoRetencion(uuid(p, "prestamo_id"), uuid(p, "cambio_fecha_prestamo_id"), "RECHAZADA", text(p, "motivo"), correlationId);
    } else if (RabbitConfiguration.GUARANTEE_CONSTITUTED.equals(routingKey)) {
      loans.registrarGarantiaConstituida(uuid(p, "prestamo_id"), instant(p, "constituida_en", event), correlationId);
    } else if (routes.incidentReportedRoutingKey().equals(routingKey)) {
      if (deferredGuarantees) loans.proyectarIncidencia(uuid(p, "prestamo_id"), uuid(p, "incidencia_id"), false, correlationId);
      else loans.registrarIncidenciaReportada(uuid(p, "prestamo_id"), correlationId);
    } else if (routes.incidentResolvedRoutingKey().equals(routingKey)) {
      if (deferredGuarantees) loans.proyectarIncidencia(uuid(p, "prestamo_id"), uuid(p, "incidencia_id"), true, correlationId);
      else loans.registrarIncidenciaResuelta(uuid(p, "prestamo_id"), correlationId);
    } else {
      throw new IllegalArgumentException("Routing key no soportada: " + routingKey);
    }
  }

  private void pagoConfirmado(Map<String, Object> p, Instant pagadoEn, String correlationId) {
    switch (text(p, "tipo")) {
      case "PAGO_TARIFA" -> loans.registrarTarifaPagada(uuid(p, "prestamo_id"), pagadoEn, correlationId);
      case "PAGO_EXTENSION" -> loans.registrarExtensionPagada(new ExtensionPagadaCommand(uuid(p, "prestamo_id"), uuid(p, "cambio_fecha_prestamo_id"),
          p.get("transaccion_id") == null ? null : uuid(p, "transaccion_id"), pagadoEn), correlationId);
      default -> { /* Otros cobros (por ejemplo, la garantía) llegan con su propio evento. */ }
    }
  }

  private static Instant instant(Map<String, Object> payload, String key, Map<String, Object> event) {
    Object value = payload.get(key) != null ? payload.get(key) : event.get("occurred_at");
    if (value == null) throw new IllegalArgumentException("Evento sin " + key);
    return Instant.parse(value.toString());
  }

  private static String text(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("Evento sin " + k);
    return v.toString();
  }

  private static UUID uuid(Map<String, Object> m, String k) { return UUID.fromString(text(m, k)); }
}
