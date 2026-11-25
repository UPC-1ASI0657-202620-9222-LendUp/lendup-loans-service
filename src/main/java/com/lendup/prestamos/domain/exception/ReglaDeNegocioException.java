package com.lendup.prestamos.domain.exception;

/** La operación no es válida en el estado actual del préstamo (409). */
public class ReglaDeNegocioException extends RuntimeException {
  public ReglaDeNegocioException(String message) { super(message); }
}
