package com.lendup.prestamos.domain.event;

import java.util.Map;

/** Hecho de dominio que el agregado registra; la capa de aplicación lo guarda en el outbox con su envelope. */
public record PrestamoEvento(String tipo, long aggregateVersion, Map<String, Object> payload) {
  public static final String CREADO = "PrestamoCreado";
  public static final String OBJETO_ENTREGADO = "ObjetoEntregado";
  public static final String ACTIVADO = "PrestamoActivado";
  public static final String EXTENSION_SOLICITADA = "ExtensionSolicitada";
  public static final String EXTENSION_ACEPTADA = "ExtensionAceptada";
  public static final String EXTENSION_RECHAZADA = "ExtensionRechazada";
  public static final String REPROGRAMACION_PROPUESTA = "ReprogramacionPropuesta";
  public static final String REPROGRAMACION_RECHAZADA = "ReprogramacionRechazada";
  public static final String FECHA_DEVOLUCION_ACTUALIZADA = "FechaDevolucionActualizada";
  public static final String DEVOLUCION_REGISTRADA = "DevolucionRegistrada";
  public static final String VENCIDO = "PrestamoVencido";
  public static final String FINALIZADO = "PrestamoFinalizado";
  public static final String CANCELADO = "PrestamoCancelado";
  public static final String CANCELACION_RECHAZADA = "CancelacionRechazada";
  public static final String PERIODO_LIBERADO = "PeriodoPrestamoLiberado";
}
