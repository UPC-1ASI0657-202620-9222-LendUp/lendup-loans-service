package com.lendup.prestamos.infrastructure.persistence;

import com.lendup.prestamos.application.port.out.OutboxPort;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.OutboxJpaRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxJpaAdapter implements OutboxPort {
  private final OutboxJpaRepository repository;
  private final JsonMapper json;

  public OutboxJpaAdapter(OutboxJpaRepository repository, JsonMapper json) { this.repository = repository; this.json = json; }

  @Override
  public void append(EventEnvelope e) {
    repository.save(new OutboxEventEntity(e.eventId(), "PRESTAMO", e.aggregateId(), e.eventType(), e.eventVersion(), e.aggregateVersion(),
        json.writeValueAsString(e.payload()), e.correlationId(), e.occurredAt()));
  }
}
