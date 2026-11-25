package com.lendup.prestamos.domain.exception;

/** El usuario no participa en el préstamo o no tiene el rol contextual que exige la operación (403). */
public class OperacionNoPermitidaException extends RuntimeException {
  public OperacionNoPermitidaException(String message) { super(message); }
}
