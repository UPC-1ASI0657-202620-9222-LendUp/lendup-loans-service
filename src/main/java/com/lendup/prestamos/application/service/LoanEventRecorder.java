package com.lendup.prestamos.application.service;

import com.lendup.prestamos.application.port.out.AuditPort;
import com.lendup.prestamos.application.port.out.OutboxPort;
import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.domain.model.Prestamo;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cierra un cambio del préstamo en una sola transacción (RNF24): guarda el agregado, escribe la auditoría (RNF21) y
 * agrega sus eventos al outbox (Outbox Event Publisher, Fig. 16).
 */
@Component
public class LoanEventRecorder {
  private static final String AGGREGATE = "PRESTAMO";
  private final PrestamoRepository repository;
  private final OutboxPort outbox;
  private final AuditPort audit;
  private final Clock clock;

  public LoanEventRecorder(PrestamoRepository repository, OutboxPort outbox, AuditPort audit, Clock clock) {
    this.repository = repository; this.outbox = outbox; this.audit = audit; this.clock = clock;
  }

  public void guardar(Prestamo prestamo, String accion, String estadoAnterior, UUID actor, String correlationId) {
    repository.save(prestamo);
    auditar(prestamo, accion, estadoAnterior, actor, correlationId);
    for (PrestamoEvento evento : prestamo.extraerEventos()) {
      outbox.append(new EventEnvelope(UUID.randomUUID(), evento.tipo(), 1, evento.aggregateVersion(), prestamo.id(), clock.instant(),
          correlationId == null ? "" : correlationId, evento.payload()));
    }
  }

  public void auditar(Prestamo prestamo, String accion, String estadoAnterior, UUID actor, String correlationId) {
    audit.record(AGGREGATE, prestamo.id(), accion, estadoAnterior, prestamo.estado().name(), actor, correlationId);
  }
}
