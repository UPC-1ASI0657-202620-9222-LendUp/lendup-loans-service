package com.lendup.prestamos.application.port.in;

import com.lendup.prestamos.domain.model.CambioFecha;
import com.lendup.prestamos.domain.model.Prestamo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Datos de entrada y salida de los casos de uso (sin dependencias de la web ni de la base de datos). */
public final class LoanViews {
  private LoanViews() {}

  public record PrestamoView(Prestamo.Datos prestamo, List<CambioFecha.Datos> cambios, List<Map<String, Object>> evidencias) {}

  public record IncidenciasView(UUID id, BigDecimal garantia, String moneda, UUID prestamista, UUID prestatario) {}

  public record CambioFechaView(UUID prestamoId, CambioFecha.Datos cambio) {}

  public record CotizacionExtensionView(UUID prestamoId, BigDecimal tarifaDiaria, String moneda, LocalDateTime devolucionVigenteEn, LocalDateTime fechaPropuestaEn,
                                        BigDecimal costoAdicional) {}

  public record CotizacionPagoView(UUID prestamoId, BigDecimal tarifaDiaria, BigDecimal garantia, String moneda, BigDecimal importeTarifa) {}

  public record ReservaConfirmadaCommand(UUID reservaId, UUID publicacionId, UUID prestamistaUsuarioId, UUID prestatarioUsuarioId, LocalDateTime desde, LocalDateTime hasta,
                                         BigDecimal tarifaDiaria, BigDecimal garantiaMonetaria, String moneda) {}

  public record ExtensionPagadaCommand(UUID prestamoId, UUID cambioFechaId, UUID transaccionId, Instant pagadoEn) {}
}
