package com.lendup.prestamos.application;

import static com.lendup.prestamos.domain.model.EstadoCambioFecha.*;
import static com.lendup.prestamos.domain.model.EstadoPrestamo.*;
import static com.lendup.prestamos.support.LoansTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.domain.exception.*;
import com.lendup.prestamos.domain.model.CambioFecha;
import com.lendup.prestamos.support.LoansTestFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Extensiones (RF23, RF24, RF47) y reprogramaciones (RF25). */
class DateChangeTest {
  private static final String ACEPTA = "ACEPTADA_PENDIENTE_PAGO";
  private static final LocalDateTime NUEVA = HASTA.plusDays(1).plusHours(1);
  private final LoansTestFixture f = new LoansTestFixture();

  private CambioFecha.Datos extension(UUID id) { return f.service.solicitarExtension(f.prestatario, id, NUEVA, null, "c").cambio(); }
  private void pagar(UUID id, UUID cambio) { f.service.registrarExtensionPagada(new ExtensionPagadaCommand(id, cambio, UUID.randomUUID(), f.clock.instant()), "c"); }

  @Test void laExtensionSeCobraPorBloquesYQuedaPendienteSinMoverLaFecha() {
    UUID id = f.activo();
    CambioFecha.Datos c = extension(id);
    assertEquals(PENDIENTE, c.estado());
    assertEquals(new BigDecimal("40.00"), c.costoAdicional(), "25 horas = 2 bloques de 20.00");
    assertEquals(HASTA, c.fechaAnteriorEn());
    assertEquals(HASTA, f.prestamo(id).devolucionVigenteEn());
  }

  @Test void elCostoInformadoDebeCoincidirConElCalculado() {
    UUID id = f.activo();
    var e = assertThrows(ReglaDeNegocioException.class, () -> f.service.solicitarExtension(f.prestatario, id, NUEVA, new BigDecimal("10.00"), "c"));
    assertTrue(e.getMessage().contains("40.00"));
    assertTrue(f.prestamo(id).cambios().isEmpty());
    assertDoesNotThrow(() -> f.service.solicitarExtension(f.prestatario, id, NUEVA, new BigDecimal("40"), "c"));
  }

  @Test void soloElPrestatarioPideLaExtensionYSoloConElPrestamoActivo() {
    UUID activo = f.activo();
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.solicitarExtension(f.prestamista, activo, NUEVA, null, "c"));
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.solicitarExtension(f.intruso, activo, NUEVA, null, "c"));
    UUID vencido = f.vencido();
    assertThrows(ReglaDeNegocioException.class, () -> f.service.solicitarExtension(f.prestatario, vencido, NUEVA, null, "c"));
  }

  @Test void noSePuedeExtenderDespuesDeLaFechaAunqueElSchedulerNoHayaPasado() {
    UUID id = f.activo();
    f.clock.setLima(HASTA);
    assertThrows(ReglaDeNegocioException.class, () -> extension(id));
  }

  @Test void laNuevaFechaDebeSerPosteriorALaVigente() {
    UUID id = f.activo();
    assertThrows(IllegalArgumentException.class, () -> f.service.solicitarExtension(f.prestatario, id, HASTA, null, "c"));
    assertThrows(IllegalArgumentException.class, () -> f.service.solicitarExtension(f.prestatario, id, HASTA.minusDays(1), null, "c"));
  }

  @Test void noSePuedeSuperponerConOtraReservaDelMismoObjeto() {
    UUID id = f.activo();
    f.service.crearDesdeReserva(f.reserva(UUID.randomUUID(), HASTA.plusHours(3), HASTA.plusDays(2), null), "c");
    assertThrows(ReglaDeNegocioException.class, () -> extension(id));
    assertDoesNotThrow(() -> f.service.solicitarExtension(f.prestatario, id, HASTA.plusHours(3), null, "c"), "puede llegar justo hasta donde empieza la otra");
  }

  @Test void siOtraReservaAparecioDespuesNoSePuedeAceptar() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.crearDesdeReserva(f.reserva(UUID.randomUUID(), HASTA.plusHours(3), HASTA.plusDays(2), null), "c");
    assertThrows(ReglaDeNegocioException.class, () -> f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c"));
  }

  @Test void unaReservaCanceladaNoBloqueaLaExtension() {
    UUID id = f.activo();
    UUID otra = UUID.randomUUID();
    f.service.crearDesdeReserva(f.reserva(otra, HASTA.plusHours(3), HASTA.plusDays(2), null), "c");
    f.service.decidirCancelacion(otra, "c");
    assertDoesNotThrow(() -> extension(id));
  }

  @Test void noPuedeHaberDosCambiosEnCurso() {
    UUID id = f.activo();
    extension(id);
    assertThrows(ReglaDeNegocioException.class, () -> extension(id));
    assertThrows(ReglaDeNegocioException.class, () -> f.service.proponerReprogramacion(f.prestamista, id, NUEVA, "c"));
  }

  @Test void alAceptarSePublicaExtensionAceptadaYLaFechaSigueIgualHastaElPago() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    var respuesta = f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c").cambio();
    assertEquals(ACEPTADA_PENDIENTE_PAGO, respuesta.estado());
    assertEquals(HASTA, f.prestamo(id).devolucionVigenteEn());
    EventEnvelope e = f.ultimo(PrestamoEvento.EXTENSION_ACEPTADA);
    assertEquals(cambio, e.payload().get("cambio_fecha_prestamo_id"));
    assertEquals(new BigDecimal("40.00"), e.payload().get("costo_adicional"));
    assertEquals("PEN", e.payload().get("moneda"));
    assertEquals(f.prestatario, e.payload().get("prestatario_usuario_id"));
  }

  @Test void elPagoConfirmadoAplicaLaNuevaFechaYConservaLaOriginal() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    pagar(id, cambio);
    var p = f.prestamo(id).datos();
    assertEquals(NUEVA, p.devolucionVigenteEn());
    assertEquals(HASTA, p.devolucionOriginalEn());
    var c = f.prestamo(id).buscarCambio(cambio).orElseThrow().datos();
    assertEquals(APLICADA, c.estado());
    assertEquals(NUEVA, c.fechaResultanteEn());
    assertNotNull(c.transaccionPagoId());
  }

  @Test void unPagoRepetidoOSinAceptacionNoTieneEfecto() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    pagar(id, cambio);
    assertEquals(HASTA, f.prestamo(id).devolucionVigenteEn(), "no estaba aceptada");
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    pagar(id, cambio);
    long version = f.prestamo(id).version();
    pagar(id, cambio);
    assertEquals(version, f.prestamo(id).version());
    assertEquals(2, f.auditoria.stream().filter(a -> a.accion().equals("PAGO_EXTENSION_SIN_EFECTO")).count());
  }

  @Test void unPagoTardioRegularizaElPrestamoVencido() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    f.clock.setLima(HASTA.plusMinutes(5));
    f.service.marcarVencidos(10);
    assertEquals(VENCIDO, f.estado(id));
    pagar(id, cambio);
    assertEquals(ACTIVO, f.estado(id));
    assertNull(f.prestamo(id).datos().vencidoEn());
    assertEquals(NUEVA, f.prestamo(id).devolucionVigenteEn());
  }

  @Test void siElObjetoYaSeDevolvioElPagoNoMueveLaFecha() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    f.service.registrarDevolucion(f.prestatario, id, "c");
    pagar(id, cambio);
    assertEquals(HASTA, f.prestamo(id).devolucionVigenteEn());
    assertEquals(DEVOLUCION_REGISTRADA, f.estado(id));
  }

  @Test void elRechazoConservaElMotivoYPermiteOtraPropuesta() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    var r = f.service.responderExtension(f.prestamista, id, cambio, "RECHAZADA", " Lo necesito ", "c").cambio();
    assertEquals(RECHAZADA, r.estado());
    assertEquals("Lo necesito", r.motivoRechazo());
    assertEquals(f.prestamista, r.respondidoPorUsuarioId());
    assertThrows(ReglaDeNegocioException.class, () -> f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c"));
    assertDoesNotThrow(() -> extension(id));
    assertEquals(2, f.queries.cambiosDeFecha(f.prestatario, id).size(), "se conserva el historial (RF47)");
  }

  @Test void respuestasInvalidas() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    assertThrows(IllegalArgumentException.class, () -> f.service.responderExtension(f.prestamista, id, cambio, "APLICADA", null, "c"));
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.responderExtension(f.prestatario, id, cambio, ACEPTA, null, "c"));
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.responderExtension(f.intruso, id, UUID.randomUUID(), ACEPTA, null, "c"));
    assertThrows(NotFoundException.class, () -> f.service.responderExtension(f.prestamista, id, UUID.randomUUID(), ACEPTA, null, "c"));
    assertThrows(NotFoundException.class, () -> f.service.responderReprogramacion(f.prestatario, id, cambio, "APLICADA", null, "c"), "es una extensión, no una reprogramación");
  }

  @Test void laReprogramacionDelPrestamistaNoTieneCostoYMueveLaFechaAlAceptarse() {
    UUID id = f.activo();
    var c = f.service.proponerReprogramacion(f.prestamista, id, NUEVA, "c").cambio();
    assertNull(c.costoAdicional());
    assertEquals(HASTA, f.prestamo(id).devolucionVigenteEn());
    var r = f.service.responderReprogramacion(f.prestatario, id, c.id(), "APLICADA", null, "c").cambio();
    assertEquals(APLICADA, r.estado());
    assertEquals(NUEVA, f.prestamo(id).devolucionVigenteEn());
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.EXTENSION_ACEPTADA), "no genera cobro");
  }

  @Test void laReprogramacionTambienPuedeAdelantarLaFecha() {
    UUID id = f.activo();
    LocalDateTime antes = HASTA.minusHours(5);
    UUID cambio = f.service.proponerReprogramacion(f.prestamista, id, antes, "c").cambio().id();
    f.service.responderReprogramacion(f.prestatario, id, cambio, "APLICADA", null, "c");
    assertEquals(antes, f.prestamo(id).devolucionVigenteEn());
  }

  // ---- Eventos que permiten avisar a la contraparte y mover los recordatorios (US21, US22, US43) ----

  @Test void laSolicitudDeExtensionSePublicaParaAvisarAlPrestamista() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    var p = f.ultimo(PrestamoEvento.EXTENSION_SOLICITADA).payload();
    assertEquals(cambio, p.get("cambio_fecha_prestamo_id"));
    assertEquals(HASTA, p.get("fecha_anterior_en"));
    assertEquals(NUEVA, p.get("fecha_propuesta_en"));
    assertEquals(new BigDecimal("40.00"), p.get("costo_adicional"));
    assertEquals("PEN", p.get("moneda"));
    assertEquals(f.prestamista, p.get("prestamista_usuario_id"));
  }

  @Test void elRechazoDeLaExtensionSePublicaConElMotivo() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, "RECHAZADA", " Lo necesito ", "c");
    var p = f.ultimo(PrestamoEvento.EXTENSION_RECHAZADA).payload();
    assertEquals(cambio, p.get("cambio_fecha_prestamo_id"));
    assertEquals("Lo necesito", p.get("motivo_rechazo"));
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.EXTENSION_ACEPTADA));
  }

  @Test void elRechazoSinMotivoNoIncluyeElCampo() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, "RECHAZADA", "  ", "c");
    assertFalse(f.ultimo(PrestamoEvento.EXTENSION_RECHAZADA).payload().containsKey("motivo_rechazo"));
  }

  @Test void laNuevaFechaSePublicaRecienConElPagoDeLaExtension() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA));
    pagar(id, cambio);
    var p = f.ultimo(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA).payload();
    assertEquals(cambio, p.get("cambio_fecha_prestamo_id"));
    assertEquals("EXTENSION", p.get("tipo"));
    assertEquals(HASTA, p.get("devolucion_anterior_en"));
    assertEquals(NUEVA, p.get("devolucion_vigente_en"));
  }

  @Test void unPagoSinEfectoNoPublicaOtraFecha() {
    UUID id = f.activo();
    UUID cambio = extension(id).id();
    f.service.responderExtension(f.prestamista, id, cambio, ACEPTA, null, "c");
    f.service.registrarDevolucion(f.prestatario, id, "c");
    pagar(id, cambio);
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA));
  }

  @Test void laReprogramacionPublicaLaPropuestaYLaFechaAceptada() {
    UUID id = f.activo();
    UUID cambio = f.service.proponerReprogramacion(f.prestamista, id, NUEVA, "c").cambio().id();
    var propuesta = f.ultimo(PrestamoEvento.REPROGRAMACION_PROPUESTA).payload();
    assertEquals(cambio, propuesta.get("cambio_fecha_prestamo_id"));
    assertEquals(NUEVA, propuesta.get("fecha_propuesta_en"));
    f.service.responderReprogramacion(f.prestatario, id, cambio, "APLICADA", null, "c");
    var fecha = f.ultimo(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA).payload();
    assertEquals("REPROGRAMACION", fecha.get("tipo"));
    assertEquals(NUEVA, fecha.get("devolucion_vigente_en"));
  }

  @Test void elRechazoDeLaReprogramacionSePublica() {
    UUID id = f.activo();
    UUID cambio = f.service.proponerReprogramacion(f.prestamista, id, NUEVA, "c").cambio().id();
    f.service.responderReprogramacion(f.prestatario, id, cambio, "RECHAZADA", "Viajo ese día", "c");
    assertEquals("Viajo ese día", f.ultimo(PrestamoEvento.REPROGRAMACION_RECHAZADA).payload().get("motivo_rechazo"));
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA));
  }

  @Test void siLaPropuestaSeSuperponeNoSePublicaNada() {
    UUID id = f.activo();
    f.service.crearDesdeReserva(f.reserva(UUID.randomUUID(), HASTA.plusHours(3), HASTA.plusDays(2), null), "c");
    assertThrows(ReglaDeNegocioException.class, () -> extension(id));
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.EXTENSION_SOLICITADA));
  }

  @Test void reglasDeLaReprogramacion() {
    UUID id = f.activo();
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.proponerReprogramacion(f.prestatario, id, NUEVA, "c"));
    assertThrows(IllegalArgumentException.class, () -> f.service.proponerReprogramacion(f.prestamista, id, HASTA, "c"));
    assertThrows(IllegalArgumentException.class, () -> f.service.proponerReprogramacion(f.prestamista, id, DESDE.minusDays(1), "c"));
    UUID cambio = f.service.proponerReprogramacion(f.prestamista, id, HASTA.minusHours(5), "c").cambio().id();
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.responderReprogramacion(f.prestamista, id, cambio, "APLICADA", null, "c"));
    f.clock.setLima(HASTA.minusHours(1));
    assertThrows(ReglaDeNegocioException.class, () -> f.service.responderReprogramacion(f.prestatario, id, cambio, "APLICADA", null, "c"), "la fecha propuesta ya pasó");
    assertEquals(RECHAZADA, f.service.responderReprogramacion(f.prestatario, id, cambio, "RECHAZADA", null, "c").cambio().estado());
  }
}
