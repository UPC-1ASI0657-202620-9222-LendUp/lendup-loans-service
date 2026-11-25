package com.lendup.prestamos.support;

import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.domain.model.*;
import java.time.LocalDateTime;
import java.util.*;

/** Repositorio en memoria para pruebas: guarda copias, igual que una base de datos. */
public class InMemoryPrestamoRepository implements PrestamoRepository {
  private record Fila(Prestamo.Datos datos, List<CambioFecha.Datos> cambios) {}
  private final Map<UUID, Fila> filas = new LinkedHashMap<>();

  private static Prestamo toDomain(Fila f) { return Prestamo.rehidratar(f.datos(), f.cambios().stream().map(CambioFecha::rehidratar).toList()); }

  @Override public Optional<Prestamo> findById(UUID id) { return Optional.ofNullable(filas.get(id)).map(InMemoryPrestamoRepository::toDomain); }
  @Override public Optional<Prestamo> findByReserva(UUID reservaId) {
    return filas.values().stream().filter(f -> f.datos().reservaId().equals(reservaId)).findFirst().map(InMemoryPrestamoRepository::toDomain);
  }
  @Override public Optional<Prestamo> lock(UUID id) { return findById(id); }
  @Override public Optional<Prestamo> lockByReserva(UUID reservaId) { return findByReserva(reservaId); }

  @Override public List<Prestamo> findByParticipante(UUID usuario, Set<EstadoPrestamo> estados) {
    return filas.values().stream().filter(f -> f.datos().prestamistaUsuarioId().equals(usuario) || f.datos().prestatarioUsuarioId().equals(usuario))
        .filter(f -> estados.contains(f.datos().estado())).map(InMemoryPrestamoRepository::toDomain).toList();
  }

  @Override public List<UUID> findActivosVencidos(LocalDateTime ahora, int limite) {
    return filas.values().stream().map(Fila::datos).filter(d -> d.estado() == EstadoPrestamo.ACTIVO && !d.devolucionVigenteEn().isAfter(ahora)).limit(limite).map(Prestamo.Datos::id).toList();
  }

  @Override public boolean existeOtroEnPeriodo(UUID publicacion, UUID prestamo, LocalDateTime desde, LocalDateTime hasta) {
    return filas.values().stream().map(Fila::datos).anyMatch(d -> d.publicacionId().equals(publicacion) && !d.id().equals(prestamo) && d.estado().abierto()
        && d.entregaProgramadaEn().isBefore(hasta) && d.devolucionVigenteEn().isAfter(desde));
  }

  @Override public void save(Prestamo prestamo) { filas.put(prestamo.id(), new Fila(prestamo.datos(), prestamo.cambios().stream().map(CambioFecha::datos).toList())); }

  public List<Prestamo> todos() { return filas.values().stream().map(InMemoryPrestamoRepository::toDomain).toList(); }
}
