package com.lendup.prestamos.application.port.in;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Operaciones que inician los participantes del préstamo. */
public interface LoanUseCases {
  PrestamoView registrarEntrega(UUID actor, UUID prestamoId, String correlationId);
  PrestamoView confirmarRecepcion(UUID actor, UUID prestamoId, String correlationId);
  PrestamoView registrarDevolucion(UUID actor, UUID prestamoId, String correlationId);
  PrestamoView confirmarDevolucion(UUID actor, UUID prestamoId, String correlationId);
  PrestamoView confirmarRecuperacion(UUID actor, UUID prestamoId, String correlationId);

  /** @param costoInformado costo que se le mostró al usuario; si no coincide con el calculado se rechaza. Puede ser nulo. */
  CambioFechaView solicitarExtension(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta, BigDecimal costoInformado, String correlationId);
  CambioFechaView responderExtension(UUID actor, UUID prestamoId, UUID cambioId, String respuesta, String motivo, String correlationId);
  CambioFechaView proponerReprogramacion(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta, String correlationId);
  CambioFechaView responderReprogramacion(UUID actor, UUID prestamoId, UUID cambioId, String respuesta, String motivo, String correlationId);
}
