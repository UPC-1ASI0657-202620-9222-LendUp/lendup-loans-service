package com.lendup.prestamos.infrastructure.client;

import com.lendup.prestamos.application.port.out.EvidenciasPort;
import com.lendup.prestamos.application.exception.DependencyUnavailableException;
import com.lendup.prestamos.config.IntegrationProperties;
import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Consulta Evidencias y coordina el sellado antes de cerrar un préstamo.
 */
@Component
public class EvidenciasRestAdapter implements EvidenciasPort {
  private static final Logger log = LoggerFactory.getLogger(EvidenciasRestAdapter.class);
  private final IntegrationProperties.Evidence config;
  private final RestClient client;

  public EvidenciasRestAdapter(IntegrationProperties properties) {
    config = properties.evidence();
    client = config.baseUrl().isBlank() ? null
        : RestClientFactory.create(config.baseUrl(), config.connectTimeoutMs(), config.readTimeoutMs());
    if (config.coordinateClosure() && (client == null || config.loanEvidenceUriTemplate().isBlank()
        || !config.serviceToken().matches("[A-Za-z0-9_-]{32,}"))) {
      throw new IllegalStateException("Configura la URL y la credencial de Evidencias para coordinar el cierre");
    }
  }

  @Override
  public List<Map<String, Object>> evidenciasDelPrestamo(UUID prestamoId, String bearerToken) {
    if (client == null || config.loanEvidenceUriTemplate().isBlank() || bearerToken == null || bearerToken.isBlank()) return List.of();
    try {
      List<Map<String, Object>> rows = client.get().uri(config.loanEvidenceUriTemplate(), prestamoId).header("Authorization", "Bearer " + bearerToken)
          .retrieve().body(new ParameterizedTypeReference<>() {});
      return rows == null ? List.of() : rows;
    } catch (Exception e) {
      if (config.coordinateClosure()) throw new DependencyUnavailableException("No se pudieron consultar las evidencias");
      log.warn("Evidencias no disponible para el préstamo {}: {}", prestamoId, e.getMessage());
      return List.of();
    }
  }

  @Override
  public Optional<List<UUID>> sellarReportes(UUID prestamoId) {
    if (!config.coordinateClosure()) return Optional.empty();
    try {
      List<UUID> ids = client.post().uri("/internal/v1/prestamos/{id}/cierre-incidencias", prestamoId)
          .header("Authorization", "Bearer " + config.serviceToken())
          .retrieve().body(new ParameterizedTypeReference<>() {});
      if (ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) {
        throw new DependencyUnavailableException("Evidencias devolvió un cierre inválido");
      }
      return Optional.of(ids);
    } catch (HttpClientErrorException.Conflict e) {
      throw new ReglaDeNegocioException("Quedan incidencias abiertas");
    } catch (RestClientException e) {
      throw new DependencyUnavailableException("No se pudo coordinar el cierre con Evidencias");
    }
  }
}
