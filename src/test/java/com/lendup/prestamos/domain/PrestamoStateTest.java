package com.lendup.prestamos.domain;

import static com.lendup.prestamos.domain.model.EstadoPrestamo.*;
import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.domain.state.PrestamoState;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tabla completa de transiciones del patrón State: lo que no está permitido explícitamente se rechaza. */
class PrestamoStateTest {
  private static final Map<EstadoPrestamo, EstadoPrestamo> ENTREGA = Map.of(RESERVADO, ENTREGA_REGISTRADA);
  private static final Map<EstadoPrestamo, EstadoPrestamo> RECEPCION = Map.of(ENTREGA_REGISTRADA, ACTIVO);
  private static final Map<EstadoPrestamo, EstadoPrestamo> CANCELACION = Map.of(RESERVADO, CANCELADO);
  private static final Map<EstadoPrestamo, EstadoPrestamo> VENCIMIENTO = Map.of(ACTIVO, VENCIDO);
  private static final Map<EstadoPrestamo, EstadoPrestamo> DEVOLUCION = Map.of(ACTIVO, DEVOLUCION_REGISTRADA, VENCIDO, DEVOLUCION_REGISTRADA);
  private static final Map<EstadoPrestamo, EstadoPrestamo> DEVOLUCION_CON_INCIDENCIA = Map.of(ACTIVO, PENDIENTE_INCIDENCIA, VENCIDO, PENDIENTE_INCIDENCIA);
  private static final Map<EstadoPrestamo, EstadoPrestamo> CONFIRMACION = Map.of(DEVOLUCION_REGISTRADA, FINALIZADO);

  @ParameterizedTest @EnumSource(EstadoPrestamo.class)
  void cadaEstadoSoloPermiteSusTransiciones(EstadoPrestamo estado) {
    verificar(estado, ENTREGA, PrestamoState::registrarEntrega);
    verificar(estado, RECEPCION, PrestamoState::confirmarRecepcion);
    verificar(estado, CANCELACION, PrestamoState::cancelar);
    verificar(estado, VENCIMIENTO, PrestamoState::vencer);
    verificar(estado, DEVOLUCION, s -> s.registrarDevolucion(false));
    verificar(estado, DEVOLUCION_CON_INCIDENCIA, s -> s.registrarDevolucion(true));
    verificar(estado, CONFIRMACION, PrestamoState::confirmarDevolucion);
  }

  @ParameterizedTest @EnumSource(EstadoPrestamo.class)
  void soloUnPrestamoActivoAdmiteCambiosDeFecha(EstadoPrestamo estado) {
    if (estado == ACTIVO) assertDoesNotThrow(() -> PrestamoState.de(estado).exigirCambioDeFecha());
    else assertThrows(ReglaDeNegocioException.class, () -> PrestamoState.de(estado).exigirCambioDeFecha());
  }

  @ParameterizedTest @EnumSource(EstadoPrestamo.class)
  void elStateCorrespondeASuEstado(EstadoPrestamo estado) { assertEquals(estado, PrestamoState.de(estado).estado()); }

  @Test void unaExtensionPagadaRegularizaUnPrestamoVencidoSoloSiLaFechaEsFutura() {
    assertEquals(ACTIVO, PrestamoState.de(VENCIDO).aplicarNuevaFecha(true));
    assertEquals(VENCIDO, PrestamoState.de(VENCIDO).aplicarNuevaFecha(false));
    assertThrows(ReglaDeNegocioException.class, () -> PrestamoState.de(DEVOLUCION_REGISTRADA).aplicarNuevaFecha(true));
  }

  @Test void lasIncidenciasBloqueanYLiberanElCierre() {
    assertEquals(PENDIENTE_INCIDENCIA, PrestamoState.de(DEVOLUCION_REGISTRADA).reportarIncidencia());
    assertEquals(DEVOLUCION_REGISTRADA, PrestamoState.de(PENDIENTE_INCIDENCIA).resolverIncidencias());
    assertEquals(ACTIVO, PrestamoState.de(ACTIVO).reportarIncidencia());
  }

  @Test void elFrontendNoVeEstadosInternosSinContratoPublico() {
    assertEquals(DEVOLUCION_REGISTRADA, PENDIENTE_INCIDENCIA.publico());
    assertEquals(ENTREGA_REGISTRADA, RECUPERACION_PENDIENTE.publico());
    EnumSet.complementOf(EnumSet.of(PENDIENTE_INCIDENCIA, RECUPERACION_PENDIENTE)).forEach(e -> assertEquals(e, e.publico()));
  }

  private static void verificar(EstadoPrestamo estado, Map<EstadoPrestamo, EstadoPrestamo> permitidas, Function<PrestamoState, EstadoPrestamo> accion) {
    EstadoPrestamo esperado = permitidas.get(estado);
    if (esperado == null) assertThrows(ReglaDeNegocioException.class, () -> accion.apply(PrestamoState.de(estado)), () -> "Debió rechazarse en " + estado);
    else assertEquals(esperado, accion.apply(PrestamoState.de(estado)));
  }
}
