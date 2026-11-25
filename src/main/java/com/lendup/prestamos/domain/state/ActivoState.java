package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.EstadoPrestamo;

/** El prestatario tiene el objeto: admite cambios de fecha, vencimiento y devolución. */
final class ActivoState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.ACTIVO; }
  @Override public EstadoPrestamo confirmarRecepcion() { throw new ReglaDeNegocioException("La recepción ya fue confirmada"); }
  @Override public EstadoPrestamo vencer() { return EstadoPrestamo.VENCIDO; }
  @Override public void exigirCambioDeFecha() { /* permitido */ }
  @Override public EstadoPrestamo aplicarNuevaFecha(boolean nuevaFechaEnElFuturo) { return EstadoPrestamo.ACTIVO; }

  @Override public EstadoPrestamo registrarDevolucion(boolean conIncidencias) {
    return conIncidencias ? EstadoPrestamo.PENDIENTE_INCIDENCIA : EstadoPrestamo.DEVOLUCION_REGISTRADA;
  }
}
