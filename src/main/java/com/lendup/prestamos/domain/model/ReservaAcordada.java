package com.lendup.prestamos.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Condiciones de la reserva confirmada que el préstamo conserva como copia inmutable (R27). */
public record ReservaAcordada(UUID reservaId, UUID publicacionId, UUID prestamistaUsuarioId, UUID prestatarioUsuarioId, PeriodoPrestamo periodo,
                              BigDecimal tarifaDiaria, BigDecimal garantiaMonetaria, String moneda) {
  public ReservaAcordada {
    if (reservaId == null || publicacionId == null || prestamistaUsuarioId == null || prestatarioUsuarioId == null || periodo == null) {
      throw new IllegalArgumentException("Reserva confirmada incompleta");
    }
    if (prestamistaUsuarioId.equals(prestatarioUsuarioId)) throw new IllegalArgumentException("Prestamista y prestatario deben ser distintos");
    if (tarifaDiaria == null || tarifaDiaria.signum() <= 0) throw new IllegalArgumentException("Tarifa diaria inválida");
    if (garantiaMonetaria != null && garantiaMonetaria.signum() < 0) throw new IllegalArgumentException("Garantía inválida");
    if (moneda == null || !moneda.matches("[A-Z]{3}")) throw new IllegalArgumentException("Moneda inválida");
  }
}
