package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.model.EstadoPrestamo;

/**
 * Pasó la fecha vigente sin devolución (RF53). Se puede devolver. Si llega el pago de una extensión que el
 * prestamista aceptó antes del vencimiento, la nueva fecha regulariza el préstamo y vuelve a ACTIVO.
 */
final class VencidoState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.VENCIDO; }

  @Override public EstadoPrestamo aplicarNuevaFecha(boolean nuevaFechaEnElFuturo) {
    return nuevaFechaEnElFuturo ? EstadoPrestamo.ACTIVO : EstadoPrestamo.VENCIDO;
  }

  @Override public EstadoPrestamo registrarDevolucion(boolean conIncidencias) {
    return conIncidencias ? EstadoPrestamo.PENDIENTE_INCIDENCIA : EstadoPrestamo.DEVOLUCION_REGISTRADA;
  }
}
