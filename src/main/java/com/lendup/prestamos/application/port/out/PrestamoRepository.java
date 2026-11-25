package com.lendup.prestamos.application.port.out;

import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.domain.model.Prestamo;
import java.time.LocalDateTime;
import java.util.*;

/** Prestamo Repository (Fig. 16): recupera y guarda préstamos con su historial de cambios de fecha. */
public interface PrestamoRepository {
  Optional<Prestamo> findById(UUID prestamoId);
  Optional<Prestamo> findByReserva(UUID reservaId);

  /** Bloquea la fila del préstamo hasta el fin de la transacción: las operaciones sobre un préstamo van en serie. */
  Optional<Prestamo> lock(UUID prestamoId);
  Optional<Prestamo> lockByReserva(UUID reservaId);

  List<Prestamo> findByParticipante(UUID usuarioId, Set<EstadoPrestamo> estados);
  List<UUID> findActivosVencidos(LocalDateTime ahoraLocal, int limite);

  /** Otro préstamo en curso del mismo objeto ocupa parte del periodo (desde, hasta). */
  boolean existeOtroEnPeriodo(UUID publicacionId, UUID prestamoId, LocalDateTime desde, LocalDateTime hasta);

  void save(Prestamo prestamo);
}
