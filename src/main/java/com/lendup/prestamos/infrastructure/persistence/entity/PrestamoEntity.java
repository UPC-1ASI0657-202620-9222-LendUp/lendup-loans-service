package com.lendup.prestamos.infrastructure.persistence.entity;

import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.domain.model.Prestamo;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prestamos")
public class PrestamoEntity {
  @Id private UUID id;
  @Column(name = "reserva_id", nullable = false) private UUID reservaId;
  @Column(name = "publicacion_id", nullable = false) private UUID publicacionId;
  @Column(name = "prestamista_usuario_id", nullable = false) private UUID prestamistaUsuarioId;
  @Column(name = "prestatario_usuario_id", nullable = false) private UUID prestatarioUsuarioId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private EstadoPrestamo estado;
  @Column(name = "entrega_programada_en", nullable = false) private LocalDateTime entregaProgramadaEn;
  @Column(name = "devolucion_original_en", nullable = false) private LocalDateTime devolucionOriginalEn;
  @Column(name = "devolucion_vigente_en", nullable = false) private LocalDateTime devolucionVigenteEn;
  @Column(name = "tarifa_diaria_acordada", nullable = false) private BigDecimal tarifaDiariaAcordada;
  @Column(name = "garantia_monetaria_acordada") private BigDecimal garantiaMonetariaAcordada;
  @Column(nullable = false, length = 3) private String moneda;
  @Column(name = "garantia_requerida", nullable = false) private boolean garantiaRequerida;
  @Column(name = "pago_tarifa_confirmado_en") private Instant pagoTarifaConfirmadoEn;
  @Column(name = "garantia_constituida_en") private Instant garantiaConstituidaEn;
  @Column(name = "entrega_registrada_en") private Instant entregaRegistradaEn;
  @Column(name = "recepcion_confirmada_en") private Instant recepcionConfirmadaEn;
  @Column(name = "activado_en") private Instant activadoEn;
  @Column(name = "devolucion_registrada_en") private Instant devolucionRegistradaEn;
  @Column(name = "devolucion_confirmada_en") private Instant devolucionConfirmadaEn;
  @Column(name = "incidencias_pendientes", nullable = false) private int incidenciasPendientes;
  @Column(name = "vencido_en") private Instant vencidoEn;
  @Column(name = "finalizado_en") private Instant finalizadoEn;
  @Column(name = "cancelado_en") private Instant canceladoEn;
  @Column(name = "creado_en", nullable = false) private Instant creadoEn;
  @Column(name = "actualizado_en", nullable = false) private Instant actualizadoEn;
  /** Versión del agregado (aggregate_version de los eventos). La concurrencia se controla con bloqueo de fila. */
  @Column(nullable = false) private long version;

  protected PrestamoEntity() {}

  public static PrestamoEntity from(Prestamo.Datos d) {
    PrestamoEntity e = new PrestamoEntity();
    e.id = d.id(); e.reservaId = d.reservaId(); e.publicacionId = d.publicacionId();
    e.prestamistaUsuarioId = d.prestamistaUsuarioId(); e.prestatarioUsuarioId = d.prestatarioUsuarioId();
    e.entregaProgramadaEn = d.entregaProgramadaEn(); e.devolucionOriginalEn = d.devolucionOriginalEn();
    e.tarifaDiariaAcordada = d.tarifaDiariaAcordada(); e.garantiaMonetariaAcordada = d.garantiaMonetariaAcordada();
    e.moneda = d.moneda(); e.garantiaRequerida = d.garantiaRequerida(); e.creadoEn = d.creadoEn();
    e.copy(d);
    return e;
  }

  /** Solo lo que puede cambiar: las condiciones acordadas en la reserva son inmutables (R27). */
  public void copy(Prestamo.Datos d) {
    estado = d.estado(); devolucionVigenteEn = d.devolucionVigenteEn(); pagoTarifaConfirmadoEn = d.pagoTarifaConfirmadoEn();
    garantiaConstituidaEn = d.garantiaConstituidaEn(); entregaRegistradaEn = d.entregaRegistradaEn(); recepcionConfirmadaEn = d.recepcionConfirmadaEn();
    activadoEn = d.activadoEn(); devolucionRegistradaEn = d.devolucionRegistradaEn(); devolucionConfirmadaEn = d.devolucionConfirmadaEn();
    incidenciasPendientes = d.incidenciasPendientes(); vencidoEn = d.vencidoEn(); finalizadoEn = d.finalizadoEn(); canceladoEn = d.canceladoEn();
    actualizadoEn = d.actualizadoEn(); version = d.version();
  }

  public Prestamo.Datos toDatos() {
    return new Prestamo.Datos(id, reservaId, publicacionId, prestamistaUsuarioId, prestatarioUsuarioId, estado, entregaProgramadaEn, devolucionOriginalEn,
        devolucionVigenteEn, tarifaDiariaAcordada, garantiaMonetariaAcordada, moneda, pagoTarifaConfirmadoEn, garantiaConstituidaEn, entregaRegistradaEn,
        recepcionConfirmadaEn, activadoEn, devolucionRegistradaEn, devolucionConfirmadaEn, incidenciasPendientes, vencidoEn, finalizadoEn, canceladoEn, creadoEn,
        actualizadoEn, version);
  }

  public UUID getId() { return id; }
}
