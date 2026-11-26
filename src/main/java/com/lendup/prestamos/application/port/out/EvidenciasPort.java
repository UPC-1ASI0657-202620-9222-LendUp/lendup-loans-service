package com.lendup.prestamos.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Evidencias del préstamo para mostrarlas en su detalle (US19, US24). Pertenecen a Evidencias e Incidencias: si ese
 * servicio no está configurado o no responde se devuelve una lista vacía, sin bloquear el préstamo (CRN-04).
 */
public interface EvidenciasPort {
  List<Map<String, Object>> evidenciasDelPrestamo(UUID prestamoId, String bearerToken);

  default Optional<List<UUID>> sellarReportes(UUID prestamoId) { return Optional.empty(); }
}
