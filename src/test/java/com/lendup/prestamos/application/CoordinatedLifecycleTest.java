package com.lendup.prestamos.application;

import static org.junit.jupiter.api.Assertions.*;
import static com.lendup.prestamos.domain.model.EstadoPrestamo.*;

import com.lendup.prestamos.application.port.in.LoanViews.ExtensionPagadaCommand;
import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.domain.exception.OperacionNoPermitidaException;
import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.EstadoCambioFecha;
import com.lendup.prestamos.support.LoansTestFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CoordinatedLifecycleTest {
  final LoansTestFixture f = new LoansTestFixture();

  @Test void retieneAntesDelCheckoutYAplicaSoloAlConfirmarOcupacion() {
    ReflectionTestUtils.setField(f.service, "coordinatedExtensions", true);
    var loan = f.activo();
    var before = f.prestamo(loan).devolucionVigenteEn();
    var change = f.service.solicitarExtension(f.prestatario, loan, before.plusDays(1), null, "c").cambio().id();
    var accepted = f.service.responderExtension(f.prestamista, loan, change, "ACEPTADA_PENDIENTE_PAGO", null, "c").cambio();
    assertEquals(EstadoCambioFecha.ACEPTADA_PENDIENTE_RETENCION, accepted.estado());
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.EXTENSION_ACEPTADA));
    f.service.registrarResultadoRetencion(loan, change, "RETENIDA", f.clock.instant().plusSeconds(120).toString(), "c");
    f.service.registrarExtensionPagada(new ExtensionPagadaCommand(loan, change, UUID.randomUUID(), f.clock.instant()), "c");
    assertEquals(before, f.prestamo(loan).devolucionVigenteEn());
    assertThrows(ReglaDeNegocioException.class, () -> f.service.registrarDevolucion(f.prestatario, loan, "c"));
    f.service.registrarResultadoRetencion(loan, change, "CONFIRMADA", "", "c");
    f.service.registrarResultadoRetencion(loan, change, "CONFIRMADA", "", "c");
    assertEquals(before.plusDays(1), f.prestamo(loan).devolucionVigenteEn());
    assertEquals(1, f.tiposDeEvento().stream().filter(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA::equals).count());
  }

  @Test void rechazoTardioYRetencionFueraDeOrdenNoAplicanFecha() {
    ReflectionTestUtils.setField(f.service, "coordinatedExtensions", true);
    var loan = f.activo();
    var before = f.prestamo(loan).devolucionVigenteEn();
    var change = f.service.solicitarExtension(f.prestatario, loan, before.plusDays(1), null, "c").cambio().id();
    f.service.responderExtension(f.prestamista, loan, change, "ACEPTADA_PENDIENTE_PAGO", null, "c");
    f.service.registrarResultadoRetencion(loan, change, "RECHAZADA", "EXPIRADA", "c");
    f.service.registrarResultadoRetencion(loan, change, "RETENIDA", f.clock.instant().plusSeconds(120).toString(), "c");
    f.service.registrarExtensionPagada(new ExtensionPagadaCommand(loan, change, UUID.randomUUID(), f.clock.instant()), "c");
    assertEquals(EstadoCambioFecha.RECHAZADA, f.prestamo(loan).buscarCambio(change).orElseThrow().estado());
    assertEquals(before, f.prestamo(loan).devolucionVigenteEn());
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.EXTENSION_ACEPTADA));
  }

  @Test void conservacionDeOcupacionHastaRecuperacionYRechazoSiRecepcionGano() {
    ReflectionTestUtils.setField(f.service, "cancellationRecovery", true);
    var delivered = f.entregado();
    f.service.decidirCancelacion(f.prestamo(delivered).reservaId(), "c");
    f.service.decidirCancelacion(f.prestamo(delivered).reservaId(), "c");
    assertEquals(RECUPERACION_PENDIENTE, f.estado(delivered));
    assertFalse(f.tiposDeEvento().contains(PrestamoEvento.CANCELADO));
    assertThrows(ReglaDeNegocioException.class, () -> f.service.confirmarRecepcion(f.prestatario, delivered, "c"));
    assertThrows(OperacionNoPermitidaException.class, () -> f.service.confirmarRecuperacion(f.prestatario, delivered, "c"));
    f.service.confirmarRecuperacion(f.prestamista, delivered, "c");
    f.service.confirmarRecuperacion(f.prestamista, delivered, "c");
    assertEquals(CANCELADO, f.estado(delivered));
    assertEquals(1, f.tiposDeEvento().stream().filter(PrestamoEvento.CANCELADO::equals).count());
    var active = f.activo();
    f.service.decidirCancelacion(f.prestamo(active).reservaId(), "c");
    assertEquals(ACTIVO, f.estado(active));
    assertTrue(f.tiposDeEvento().contains(PrestamoEvento.CANCELACION_RECHAZADA));
  }
}
