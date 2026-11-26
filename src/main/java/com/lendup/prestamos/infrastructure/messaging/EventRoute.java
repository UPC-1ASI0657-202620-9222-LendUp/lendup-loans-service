package com.lendup.prestamos.infrastructure.messaging;

import com.lendup.prestamos.domain.event.PrestamoEvento;
import java.util.Arrays;

/**
 * Routing key de cada evento publicado y si otro servicio depende de él para avanzar. Con consumidor obligatorio, un
 * evento que ninguna cola recibe queda pendiente y se reintenta; los informativos (avisos de Notificaciones) se dan por
 * publicados, para que Préstamos pueda funcionar sin ese servicio.
 */
public enum EventRoute {
  CREADO(PrestamoEvento.CREADO, "prestamos.creado.v1", true),
  OBJETO_ENTREGADO(PrestamoEvento.OBJETO_ENTREGADO, "prestamos.objeto-entregado.v1", false),
  ACTIVADO(PrestamoEvento.ACTIVADO, "prestamos.activado.v1", true),
  EXTENSION_SOLICITADA(PrestamoEvento.EXTENSION_SOLICITADA, "prestamos.extension-solicitada.v1", false),
  EXTENSION_ACEPTADA(PrestamoEvento.EXTENSION_ACEPTADA, "prestamos.extension-aceptada.v1", true),
  EXTENSION_RETENCION_SOLICITADA(PrestamoEvento.EXTENSION_RETENCION_SOLICITADA, "prestamos.extension-retencion-solicitada.v1", true),
  EXTENSION_CONFIRMACION_SOLICITADA(PrestamoEvento.EXTENSION_CONFIRMACION_SOLICITADA, "prestamos.extension-confirmacion-solicitada.v1", true),
  EXTENSION_RETENCION_CANCELADA(PrestamoEvento.EXTENSION_RETENCION_CANCELADA, "prestamos.extension-retencion-cancelada.v1", true),
  EXTENSION_RECHAZADA(PrestamoEvento.EXTENSION_RECHAZADA, "prestamos.extension-rechazada.v1", false),
  REPROGRAMACION_PROPUESTA(PrestamoEvento.REPROGRAMACION_PROPUESTA, "prestamos.reprogramacion-propuesta.v1", false),
  REPROGRAMACION_RECHAZADA(PrestamoEvento.REPROGRAMACION_RECHAZADA, "prestamos.reprogramacion-rechazada.v1", false),
  FECHA_DEVOLUCION_ACTUALIZADA(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA, "prestamos.fecha-devolucion-actualizada.v1", false),
  DEVOLUCION_REGISTRADA(PrestamoEvento.DEVOLUCION_REGISTRADA, "prestamos.devolucion-registrada.v1", false),
  VENCIDO(PrestamoEvento.VENCIDO, "prestamos.vencido.v1", false),
  FINALIZADO(PrestamoEvento.FINALIZADO, "prestamos.finalizado.v1", true),
  CANCELADO(PrestamoEvento.CANCELADO, "prestamos.cancelado.v1", true),
  CANCELACION_RECHAZADA(PrestamoEvento.CANCELACION_RECHAZADA, "prestamos.cancelacion-rechazada.v1", true),
  RECUPERACION_PENDIENTE(PrestamoEvento.RECUPERACION_PENDIENTE, "prestamos.recuperacion-pendiente.v1", false),
  PERIODO_LIBERADO(PrestamoEvento.PERIODO_LIBERADO, "prestamos.periodo-liberado.v1", true);

  private final String eventType;
  private final String routingKey;
  private final boolean subscriberRequired;

  EventRoute(String eventType, String routingKey, boolean subscriberRequired) {
    this.eventType = eventType; this.routingKey = routingKey; this.subscriberRequired = subscriberRequired;
  }

  public String eventType() { return eventType; }
  public String routingKey() { return routingKey; }
  public boolean subscriberRequired() { return subscriberRequired; }

  public static EventRoute of(String eventType) {
    return Arrays.stream(values()).filter(r -> r.eventType.equals(eventType)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Tipo de evento Outbox no soportado: " + eventType));
  }
}
