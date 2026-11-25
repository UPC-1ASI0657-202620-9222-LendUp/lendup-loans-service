package com.lendup.prestamos.infrastructure.persistence.repository;

import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.infrastructure.persistence.entity.PrestamoEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PrestamoJpaRepository extends JpaRepository<PrestamoEntity, UUID> {
  @Query("select p from PrestamoEntity p where p.reservaId=:reservaId")
  Optional<PrestamoEntity> findByReserva(@Param("reservaId") UUID reservaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PrestamoEntity p where p.id=:id")
  Optional<PrestamoEntity> lockById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PrestamoEntity p where p.reservaId=:reservaId")
  Optional<PrestamoEntity> lockByReserva(@Param("reservaId") UUID reservaId);

  @Query("select p from PrestamoEntity p where (p.prestatarioUsuarioId=:usuario or p.prestamistaUsuarioId=:usuario) and p.estado in :estados order by p.creadoEn desc")
  List<PrestamoEntity> findByParticipante(@Param("usuario") UUID usuario, @Param("estados") Collection<EstadoPrestamo> estados);

  @Query("select p.id from PrestamoEntity p where p.estado=com.lendup.prestamos.domain.model.EstadoPrestamo.ACTIVO and p.devolucionVigenteEn<=:ahora order by p.devolucionVigenteEn")
  List<UUID> findActivosVencidos(@Param("ahora") LocalDateTime ahora, Pageable page);

  @Query("select count(p) from PrestamoEntity p where p.publicacionId=:publicacion and p.id<>:prestamo and p.estado not in :cerrados and p.entregaProgramadaEn<:hasta and p.devolucionVigenteEn>:desde")
  long contarOtrosEnPeriodo(@Param("publicacion") UUID publicacion, @Param("prestamo") UUID prestamo, @Param("cerrados") Collection<EstadoPrestamo> cerrados,
                            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
