package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.model.EstadoPrestamo;

/** Reserva confirmada: se puede entregar (con pago y garantía) o cancelar. */
final class ReservadoState implements PrestamoState {
  @Override public EstadoPrestamo estado() { return EstadoPrestamo.RESERVADO; }
  @Override public EstadoPrestamo registrarEntrega() { return EstadoPrestamo.ENTREGA_REGISTRADA; }
  @Override public EstadoPrestamo cancelar() { return EstadoPrestamo.CANCELADO; }
}
