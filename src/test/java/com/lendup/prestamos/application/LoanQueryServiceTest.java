package com.lendup.prestamos.application;

import static com.lendup.prestamos.support.LoansTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.application.port.in.LoanViews.PrestamoView;
import com.lendup.prestamos.application.service.LoanQueryService;
import com.lendup.prestamos.domain.exception.OperacionNoPermitidaException;
import com.lendup.prestamos.domain.model.*;
import com.lendup.prestamos.support.LoansTestFixture;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class LoanQueryServiceTest {
  private final LoansTestFixture f = new LoansTestFixture();

  @Test void cadaParticipanteVeSusPrestamosYUnIntrusoNinguno() {
    UUID id = f.activo();
    assertEquals(List.of(id), f.queries.listar(f.prestamista, null).stream().map(v -> v.prestamo().id()).toList());
    assertEquals(List.of(id), f.queries.listar(f.prestatario, "ACTIVO").stream().map(v -> v.prestamo().id()).toList());
    assertTrue(f.queries.listar(f.prestatario, "FINALIZADO").isEmpty());
    assertTrue(f.queries.listar(f.intruso, null).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> f.queries.listar(f.prestatario, "OTRO"));
  }

  @Test void elFiltroDeDevolucionRegistradaIncluyeLosPendientesDeIncidencia() {
    UUID id = f.devuelto();
    f.service.registrarIncidenciaReportada(id, "c");
    assertEquals(1, f.queries.listar(f.prestamista, "DEVOLUCION_REGISTRADA").size());
    assertEquals(1, f.queries.listar(f.prestamista, "PENDIENTE_INCIDENCIA").size());
  }

  @Test void elDetalleEsSoloParaParticipantesEIncluyeEvidencias() {
    UUID id = f.activo();
    var queries = new LoanQueryService(f.repository, (prestamo, token) -> List.of(Map.of("id", "e1", "token", token)), PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS);
    PrestamoView v = queries.detalle(f.prestatario, id, "tkn");
    assertEquals("tkn", v.evidencias().getFirst().get("token"));
    assertThrows(OperacionNoPermitidaException.class, () -> queries.detalle(f.intruso, id, "tkn"));
    assertThrows(NotFoundException.class, () -> queries.detalle(f.prestatario, UUID.randomUUID(), "tkn"));
  }

  @Test void elCalendarioMuestraReservasYPrestamosOrdenadosYSinCancelados() {
    UUID cancelado = f.reservado();
    f.service.decidirCancelacion(f.prestamo(cancelado).reservaId(), "c");
    UUID despues = UUID.randomUUID();
    f.service.crearDesdeReserva(f.reserva(despues, HASTA.plusDays(5), HASTA.plusDays(6), null), "c");
    UUID antes = f.reservado();
    assertEquals(List.of(antes, f.repository.findByReserva(despues).orElseThrow().id()), f.queries.calendario(f.prestatario).stream().map(v -> v.prestamo().id()).toList());
    assertTrue(f.queries.calendario(f.intruso).isEmpty());
  }

  @Test void cotizaciones() {
    UUID id = f.activo();
    var pago = f.queries.cotizarPago(f.prestatario, id);
    assertEquals(new BigDecimal("40.00"), pago.importeTarifa(), "48 horas = 2 bloques");
    assertEquals(new BigDecimal("50.00"), pago.garantia());
    assertNull(f.queries.cotizarExtension(f.prestatario, id, null).costoAdicional());
    assertEquals(new BigDecimal("20.00"), f.queries.cotizarExtension(f.prestatario, id, HASTA.plusHours(1)).costoAdicional());
    assertThrows(IllegalArgumentException.class, () -> f.queries.cotizarExtension(f.prestatario, id, HASTA));
    assertThrows(OperacionNoPermitidaException.class, () -> f.queries.cotizarPago(f.intruso, id));
  }

  @Test void elContactoEstaVigenteSoloParaParticipantesYMientrasElPrestamoSigueAbierto() {
    UUID id = f.activo();
    UUID reserva = f.prestamo(id).reservaId();
    assertTrue(f.queries.contactoVigente(reserva, f.prestatario));
    assertTrue(f.queries.contactoVigente(reserva, f.prestamista));
    assertFalse(f.queries.contactoVigente(reserva, f.intruso));
    UUID cerrado = f.finalizado();
    assertFalse(f.queries.contactoVigente(f.prestamo(cerrado).reservaId(), f.prestatario));
    assertThrows(NotFoundException.class, () -> f.queries.contactoVigente(UUID.randomUUID(), f.prestatario));
  }

  @Test void sinCambiosElHistorialEstaVacio() { assertTrue(f.queries.cambiosDeFecha(f.prestatario, f.activo()).isEmpty()); }
}
