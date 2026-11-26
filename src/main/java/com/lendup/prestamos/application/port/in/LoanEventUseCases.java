package com.lendup.prestamos.application.port.in;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import java.time.Instant;
import java.util.UUID;

/** Reacciones a eventos de Reservas, Pagos y Evidencias, y al paso del tiempo. */
public interface LoanEventUseCases {
  void crearDesdeReserva(ReservaConfirmadaCommand command, String correlationId);
  void decidirCancelacion(UUID reservaId, String correlationId);
  void registrarReservaCancelada(UUID reservaId, String correlationId);
  void registrarTarifaPagada(UUID prestamoId, Instant pagadoEn, String correlationId);
  void registrarExtensionPagada(ExtensionPagadaCommand command, String correlationId);
  void registrarResultadoRetencion(UUID prestamoId, UUID cambioId, String resultado, String detalle, String correlationId);
  void registrarPagoRechazado(UUID prestamoId, String tipo, String correlationId);
  void registrarGarantiaConstituida(UUID prestamoId, Instant constituidaEn, String correlationId);
  void registrarIncidenciaReportada(UUID prestamoId, String correlationId);
  void registrarIncidenciaResuelta(UUID prestamoId, String correlationId);
  void proyectarIncidencia(UUID prestamoId, UUID incidenciaId, boolean resuelta, String correlationId);

  /** Marca como VENCIDO los préstamos activos que pasaron su fecha vigente (RF53). Devuelve cuántos cambió. */
  int marcarVencidos(int limite);
}
