package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.EstadoPrestamo;

/** El prestamista registró la entrega: falta que el prestatario confirme la recepción. */
final class EntregaRegistradaState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.ENTREGA_REGISTRADA; }
  @Override public EstadoPrestamo registrarEntrega() { throw new ReglaDeNegocioException("La entrega ya fue registrada"); }
  @Override public EstadoPrestamo confirmarRecepcion() { return EstadoPrestamo.ACTIVO; }
}
