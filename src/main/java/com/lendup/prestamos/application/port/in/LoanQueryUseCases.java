package com.lendup.prestamos.application.port.in;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Consultas de préstamos, calendario, historial de cambios de fecha y cotizaciones. */
public interface LoanQueryUseCases {
  List<PrestamoView> listar(UUID actor, String estado);
  PrestamoView detalle(UUID actor, UUID prestamoId, String bearerToken);
  List<PrestamoView> calendario(UUID actor);
  List<CambioFechaView> cambiosDeFecha(UUID actor, UUID prestamoId);
  CotizacionExtensionView cotizarExtension(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta);
  CotizacionPagoView cotizarPago(UUID actor, UUID prestamoId);

  /** Contrato interno con Reservas: si el contacto entre participantes sigue vigente (RF52). */
  boolean contactoVigente(UUID reservaId, UUID usuarioId);
}
