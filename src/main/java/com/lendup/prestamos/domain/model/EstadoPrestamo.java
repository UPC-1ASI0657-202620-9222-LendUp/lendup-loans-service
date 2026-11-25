package com.lendup.prestamos.domain.model;

/** Estados del préstamo (Fig. 9). Las transiciones válidas las define cada {@code PrestamoState}. */
public enum EstadoPrestamo {
  RESERVADO, ENTREGA_REGISTRADA, ACTIVO, VENCIDO, DEVOLUCION_REGISTRADA, PENDIENTE_INCIDENCIA, FINALIZADO, CANCELADO;

  /** El préstamo sigue en curso: se puede reportar incidencias y el contacto entre participantes está vigente. */
  public boolean abierto() { return this != FINALIZADO && this != CANCELADO; }

  /**
   * Estado que ve el frontend. PENDIENTE_INCIDENCIA es un detalle interno de la Fig. 9 que la web no conoce: se
   * muestra como DEVOLUCION_REGISTRADA (igual que el monolito) y el valor real viaja en estado_detalle.
   */
  public EstadoPrestamo publico() { return this == PENDIENTE_INCIDENCIA ? DEVOLUCION_REGISTRADA : this; }
}
