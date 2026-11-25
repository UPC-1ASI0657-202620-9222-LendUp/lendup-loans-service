package com.lendup.prestamos.support;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.application.service.*;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.transaction.support.TransactionOperations;

/** Servicios reales con persistencia en memoria y reloj controlable (hora de Lima). */
public class LoansTestFixture {
  public static final class MutableClock extends Clock {
    private Instant now;
    MutableClock(LocalDateTime lima) { setLima(lima); }
    public void setLima(LocalDateTime lima) { now = lima.atZone(BusinessClock.ZONA).toInstant(); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  public record Auditoria(String accion, String anterior, String nuevo, UUID actor) {}

  public static final LocalDateTime DESDE = LocalDateTime.of(2026, 11, 10, 9, 0);
  public static final LocalDateTime HASTA = LocalDateTime.of(2026, 11, 12, 9, 0);

  public final UUID prestamista = UUID.randomUUID(), prestatario = UUID.randomUUID(), intruso = UUID.randomUUID();
  public final UUID publicacion = UUID.randomUUID();
  public final MutableClock clock = new MutableClock(DESDE.minusDays(2));
  public final InMemoryPrestamoRepository repository = new InMemoryPrestamoRepository();
  public final List<EventEnvelope> eventos = new ArrayList<>();
  public final List<Auditoria> auditoria = new ArrayList<>();
  public final BusinessClock businessClock = new BusinessClock(clock);
  public final LoanEventRecorder recorder = new LoanEventRecorder(repository, eventos::add,
      (tipo, id, accion, anterior, nuevo, actor, correlacion) -> auditoria.add(new Auditoria(accion, anterior, nuevo, actor)), clock);
  public final LoanApplicationService service = new LoanApplicationService(repository, recorder, PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS, businessClock,
      TransactionOperations.withoutTransaction());
  public final LoanQueryService queries = new LoanQueryService(repository, (id, token) -> List.of(), PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS);

  public ReservaConfirmadaCommand reserva(UUID reservaId, LocalDateTime desde, LocalDateTime hasta, String garantia) {
    return new ReservaConfirmadaCommand(reservaId, publicacion, prestamista, prestatario, desde, hasta, new BigDecimal("20.00"), garantia == null ? null : new BigDecimal(garantia), "PEN");
  }

  /** Crea el préstamo desde una reserva confirmada (garantía de 50.00) y devuelve su id. */
  public UUID reservado() { return reservado("50.00"); }

  public UUID reservado(String garantia) {
    UUID reservaId = UUID.randomUUID();
    service.crearDesdeReserva(reserva(reservaId, DESDE, HASTA, garantia), "test");
    return repository.findByReserva(reservaId).orElseThrow().id();
  }

  public UUID listoParaEntregar() {
    UUID id = reservado();
    service.registrarTarifaPagada(id, clock.instant(), "test");
    service.registrarGarantiaConstituida(id, clock.instant(), "test");
    return id;
  }

  public UUID entregado() {
    UUID id = listoParaEntregar();
    clock.setLima(DESDE);
    service.registrarEntrega(prestamista, id, "test");
    return id;
  }

  public UUID activo() {
    UUID id = entregado();
    service.confirmarRecepcion(prestatario, id, "test");
    return id;
  }

  public UUID vencido() {
    UUID id = activo();
    clock.setLima(HASTA.plusMinutes(1));
    service.marcarVencidos(10);
    return id;
  }

  public UUID devuelto() {
    UUID id = activo();
    clock.setLima(HASTA.minusHours(1));
    service.registrarDevolucion(prestatario, id, "test");
    return id;
  }

  public UUID finalizado() {
    UUID id = devuelto();
    service.confirmarDevolucion(prestamista, id, "test");
    return id;
  }

  public Prestamo prestamo(UUID id) { return repository.findById(id).orElseThrow(); }
  public EstadoPrestamo estado(UUID id) { return prestamo(id).estado(); }
  public List<String> tiposDeEvento() { return eventos.stream().map(EventEnvelope::eventType).toList(); }
  public EventEnvelope ultimo(String tipo) { return eventos.stream().filter(e -> e.eventType().equals(tipo)).reduce((a, b) -> b).orElseThrow(() -> new AssertionError("No se publicó " + tipo)); }
}
