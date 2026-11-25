package com.lendup.prestamos.application;

import static com.lendup.prestamos.domain.model.EstadoPrestamo.*;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.domain.exception.*;
import com.lendup.prestamos.domain.model.Prestamo;
import com.lendup.prestamos.support.LoansTestFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Ciclo del préstamo: creación, entrega, recepción, vencimiento, devolución, cierre, incidencias y cancelación. */
class LoanLifecycleTest {
  private final LoansTestFixture f = new LoansTestFixture();

  @Test void unaReservaConfirmadaCreaUnPrestamoReservadoConSusCondiciones() {
    UUID id = f.reservado();
    Prestamo.Datos d = f.prestamo(id).datos();
    assertEquals(RESERVADO, d.estado());
    assertEquals(LoansTestFixture.HASTA, d.devolucionOriginalEn());
    assertEquals(LoansTestFixture.HASTA, d.devolucionVigenteEn());
    assertEquals(new BigDecimal("20.00"), d.tarifaDiariaAcordada());
    assertTrue(d.garantiaRequerida());
    EventEnvelope evento = f.ultimo(PrestamoEvento.CREADO);
    assertEquals(id, evento.payload().get("prestamo_id"));
    assertEquals(d.reservaId(), evento.payload().get("reserva_id"));
    assertEquals(f.prestatario, evento.payload().get("prestatario_usuario_id"));
    assertEquals(f.prestamista, evento.payload().get("prestamista_usuario_id"));
    assertEquals(1, evento.aggregateVersion());
  }

  @Test void laMismaReservaNoCreaDosPrestamos() {
    UUID reserva = UUID.randomUUID();
    var command = f.reserva(reserva, LoansTestFixture.DESDE, LoansTestFixture.HASTA, null);
    f.service.crearDesdeReserva(command, "a");
    f.service.crearDesdeReserva(command, "b");
    assertEquals(1, f.repository.todos().size());
    assertEquals(List.of(PrestamoEvento.CREADO), f.tiposDeEvento());
  }

  @Test void sinPagoOGarantiaNoSePuedeEntregar() {
    UUID id = f.reservado();
    assertEquals("Falta confirmar el pago o la garantía", assertThrows(ReglaDeNegocioException.class, () -> f.service.registrarEntrega(f.prestamista, id, "c")).getMessage());
    f.service.registrarTarifaPagada(id, f.clock.instant(), "c");
    assertThrows(ReglaDeNegocioException.class, () -> f.service.registrarEntrega(f.prestamista, id, "c"));
    f.service.registrarGarantiaConstituida(id, f.clock.instant(), "c");
    assertDoesNotThrow(() -> f.service.registrarEntrega(f.prestamista, id, "c"));
  }

  @Test void siLaReservaNoPideGarantiaBastaElPagoDeLaTarifa() {
    UUID id = f.reservado(null);
    assertFalse(f.prestamo(id).garantiaRequerida());
    f.service.registrarTarifaPagada(id, f.clock.instant(), "c");
    f.service.registrarEntrega(f.prestamista, id, "c");
    assertEquals(ENTREGA_REGISTRADA, f.estado(id));
  }

  @Test void entregaYRecepcionActivanElPrestamo() {
    UUID id = f.entregado();
    assertEquals(ENTREGA_REGISTRADA, f.estado(id));
    assertEquals(id, f.ultimo(PrestamoEvento.OBJETO_ENTREGADO).payload().get("prestamo_id"));
    f.service.confirmarRecepcion(f.prestatario, id, "c");
    Prestamo.Datos d = f.prestamo(id).datos();
    assertEquals(ACTIVO, d.estado());
    assertEquals(d.recepcionConfirmadaEn(), d.activadoEn());
    assertEquals(f.prestatario, f.ultimo(PrestamoEvento.ACTIVADO).payload().get("prestatario_usuario_id"));
  }

  @Test void cadaOperacionExigeElRolContextualCorrecto() {
    UUID listo = f.listoParaEntregar();
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.registrarEntrega(f.prestatario, listo, "c"));
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.registrarEntrega(f.intruso, listo, "c"));
    UUID entregado = f.entregado();
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.confirmarRecepcion(f.prestamista, entregado, "c"));
    f.service.confirmarRecepcion(f.prestatario, entregado, "c");
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.registrarDevolucion(f.prestamista, entregado, "c"));
    f.service.registrarDevolucion(f.prestatario, entregado, "c");
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.confirmarDevolucion(f.prestatario, entregado, "c"));
  }

  @Test void unPrestamoInexistenteDa404() {
    assertThrows(NotFoundException.class, () -> f.service.registrarEntrega(f.prestamista, UUID.randomUUID(), "c"));
  }

  @Test void venceSoloCuandoLlegaLaFechaVigenteEnHoraDeLima() {
    UUID id = f.activo();
    f.clock.setLima(LoansTestFixture.HASTA.minusSeconds(1));
    assertEquals(0, f.service.marcarVencidos(10));
    assertEquals(ACTIVO, f.estado(id));
    f.clock.setLima(LoansTestFixture.HASTA);
    assertEquals(1, f.service.marcarVencidos(10));
    assertEquals(VENCIDO, f.estado(id));
    assertEquals(LoansTestFixture.HASTA, f.ultimo(PrestamoEvento.VENCIDO).payload().get("devolucion_vigente_en"));
    assertEquals(0, f.service.marcarVencidos(10), "no se vuelve a marcar");
  }

  @Test void unPrestamoVencidoSePuedeDevolver() {
    UUID id = f.vencido();
    f.service.registrarDevolucion(f.prestatario, id, "c");
    assertEquals(DEVOLUCION_REGISTRADA, f.estado(id));
    f.service.confirmarDevolucion(f.prestamista, id, "c");
    assertEquals(FINALIZADO, f.estado(id));
  }

  @Test void laDevolucionConfirmadaFinalizaYLiberaElPeriodoSiFueAnticipada() {
    UUID id = f.finalizado();
    assertEquals(FINALIZADO, f.estado(id));
    assertNotNull(f.ultimo(PrestamoEvento.FINALIZADO).payload().get("finalizado_en"));
    EventEnvelope liberado = f.ultimo(PrestamoEvento.PERIODO_LIBERADO);
    assertEquals(LoansTestFixture.HASTA.minusHours(1), liberado.payload().get("nuevo_hasta"));
    assertEquals("LIBERACION_CONFIRMADA", liberado.payload().get("decision"));
    assertEquals(true, liberado.payload().get("devolucion_confirmada_por_prestamista"));
    assertEquals(false, liberado.payload().get("incidencias_pendientes"));
  }

  @Test void unaDevolucionFueraDeFechaNoLiberaPeriodo() {
    UUID id = f.vencido();
    f.service.registrarDevolucion(f.prestatario, id, "c");
    f.service.confirmarDevolucion(f.prestamista, id, "c");
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.PERIODO_LIBERADO));
  }

  @Test void noSeFinalizaConIncidenciasPendientes() {
    UUID id = f.devuelto();
    f.service.registrarIncidenciaReportada(id, "c");
    assertEquals(PENDIENTE_INCIDENCIA, f.estado(id));
    assertThrows(ReglaDeNegocioException.class, () -> f.service.confirmarDevolucion(f.prestamista, id, "c"));
    f.service.registrarIncidenciaReportada(id, "c");
    f.service.registrarIncidenciaResuelta(id, "c");
    assertEquals(PENDIENTE_INCIDENCIA, f.estado(id), "queda una incidencia");
    f.service.registrarIncidenciaResuelta(id, "c");
    assertEquals(DEVOLUCION_REGISTRADA, f.estado(id));
    f.service.confirmarDevolucion(f.prestamista, id, "c");
    assertEquals(FINALIZADO, f.estado(id));
  }

  @Test void unaIncidenciaDuranteElPrestamoSeArrastraALaDevolucion() {
    UUID id = f.activo();
    f.service.registrarIncidenciaReportada(id, "c");
    assertEquals(ACTIVO, f.estado(id));
    f.service.registrarDevolucion(f.prestatario, id, "c");
    assertEquals(PENDIENTE_INCIDENCIA, f.estado(id));
    f.service.registrarIncidenciaResuelta(id, "c");
    f.service.registrarIncidenciaResuelta(id, "c");
    assertEquals(0, f.prestamo(id).datos().incidenciasPendientes(), "nunca queda en negativo");
    assertEquals(DEVOLUCION_REGISTRADA, f.estado(id));
  }

  @Test void laCancelacionSeAceptaSiElObjetoNoSeHaEntregado() {
    UUID id = f.listoParaEntregar();
    UUID reserva = f.prestamo(id).reservaId();
    f.service.decidirCancelacion(reserva, "c");
    assertEquals(CANCELADO, f.estado(id));
    EventEnvelope e = f.ultimo(PrestamoEvento.CANCELADO);
    assertEquals(reserva, e.payload().get("reserva_id"));
    assertTrue(e.aggregateVersion() > 1);
    f.service.registrarReservaCancelada(reserva, "c");
    assertEquals(1, f.tiposDeEvento().stream().filter(PrestamoEvento.CANCELADO::equals).count(), "ReservaCancelada posterior no repite el evento");
  }

  @Test void laCancelacionSeRechazaSiElObjetoYaSeEntrego() {
    UUID id = f.entregado();
    UUID reserva = f.prestamo(id).reservaId();
    f.service.decidirCancelacion(reserva, "c");
    assertEquals(ENTREGA_REGISTRADA, f.estado(id));
    EventEnvelope primero = f.ultimo(PrestamoEvento.CANCELACION_RECHAZADA);
    f.service.decidirCancelacion(reserva, "c");
    EventEnvelope segundo = f.ultimo(PrestamoEvento.CANCELACION_RECHAZADA);
    assertEquals(reserva, primero.payload().get("reserva_id"));
    assertTrue(segundo.aggregateVersion() > primero.aggregateVersion(), "Reservas descarta versiones no crecientes");
  }

  @Test void repetirElPedidoDeCancelacionVuelveAInformarLaCancelacion() {
    UUID id = f.reservado();
    UUID reserva = f.prestamo(id).reservaId();
    f.service.decidirCancelacion(reserva, "c");
    f.service.decidirCancelacion(reserva, "c");
    assertEquals(2, f.tiposDeEvento().stream().filter(PrestamoEvento.CANCELADO::equals).count());
    assertEquals(CANCELADO, f.estado(id));
  }

  @Test void unaReservaCanceladaFueraDeLaSagaCancelaElPrestamoSinIniciar() {
    UUID sinIniciar = f.reservado();
    f.service.registrarReservaCancelada(f.prestamo(sinIniciar).reservaId(), "c");
    assertEquals(CANCELADO, f.estado(sinIniciar));
    UUID activo = f.activo();
    f.service.registrarReservaCancelada(f.prestamo(activo).reservaId(), "c");
    assertEquals(ACTIVO, f.estado(activo));
  }

  @Test void unPagoRepetidoNoCambiaLaFechaDelPrimero() {
    UUID id = f.reservado();
    var primero = f.clock.instant();
    f.service.registrarTarifaPagada(id, primero, "c");
    f.service.registrarTarifaPagada(id, primero.plusSeconds(60), "c");
    assertEquals(primero, f.prestamo(id).datos().pagoTarifaConfirmadoEn());
  }

  @Test void unPagoQueLlegaDespuesDeCancelarNoHabilitaLaEntrega() {
    UUID id = f.reservado();
    f.service.decidirCancelacion(f.prestamo(id).reservaId(), "c");
    f.service.registrarTarifaPagada(id, f.clock.instant(), "c");
    assertNull(f.prestamo(id).datos().pagoTarifaConfirmadoEn());
  }

  @Test void unPagoRechazadoNoCambiaElPrestamoYQuedaAuditado() {
    UUID id = f.reservado();
    long version = f.prestamo(id).version();
    f.service.registrarPagoRechazado(id, "PAGO_TARIFA", "c");
    assertEquals(version, f.prestamo(id).version());
    assertTrue(f.auditoria.stream().anyMatch(a -> a.accion().equals("PAGO_RECHAZADO_PAGO_TARIFA")));
  }

  @Test void cadaOperacionCriticaQuedaAuditadaConActorYEstados() {
    f.finalizado();
    List<String> acciones = f.auditoria.stream().map(LoansTestFixture.Auditoria::accion).toList();
    assertTrue(acciones.containsAll(List.of("CREAR_DESDE_RESERVA", "REGISTRAR_TARIFA_PAGADA", "REGISTRAR_GARANTIA_CONSTITUIDA", "REGISTRAR_ENTREGA", "CONFIRMAR_RECEPCION",
        "REGISTRAR_DEVOLUCION", "CONFIRMAR_DEVOLUCION")), acciones::toString);
    var entrega = f.auditoria.stream().filter(a -> a.accion().equals("REGISTRAR_ENTREGA")).findFirst().orElseThrow();
    assertEquals(new LoansTestFixture.Auditoria("REGISTRAR_ENTREGA", "RESERVADO", "ENTREGA_REGISTRADA", f.prestamista), entrega);
  }

  @Test void lasVersionesDeLosEventosCrecenSinRepetirseEnElCicloNormal() {
    f.finalizado();
    long anterior = 0;
    for (EventEnvelope e : f.eventos) {
      assertTrue(e.aggregateVersion() >= anterior);
      anterior = e.aggregateVersion();
    }
    assertEquals(List.of(PrestamoEvento.CREADO, PrestamoEvento.OBJETO_ENTREGADO, PrestamoEvento.ACTIVADO, PrestamoEvento.DEVOLUCION_REGISTRADA, PrestamoEvento.FINALIZADO,
        PrestamoEvento.PERIODO_LIBERADO), f.tiposDeEvento());
  }

  @Test void unaReservaInvalidaNoCreaPrestamo() {
    LocalDateTime d = LoansTestFixture.DESDE;
    assertThrows(IllegalArgumentException.class, () -> f.service.crearDesdeReserva(f.reserva(UUID.randomUUID(), d, d, null), "c"));
    assertTrue(f.repository.todos().isEmpty());
  }
}
