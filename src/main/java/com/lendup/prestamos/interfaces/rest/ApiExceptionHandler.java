package com.lendup.prestamos.interfaces.rest;

import com.lendup.prestamos.application.exception.*;
import com.lendup.prestamos.domain.exception.*;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Mismo formato de error que el monolito, Reservas y Pagos: {status, error, message}. */
@RestControllerAdvice
public class ApiExceptionHandler {
  record ErrorResponse(int status, String error, String message, Instant timestamp) {}

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> notFound(Exception e) { return error(404, "NO_ENCONTRADO", e.getMessage()); }

  @ExceptionHandler({ForbiddenException.class, OperacionNoPermitidaException.class})
  ResponseEntity<ErrorResponse> forbidden(Exception e) { return error(403, "NO_AUTORIZADO", e.getMessage()); }

  @ExceptionHandler(ReglaDeNegocioException.class)
  ResponseEntity<ErrorResponse> conflict(Exception e) { return error(409, "CONFLICTO_NEGOCIO", e.getMessage()); }

  @ExceptionHandler(DependencyUnavailableException.class)
  ResponseEntity<ErrorResponse> unavailable(Exception e) { return error(503, "DEPENDENCIA_NO_DISPONIBLE", e.getMessage()); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
    String detail = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().findFirst().orElse("Solicitud inválida");
    return error(400, "SOLICITUD_INVALIDA", detail);
  }

  @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ErrorResponse> unreadable(Exception e) { return error(400, "SOLICITUD_INVALIDA", "Solicitud inválida"); }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> bad(Exception e) { return error(400, "SOLICITUD_INVALIDA", e.getMessage() == null ? "Solicitud inválida" : e.getMessage()); }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> database(Exception e) { return error(409, "CONFLICTO_DATOS", "Conflicto de unicidad o integridad"); }

  private static ResponseEntity<ErrorResponse> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(status, code, message, Instant.now()));
  }
}
