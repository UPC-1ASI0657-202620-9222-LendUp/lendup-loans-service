package com.lendup.prestamos.infrastructure.persistence;

import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.domain.model.*;
import com.lendup.prestamos.infrastructure.persistence.entity.*;
import com.lendup.prestamos.infrastructure.persistence.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class JpaPrestamoRepositoryAdapter implements PrestamoRepository {
  private static final Set<EstadoPrestamo> CERRADOS = EnumSet.of(EstadoPrestamo.FINALIZADO, EstadoPrestamo.CANCELADO);
  private final PrestamoJpaRepository prestamos;
  private final CambioFechaJpaRepository cambios;

  public JpaPrestamoRepositoryAdapter(PrestamoJpaRepository prestamos, CambioFechaJpaRepository cambios) { this.prestamos = prestamos; this.cambios = cambios; }

  @Override public Optional<Prestamo> findById(UUID id) { return prestamos.findById(id).map(this::toDomain); }
  @Override public Optional<Prestamo> findByReserva(UUID reservaId) { return prestamos.findByReserva(reservaId).map(this::toDomain); }
  @Override public Optional<Prestamo> lock(UUID id) { return prestamos.lockById(id).map(this::toDomain); }
  @Override public Optional<Prestamo> lockByReserva(UUID reservaId) { return prestamos.lockByReserva(reservaId).map(this::toDomain); }

  @Override
  public List<Prestamo> findByParticipante(UUID usuarioId, Set<EstadoPrestamo> estados) {
    List<PrestamoEntity> filas = prestamos.findByParticipante(usuarioId, estados);
    if (filas.isEmpty()) return List.of();
    Map<UUID, List<CambioFecha>> porPrestamo = cambios.findByPrestamos(filas.stream().map(PrestamoEntity::getId).toList()).stream()
        .collect(Collectors.groupingBy(CambioFechaEntity::getPrestamoId, Collectors.mapping(CambioFechaEntity::toDomain, Collectors.toList())));
    return filas.stream().map(e -> Prestamo.rehidratar(e.toDatos(), porPrestamo.getOrDefault(e.getId(), List.of()))).toList();
  }

  @Override
  public List<UUID> findActivosVencidos(LocalDateTime ahoraLocal, int limite) { return prestamos.findActivosVencidos(ahoraLocal, PageRequest.of(0, limite)); }

  @Override
  public boolean existeOtroEnPeriodo(UUID publicacionId, UUID prestamoId, LocalDateTime desde, LocalDateTime hasta) {
    return prestamos.contarOtrosEnPeriodo(publicacionId, prestamoId, CERRADOS, desde, hasta) > 0;
  }

  @Override
  public void save(Prestamo prestamo) {
    Prestamo.Datos datos = prestamo.datos();
    PrestamoEntity fila = prestamos.findById(datos.id()).map(e -> { e.copy(datos); return e; }).orElseGet(() -> PrestamoEntity.from(datos));
    prestamos.saveAndFlush(fila);
    for (CambioFecha cambio : prestamo.cambios()) {
      CambioFecha.Datos c = cambio.datos();
      cambios.save(cambios.findById(c.id()).map(e -> { e.copy(c); return e; }).orElseGet(() -> CambioFechaEntity.from(datos.id(), c)));
    }
    cambios.flush();
  }

  private Prestamo toDomain(PrestamoEntity e) {
    return Prestamo.rehidratar(e.toDatos(), cambios.findByPrestamos(List.of(e.getId())).stream().map(CambioFechaEntity::toDomain).toList());
  }
}
