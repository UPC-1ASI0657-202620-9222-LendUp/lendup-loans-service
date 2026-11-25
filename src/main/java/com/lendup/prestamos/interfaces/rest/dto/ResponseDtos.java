package com.lendup.prestamos.interfaces.rest.dto;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.domain.model.CambioFecha;
import com.lendup.prestamos.domain.model.Prestamo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Respuestas en snake_case con los campos de la tabla prestamos del monolito, que es lo que consume
 * services/api/mappers/domain.ts. Los campos estado_detalle, garantia_monetaria_acordada y cambios_fecha son nuevos.
 */
public final class ResponseDtos {
  private ResponseDtos() {}

  public record PrestamoResponse(UUID id, UUID reserva_id, UUID publicacion_id, UUID prestamista_usuario_id, UUID prestatario_usuario_id, String estado, String estado_detalle,
                                 LocalDateTime entrega_programada_en, LocalDateTime devolucion_original_en, LocalDateTime devolucion_vigente_en,
                                 BigDecimal tarifa_diaria_acordada, String moneda, boolean garantia_requerida, BigDecimal garantia_monetaria_acordada,
                                 Instant pago_tarifa_confirmado_en, Instant garantia_constituida_en, Instant entrega_registrada_en, Instant recepcion_confirmada_en,
                                 Instant activado_en, Instant devolucion_registrada_en, Instant devolucion_confirmada_en, int incidencias_pendientes, Instant vencido_en,
                                 Instant finalizado_en, Instant cancelado_en, Instant creado_en, Instant actualizado_en, List<CambioFechaResponse> cambios_fecha,
                                 List<Map<String, Object>> evidencias) {
    public static PrestamoResponse from(PrestamoView view) {
      Prestamo.Datos p = view.prestamo();
      return new PrestamoResponse(p.id(), p.reservaId(), p.publicacionId(), p.prestamistaUsuarioId(), p.prestatarioUsuarioId(), p.estado().publico().name(), p.estado().name(),
          p.entregaProgramadaEn(), p.devolucionOriginalEn(), p.devolucionVigenteEn(), p.tarifaDiariaAcordada(), p.moneda(), p.garantiaRequerida(),
          p.garantiaMonetariaAcordada(), p.pagoTarifaConfirmadoEn(), p.garantiaConstituidaEn(), p.entregaRegistradaEn(), p.recepcionConfirmadaEn(), p.activadoEn(),
          p.devolucionRegistradaEn(), p.devolucionConfirmadaEn(), p.incidenciasPendientes(), p.vencidoEn(), p.finalizadoEn(), p.canceladoEn(), p.creadoEn(),
          p.actualizadoEn(), view.cambios().stream().map(c -> CambioFechaResponse.from(p.id(), c)).toList(), view.evidencias());
    }
  }

  public record CambioFechaResponse(UUID id, UUID prestamo_id, String tipo, String estado, UUID propuesto_por_usuario_id, LocalDateTime fecha_anterior_en,
                                    LocalDateTime fecha_propuesta_en, BigDecimal costo_adicional, String moneda, Instant propuesto_en, UUID respondido_por_usuario_id,
                                    Instant respondido_en, String motivo_rechazo, UUID transaccion_pago_id, Instant pago_confirmado_en,
                                    LocalDateTime fecha_resultante_en, Instant aplicado_en) {
    public static CambioFechaResponse from(CambioFechaView view) { return from(view.prestamoId(), view.cambio()); }

    static CambioFechaResponse from(UUID prestamoId, CambioFecha.Datos c) {
      return new CambioFechaResponse(c.id(), prestamoId, c.tipo().name(), c.estado().name(), c.propuestoPorUsuarioId(), c.fechaAnteriorEn(), c.fechaPropuestaEn(),
          c.costoAdicional(), c.moneda(), c.propuestoEn(), c.respondidoPorUsuarioId(), c.respondidoEn(), c.motivoRechazo(), c.transaccionPagoId(), c.pagoConfirmadoEn(),
          c.fechaResultanteEn(), c.aplicadoEn());
    }
  }

  public record ExtensionQuoteResponse(UUID prestamo_id, BigDecimal tarifa_diaria, String moneda, String estado, String mensaje, LocalDateTime devolucion_vigente_en,
                                       LocalDateTime fecha_propuesta_en, BigDecimal costo_adicional) {
    public static ExtensionQuoteResponse from(CotizacionExtensionView c) {
      boolean calculada = c.costoAdicional() != null;
      return new ExtensionQuoteResponse(c.prestamoId(), c.tarifaDiaria(), c.moneda(), calculada ? "CALCULADA" : "COTIZACION_PRELIMINAR",
          calculada ? "Costo por bloques iniciados de 24 horas" : "El costo adicional requiere la fecha propuesta", c.devolucionVigenteEn(), c.fechaPropuestaEn(),
          c.costoAdicional());
    }
  }

  public record PaymentQuoteResponse(UUID prestamo_id, BigDecimal tarifa_diaria, BigDecimal garantia, String moneda, String estado, BigDecimal importe_tarifa) {
    public static PaymentQuoteResponse from(CotizacionPagoView c) {
      return new PaymentQuoteResponse(c.prestamoId(), c.tarifaDiaria(), c.garantia(), c.moneda(), "COTIZACION_PRELIMINAR", c.importeTarifa());
    }
  }

  public record ContactAccessResponse(boolean contacto_vigente) {}
}
