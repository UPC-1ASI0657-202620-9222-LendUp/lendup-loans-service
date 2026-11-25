package com.lendup.prestamos.infrastructure.messaging;

import com.lendup.prestamos.config.EventRoutingProperties;
import com.lendup.prestamos.config.RabbitConfiguration;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox Event Publisher (Fig. 16). Publica con publisher confirms y mandatory=true (igual que Reservas y Pagos): un
 * evento con consumidores conocidos solo se marca publicado si el broker lo confirma Y lo entregó al menos a una
 * cola. Si no, queda pendiente y se reintenta con espera creciente, sin perderse: mientras nadie esté suscrito no se
 * agotan los intentos, así que sale en cuanto el consumidor declara su cola.
 */
@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private final OutboxJpaRepository repository;
  private final RabbitTemplate rabbit;
  private final JsonMapper json;
  private final Clock clock;
  private final EventRoutingProperties routes;
  private final int maxAttempts;

  public OutboxRelay(OutboxJpaRepository repository, RabbitTemplate rabbit, JsonMapper json, Clock clock, EventRoutingProperties routes,
                     @Value("${lendup.outbox.max-attempts:10}") int maxAttempts) {
    this.repository = repository; this.rabbit = rabbit; this.json = json; this.clock = clock; this.routes = routes; this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${lendup.outbox.relay-delay-ms:1000}")
  @Transactional
  public void relay() {
    for (OutboxEventEntity e : repository.pending(clock.instant(), maxAttempts, PageRequest.of(0, 50))) {
      try {
        publish(e);
        e.published(clock.instant());
      } catch (SinSuscriptoresException ex) {
        e.waiting(ex.getMessage(), proximoIntento(e), maxAttempts);
        log.warn("outbox_event_waiting_for_subscriber event_id={} event_type={} attempts={} error={}", e.getId(), e.getEventType(), e.getAttempts(), e.getLastError());
      } catch (Exception ex) {
        e.failed(ex.getMessage(), proximoIntento(e));
        if (e.getAttempts() >= maxAttempts) log.error("outbox_event_exhausted event_id={} event_type={} attempts={} error={}", e.getId(), e.getEventType(), e.getAttempts(), e.getLastError());
        else log.warn("outbox_publish_failed event_id={} event_type={} attempts={} error={}", e.getId(), e.getEventType(), e.getAttempts(), e.getLastError());
      }
      repository.save(e);
    }
  }

  private Instant proximoIntento(OutboxEventEntity e) { return clock.instant().plusSeconds(Math.min(300, 1L << Math.min(e.getAttempts() + 1, 8))); }

  private void publish(OutboxEventEntity e) throws Exception {
    EventRoute route = EventRoute.of(e.getEventType());
    @SuppressWarnings("unchecked") Map<String, Object> payload = json.readValue(e.getPayload(), Map.class);
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("event_id", e.getId());
    envelope.put("event_type", e.getEventType());
    envelope.put("event_version", e.getEventVersion());
    envelope.put("aggregate_version", e.getAggregateVersion());
    envelope.put("aggregate_id", e.getAggregateId());
    envelope.put("occurred_at", e.getOccurredAt());
    envelope.put("correlation_id", e.getCorrelationId());
    envelope.put("payload", payload);
    boolean entregado = enviar(e, route.routingKey(), envelope);
    if (!entregado && route.subscriberRequired()) throw new SinSuscriptoresException(route.routingKey());
    if (!entregado) log.info("outbox_event_without_subscribers event_id={} routing_key={}", e.getId(), route.routingKey());
    if (ReputationEventTranslator.aplica(e.getEventType()) && routes.reputationEnabled()) {
      enviar(e, routes.reputationLoanFinishedRoutingKey(), ReputationEventTranslator.traducir(e, payload));
    }
  }

  /** Devuelve false si el broker confirmó el mensaje pero ninguna cola lo recibió. */
  private boolean enviar(OutboxEventEntity e, String routingKey, Map<String, Object> body) throws Exception {
    CorrelationData correlation = new CorrelationData(e.getId().toString());
    rabbit.convertAndSend(RabbitConfiguration.EVENTS_EXCHANGE, routingKey, json.writeValueAsBytes(body), message -> {
      message.getMessageProperties().setContentType("application/json");
      message.getMessageProperties().setMessageId(e.getId().toString());
      message.getMessageProperties().setHeader("x-event-type", e.getEventType());
      return message;
    }, correlation);
    CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
    if (!confirm.ack()) throw new IllegalStateException("NACK del broker: " + confirm.reason());
    return correlation.getReturned() == null;
  }

  private static final class SinSuscriptoresException extends RuntimeException {
    SinSuscriptoresException(String routingKey) { super("Ninguna cola suscrita a " + routingKey + " (el broker devolvió el evento)"); }
  }
}
