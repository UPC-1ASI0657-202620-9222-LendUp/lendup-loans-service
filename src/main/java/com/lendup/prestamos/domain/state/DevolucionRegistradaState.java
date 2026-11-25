package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.model.EstadoPrestamo;

/** El prestatario registró la devolución: el prestamista la confirma y cierra el préstamo. */
final class DevolucionRegistradaState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.DEVOLUCION_REGISTRADA; }
  @Override public EstadoPrestamo confirmarDevolucion() { return EstadoPrestamo.FINALIZADO; }
  @Override public EstadoPrestamo reportarIncidencia() { return EstadoPrestamo.PENDIENTE_INCIDENCIA; }
}
