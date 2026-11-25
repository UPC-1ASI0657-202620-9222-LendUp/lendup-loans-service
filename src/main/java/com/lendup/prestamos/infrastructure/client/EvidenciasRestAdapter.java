package com.lendup.prestamos.infrastructure.client;

import com.lendup.prestamos.application.port.out.EvidenciasPort;
import com.lendup.prestamos.config.IntegrationProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Evidencias e Incidencias sigue en el monolito y todavía no publica una ruta de lectura por préstamo: la plantilla
 * es configurable y vacía por defecto. Sin configurar, o si falla, el detalle del préstamo se muestra sin evidencias.
 */
@Component
public class EvidenciasRestAdapter implements EvidenciasPort {
  private static final Logger log = LoggerFactory.getLogger(EvidenciasRestAdapter.class);
  private final IntegrationProperties.Evidence config;
  private final RestClient client;

  public EvidenciasRestAdapter(IntegrationProperties properties) {
    config = properties.evidence();
    client = config.baseUrl().isBlank() || config.loanEvidenceUriTemplate().isBlank() ? null
        : RestClientFactory.create(config.baseUrl(), config.connectTimeoutMs(), config.readTimeoutMs());
  }

  @Override
  public List<Map<String, Object>> evidenciasDelPrestamo(UUID prestamoId, String bearerToken) {
    if (client == null || bearerToken == null || bearerToken.isBlank()) return List.of();
    try {
      List<Map<String, Object>> rows = client.get().uri(config.loanEvidenceUriTemplate(), prestamoId).header("Authorization", "Bearer " + bearerToken)
          .retrieve().body(new ParameterizedTypeReference<>() {});
      return rows == null ? List.of() : rows;
    } catch (Exception e) {
      log.warn("Evidencias no disponible para el préstamo {}: {}", prestamoId, e.getMessage());
      return List.of();
    }
  }
}
