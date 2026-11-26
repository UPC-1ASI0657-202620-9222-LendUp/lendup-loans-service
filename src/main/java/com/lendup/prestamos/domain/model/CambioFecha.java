package com.lendup.prestamos.domain.model;

import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad del agregado Préstamo: una propuesta de extensión o reprogramación con su respuesta y resultado. Nunca se
 * borra ni se sobrescribe la fecha anterior, para conservar el historial completo (RF24, RF47).
 */
public final class CambioFecha {
  /** Estado completo, para reconstruir la entidad desde la base de datos y para exponerla. */
  public record Datos(UUID id, TipoCambioFecha tipo, EstadoCambioFecha estado, UUID propuestoPorUsuarioId, LocalDateTime fechaAnteriorEn,
                      LocalDateTime fechaPropuestaEn, BigDecimal costoAdicional, String moneda, Instant propuestoEn, UUID respondidoPorUsuarioId,
                      Instant respondidoEn, String motivoRechazo, UUID transaccionPagoId, Instant pagoConfirmadoEn, LocalDateTime fechaResultanteEn,
                      Instant aplicadoEn) {}

  private final UUID id;
  private final TipoCambioFecha tipo;
  private final UUID propuestoPorUsuarioId;
  private final LocalDateTime fechaAnteriorEn;
  private final LocalDateTime fechaPropuestaEn;
  private final BigDecimal costoAdicional;
  private final String moneda;
  private final Instant propuestoEn;
  private EstadoCambioFecha estado;
  private UUID respondidoPorUsuarioId;
  private Instant respondidoEn;
  private String motivoRechazo;
  private UUID transaccionPagoId;
  private Instant pagoConfirmadoEn;
  private LocalDateTime fechaResultanteEn;
  private Instant aplicadoEn;

  private CambioFecha(Datos d) {
    id = d.id(); tipo = d.tipo(); estado = d.estado(); propuestoPorUsuarioId = d.propuestoPorUsuarioId(); fechaAnteriorEn = d.fechaAnteriorEn();
    fechaPropuestaEn = d.fechaPropuestaEn(); costoAdicional = d.costoAdicional(); moneda = d.moneda(); propuestoEn = d.propuestoEn();
    respondidoPorUsuarioId = d.respondidoPorUsuarioId(); respondidoEn = d.respondidoEn(); motivoRechazo = d.motivoRechazo();
    transaccionPagoId = d.transaccionPagoId(); pagoConfirmadoEn = d.pagoConfirmadoEn(); fechaResultanteEn = d.fechaResultanteEn(); aplicadoEn = d.aplicadoEn();
  }

  public static CambioFecha rehidratar(Datos datos) { return new CambioFecha(datos); }

  static CambioFecha extension(UUID id, UUID prestatario, LocalDateTime anterior, LocalDateTime propuesta, BigDecimal costo, String moneda, Instant now) {
    return new CambioFecha(new Datos(id, TipoCambioFecha.EXTENSION, EstadoCambioFecha.PENDIENTE, prestatario, anterior, propuesta, costo, moneda, now,
        null, null, null, null, null, null, null));
  }

  static CambioFecha reprogramacion(UUID id, UUID prestamista, LocalDateTime anterior, LocalDateTime propuesta, Instant now) {
    return new CambioFecha(new Datos(id, TipoCambioFecha.REPROGRAMACION, EstadoCambioFecha.PENDIENTE, prestamista, anterior, propuesta, null, null, now,
        null, null, null, null, null, null, null));
  }

  void exigirPendiente() { if (estado != EstadoCambioFecha.PENDIENTE) throw new ReglaDeNegocioException("Cambio ya respondido"); }

  void rechazar(UUID actor, String motivo, Instant now) {
    exigirPendiente();
    estado = EstadoCambioFecha.RECHAZADA; respondidoPorUsuarioId = actor; respondidoEn = now;
    motivoRechazo = motivo == null || motivo.isBlank() ? null : motivo.trim();
  }

  void aceptarPendienteDePago(UUID actor, Instant now) {
    exigirPendiente();
    estado = EstadoCambioFecha.ACEPTADA_PENDIENTE_PAGO; respondidoPorUsuarioId = actor; respondidoEn = now;
  }

  void esperarRetencion(UUID actor, Instant now) {
    exigirPendiente();
    estado = EstadoCambioFecha.ACEPTADA_PENDIENTE_RETENCION;
    respondidoPorUsuarioId = actor;
    respondidoEn = now;
  }

  void habilitarPago() { estado = EstadoCambioFecha.ACEPTADA_PENDIENTE_PAGO; }

  void esperarOcupacion(UUID transaccion, Instant pagadoEn) {
    estado = EstadoCambioFecha.PAGADA_PENDIENTE_OCUPACION;
    transaccionPagoId = transaccion;
    pagoConfirmadoEn = pagadoEn;
  }

  void rechazarRetencion(String motivo) {
    estado = EstadoCambioFecha.RECHAZADA;
    motivoRechazo = motivo;
  }

  void aceptarSinCosto(UUID actor, Instant now) {
    exigirPendiente();
    respondidoPorUsuarioId = actor; respondidoEn = now;
    aplicar(now);
  }

  void pagar(UUID transaccionId, Instant pagadoEn, Instant now) {
    transaccionPagoId = transaccionId; pagoConfirmadoEn = pagadoEn;
    aplicar(now);
  }

  private void aplicar(Instant now) { estado = EstadoCambioFecha.APLICADA; fechaResultanteEn = fechaPropuestaEn; aplicadoEn = now; }

  public Datos datos() {
    return new Datos(id, tipo, estado, propuestoPorUsuarioId, fechaAnteriorEn, fechaPropuestaEn, costoAdicional, moneda, propuestoEn, respondidoPorUsuarioId,
        respondidoEn, motivoRechazo, transaccionPagoId, pagoConfirmadoEn, fechaResultanteEn, aplicadoEn);
  }

  public UUID id() { return id; }
  public TipoCambioFecha tipo() { return tipo; }
  public EstadoCambioFecha estado() { return estado; }
  public LocalDateTime fechaPropuestaEn() { return fechaPropuestaEn; }
  public BigDecimal costoAdicional() { return costoAdicional; }
  public String moneda() { return moneda; }
}
