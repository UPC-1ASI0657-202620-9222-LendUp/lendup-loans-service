package com.lendup.prestamos.domain.model;

public enum EstadoCambioFecha {
  PENDIENTE, RECHAZADA, ACEPTADA_PENDIENTE_PAGO, APLICADA;

  /** Todavía espera una respuesta o un pago: mientras exista, no se puede proponer otro cambio. */
  public boolean enCurso() { return this == PENDIENTE || this == ACEPTADA_PENDIENTE_PAGO; }
}
