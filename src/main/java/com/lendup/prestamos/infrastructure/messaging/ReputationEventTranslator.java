package com.lendup.prestamos.infrastructure.messaging;

import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Capa anticorrupción hacia Reputación: su consumidor no lee el envelope estándar. Exige los campos en la raíz, rechaza
 * cualquier otro y pide correlation_id como UUID (lendup-reputation-service, contracts/fixtures/prestamo-finalizado.v1.json).
 */
final class ReputationEventTranslator {
  private ReputationEventTranslator() {}

  static boolean aplica(String eventType) { return PrestamoEvento.FINALIZADO.equals(eventType); }

  static Map<String, Object> traducir(OutboxEventEntity e, Map<String, Object> payload) {
    Map<String, Object> plano = new LinkedHashMap<>();
    plano.put("event_id", e.getId());
    plano.put("event_type", e.getEventType());
    plano.put("event_version", e.getEventVersion());
    for (String campo : List.of("prestamo_id", "prestamista_usuario_id", "prestatario_usuario_id", "finalizado_en")) plano.put(campo, payload.get(campo));
    plano.put("correlation_id", comoUuid(e.getCorrelationId()));
    plano.put("aggregate_version", e.getAggregateVersion());
    return plano;
  }

  private static UUID comoUuid(String correlationId) {
    try { return UUID.fromString(correlationId); }
    catch (IllegalArgumentException ex) { return UUID.nameUUIDFromBytes(correlationId.getBytes(StandardCharsets.UTF_8)); }
  }
}
