package com.lendup.prestamos.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.port.in.LoanViews.ExtensionPagadaCommand;
import com.lendup.prestamos.domain.model.*;
import com.lendup.prestamos.support.LoansTestFixture;
import io.cucumber.java.es.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.function.Executable;

/** Pasos en español sobre los servicios reales con persistencia en memoria y reloj controlado. */
public class LoansSteps {
  private final LoansTestFixture f = new LoansTestFixture();
  private UUID prestamo;
  private UUID cambio;
  private LocalDateTime vigenteInicial = LoansTestFixture.HASTA;
  private RuntimeException error;
  private int operaciones;

  private void intentar(Executable accion) {
    error = null;
    try { accion.execute(); }
    catch (RuntimeException e) { error = e; }
    catch (Throwable t) { throw new AssertionError(t); }
  }

  @Dado("un préstamo reservado con garantía de {word}") public void reservado(String garantia) { prestamo = f.reservado(garantia); }
  @Dado("un préstamo reservado sin garantía") public void reservadoSinGarantia() { prestamo = f.reservado(null); }
  @Dado("un préstamo listo para entregar") public void listo() { prestamo = f.listoParaEntregar(); f.clock.setLima(LoansTestFixture.DESDE); }
  @Dado("un préstamo con la entrega registrada") public void entregado() { prestamo = f.entregado(); }
  @Dado("un préstamo activo") public void activo() { prestamo = f.activo(); }
  @Dado("un préstamo vencido") public void vencido() { prestamo = f.vencido(); }
  @Dado("un préstamo con la devolución registrada") public void devuelto() { prestamo = f.devuelto(); }
  @Dado("Pagos confirma la tarifa") public void tarifa() { f.service.registrarTarifaPagada(prestamo, f.clock.instant(), "bdd"); }
  @Dado("Pagos confirma la garantía") public void garantia() { f.service.registrarGarantiaConstituida(prestamo, f.clock.instant(), "bdd"); }
  @Dado("Evidencias reporta una incidencia") public void incidencia() { f.service.registrarIncidenciaReportada(prestamo, "bdd"); }
  @Dado("Evidencias resuelve la incidencia") public void incidenciaResuelta() { f.service.registrarIncidenciaResuelta(prestamo, "bdd"); }
  @Dado("otra reserva confirmada del mismo objeto {int} horas después") public void otraReserva(int horas) {
    f.service.crearDesdeReserva(f.reserva(UUID.randomUUID(), LoansTestFixture.HASTA.plusHours(horas), LoansTestFixture.HASTA.plusDays(3), null), "bdd");
  }
  @Dado("una extensión de {int} horas aceptada y pagada") public void extensionPagada(int horas) { solicitarExtension(horas); aceptarExtension(); pagarExtension(); }

  @Cuando("el prestamista registra la entrega") public void entrega() { intentar(() -> f.service.registrarEntrega(f.prestamista, prestamo, "bdd")); }
  @Cuando("el prestatario registra la entrega") public void entregaPorPrestatario() { intentar(() -> f.service.registrarEntrega(f.prestatario, prestamo, "bdd")); }
  @Cuando("el prestatario confirma la recepción") public void recepcion() { intentar(() -> f.service.confirmarRecepcion(f.prestatario, prestamo, "bdd")); }
  @Cuando("el prestatario registra la devolución") public void devolucion() { intentar(() -> f.service.registrarDevolucion(f.prestatario, prestamo, "bdd")); }
  @Cuando("el prestamista confirma la devolución") public void confirmaDevolucion() { intentar(() -> f.service.confirmarDevolucion(f.prestamista, prestamo, "bdd")); }
  @Cuando("llega la fecha vigente de devolución") public void llegaVigente() { f.clock.setLima(f.prestamo(prestamo).devolucionVigenteEn()); f.service.marcarVencidos(10); }
  @Cuando("llega la fecha original de devolución") public void llegaOriginal() { f.clock.setLima(LoansTestFixture.HASTA); f.service.marcarVencidos(10); }
  @Cuando("un estudiante ajeno consulta el préstamo") public void consultaAjena() { intentar(() -> f.queries.detalle(f.intruso, prestamo, "")); }
  @Cuando("el prestatario consulta su calendario") public void calendario() { operaciones = f.queries.calendario(f.prestatario).size(); }
  @Cuando("un estudiante ajeno consulta su calendario") public void calendarioAjeno() { operaciones = f.queries.calendario(f.intruso).size(); }
  @Cuando("el prestatario solicita extender {int} horas") public void solicitarExtension(int horas) {
    intentar(() -> cambio = f.service.solicitarExtension(f.prestatario, prestamo, vigenteInicial.plusHours(horas), null, "bdd").cambio().id());
  }
  @Cuando("el prestamista acepta la extensión") public void aceptarExtension() { intentar(() -> f.service.responderExtension(f.prestamista, prestamo, cambio, "ACEPTADA_PENDIENTE_PAGO", null, "bdd")); }
  @Cuando("el prestamista rechaza la extensión") public void rechazarExtension() { intentar(() -> f.service.responderExtension(f.prestamista, prestamo, cambio, "RECHAZADA", "No puedo", "bdd")); }
  @Cuando("Pagos confirma la extensión") public void pagarExtension() { f.service.registrarExtensionPagada(new ExtensionPagadaCommand(prestamo, cambio, UUID.randomUUID(), f.clock.instant()), "bdd"); }
  @Cuando("el prestamista propone devolver {int} horas después") public void reprogramar(int horas) {
    intentar(() -> cambio = f.service.proponerReprogramacion(f.prestamista, prestamo, vigenteInicial.plusHours(horas), "bdd").cambio().id());
  }
  @Cuando("el prestatario acepta la reprogramación") public void aceptarReprogramacion() { intentar(() -> f.service.responderReprogramacion(f.prestatario, prestamo, cambio, "APLICADA", null, "bdd")); }
  @Cuando("el prestatario rechaza la reprogramación") public void rechazarReprogramacion() { intentar(() -> f.service.responderReprogramacion(f.prestatario, prestamo, cambio, "RECHAZADA", null, "bdd")); }
  @Cuando("Reservas solicita cancelar la reserva") public void cancelar() { f.service.decidirCancelacion(f.prestamo(prestamo).reservaId(), "bdd"); }

  @Entonces("la operación se rechaza con {string}") public void rechazo(String mensaje) { assertNotNull(error, "La operación no se rechazó"); assertEquals(mensaje, error.getMessage()); }
  @Entonces("el préstamo queda en estado {string}") public void estado(String esperado) {
    if (error != null && f.estado(prestamo) != EstadoPrestamo.valueOf(esperado)) throw error;
    assertEquals(EstadoPrestamo.valueOf(esperado), f.estado(prestamo));
  }
  @Entonces("se publica el evento {string}") public void evento(String tipo) { assertTrue(f.tiposDeEvento().contains(tipo), () -> f.tiposDeEvento().toString()); }
  @Entonces("no se publica el evento {string}") public void sinEvento(String tipo) { assertFalse(f.tiposDeEvento().contains(tipo)); }
  @Entonces("la extensión queda en estado {string} con un costo de {word}") public void extension(String estado, String costo) {
    CambioFecha c = f.prestamo(prestamo).buscarCambio(cambio).orElseThrow();
    assertEquals(EstadoCambioFecha.valueOf(estado), c.estado());
    assertEquals(new BigDecimal(costo), c.costoAdicional());
  }
  @Entonces("la reprogramación queda en estado {string} sin costo") public void reprogramacion(String estado) {
    CambioFecha c = f.prestamo(prestamo).buscarCambio(cambio).orElseThrow();
    assertEquals(EstadoCambioFecha.valueOf(estado), c.estado());
    assertNull(c.costoAdicional());
  }
  @Entonces("la fecha vigente de devolución no cambia") public void fechaIgual() { assertEquals(vigenteInicial, f.prestamo(prestamo).devolucionVigenteEn()); }
  @Entonces("la fecha vigente de devolución avanza {int} horas") public void fechaAvanza(int horas) {
    assertEquals(vigenteInicial.plusHours(horas), f.prestamo(prestamo).devolucionVigenteEn());
    assertEquals(vigenteInicial, f.prestamo(prestamo).datos().devolucionOriginalEn(), "la original se conserva");
  }
  @Entonces("el historial tiene {int} cambio de fecha") public void historial(int total) { assertEquals(total, f.queries.cambiosDeFecha(f.prestatario, prestamo).size()); }
  @Entonces("ve {int} operación/operaciones programada/programadas") public void operaciones(int total) { assertEquals(total, operaciones); }
}
