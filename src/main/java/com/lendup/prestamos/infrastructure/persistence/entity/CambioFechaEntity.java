package com.lendup.prestamos.infrastructure.persistence.entity;

import com.lendup.prestamos.domain.model.CambioFecha;
import com.lendup.prestamos.domain.model.EstadoCambioFecha;
import com.lendup.prestamos.domain.model.TipoCambioFecha;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cambios_fecha_prestamo")
public class CambioFechaEntity {
  @Id private UUID id;
  @Column(name = "prestamo_id", nullable = false) private UUID prestamoId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoCambioFecha tipo;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoCambioFecha estado;
  @Column(name = "propuesto_por_usuario_id", nullable = false) private UUID propuestoPorUsuarioId;
  @Column(name = "fecha_anterior_en", nullable = false) private LocalDateTime fechaAnteriorEn;
  @Column(name = "fecha_propuesta_en", nullable = false) private LocalDateTime fechaPropuestaEn;
  @Column(name = "costo_adicional") private BigDecimal costoAdicional;
  @Column(length = 3) private String moneda;
  @Column(name = "propuesto_en", nullable = false) private Instant propuestoEn;
  @Column(name = "respondido_por_usuario_id") private UUID respondidoPorUsuarioId;
  @Column(name = "respondido_en") private Instant respondidoEn;
  @Column(name = "motivo_rechazo", length = 1000) private String motivoRechazo;
  @Column(name = "transaccion_pago_id") private UUID transaccionPagoId;
  @Column(name = "pago_confirmado_en") private Instant pagoConfirmadoEn;
  @Column(name = "fecha_resultante_en") private LocalDateTime fechaResultanteEn;
  @Column(name = "aplicado_en") private Instant aplicadoEn;

  protected CambioFechaEntity() {}

  public static CambioFechaEntity from(UUID prestamoId, CambioFecha.Datos d) {
    CambioFechaEntity e = new CambioFechaEntity();
    e.id = d.id(); e.prestamoId = prestamoId; e.tipo = d.tipo(); e.propuestoPorUsuarioId = d.propuestoPorUsuarioId();
    e.fechaAnteriorEn = d.fechaAnteriorEn(); e.fechaPropuestaEn = d.fechaPropuestaEn(); e.costoAdicional = d.costoAdicional();
    e.moneda = d.moneda(); e.propuestoEn = d.propuestoEn();
    e.copy(d);
    return e;
  }

  /** Solo la respuesta y su resultado: la propuesta original no se modifica (RF47). */
  public void copy(CambioFecha.Datos d) {
    estado = d.estado(); respondidoPorUsuarioId = d.respondidoPorUsuarioId(); respondidoEn = d.respondidoEn(); motivoRechazo = d.motivoRechazo();
    transaccionPagoId = d.transaccionPagoId(); pagoConfirmadoEn = d.pagoConfirmadoEn(); fechaResultanteEn = d.fechaResultanteEn(); aplicadoEn = d.aplicadoEn();
  }

  public CambioFecha toDomain() {
    return CambioFecha.rehidratar(new CambioFecha.Datos(id, tipo, estado, propuestoPorUsuarioId, fechaAnteriorEn, fechaPropuestaEn, costoAdicional, moneda, propuestoEn,
        respondidoPorUsuarioId, respondidoEn, motivoRechazo, transaccionPagoId, pagoConfirmadoEn, fechaResultanteEn, aplicadoEn));
  }

  public UUID getId() { return id; }
  public UUID getPrestamoId() { return prestamoId; }
}
