package com.lendup.prestamos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Routing keys de otros contextos: Evidencias todavía no publica sus eventos y Reputación declara la suya como
 * provisional. Con la de Reputación vacía no se envía su copia de PrestamoFinalizado (docs/events.md).
 */
@ConfigurationProperties(prefix = "lendup.events")
public record EventRoutingProperties(String incidentReportedRoutingKey, String incidentResolvedRoutingKey, String reputationLoanFinishedRoutingKey) {
  public EventRoutingProperties {
    if (incidentReportedRoutingKey == null || incidentReportedRoutingKey.isBlank()) incidentReportedRoutingKey = "evidencias.incidencia-reportada.v1";
    if (incidentResolvedRoutingKey == null || incidentResolvedRoutingKey.isBlank()) incidentResolvedRoutingKey = "evidencias.incidencia-resuelta.v1";
    if (reputationLoanFinishedRoutingKey == null) reputationLoanFinishedRoutingKey = "loans.loan-finalized.v1";
  }

  public static EventRoutingProperties defaults() { return new EventRoutingProperties(null, null, null); }

  public boolean reputationEnabled() { return !reputationLoanFinishedRoutingKey.isBlank(); }
}
