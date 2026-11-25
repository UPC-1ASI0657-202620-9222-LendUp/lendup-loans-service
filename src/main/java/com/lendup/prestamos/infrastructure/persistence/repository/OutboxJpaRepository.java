package com.lendup.prestamos.infrastructure.persistence.repository;

import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
  /**
   * Pendientes listos para publicar, en orden. Un evento espera mientras haya otro anterior del mismo préstamo sin
   * publicar (y con intentos disponibles): así PrestamoActivado nunca sale antes que PrestamoCreado.
   * SKIP LOCKED (lock.timeout=-2): dos réplicas no toman el mismo evento.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query("""
      select e from OutboxEventEntity e
      where e.publishedAt is null and e.attempts<:maxAttempts and (e.nextAttemptAt is null or e.nextAttemptAt<=:now)
        and not exists (select 1 from OutboxEventEntity o where o.aggregateId=e.aggregateId and o.publishedAt is null
                        and o.attempts<:maxAttempts and o.aggregateVersion<e.aggregateVersion)
      order by e.occurredAt, e.aggregateVersion""")
  List<OutboxEventEntity> pending(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable page);
}
