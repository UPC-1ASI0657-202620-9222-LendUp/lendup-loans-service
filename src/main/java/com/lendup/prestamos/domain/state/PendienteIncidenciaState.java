package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.model.EstadoPrestamo;

/** Objeto devuelto con incidencias sin resolver: no se puede finalizar hasta que se resuelvan todas (RF27). */
final class PendienteIncidenciaState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.PENDIENTE_INCIDENCIA; }
  @Override public EstadoPrestamo resolverIncidencias() { return EstadoPrestamo.DEVOLUCION_REGISTRADA; }
}
