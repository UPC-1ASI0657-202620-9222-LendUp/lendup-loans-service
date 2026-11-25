package com.lendup.prestamos.infrastructure.persistence.repository;

import com.lendup.prestamos.infrastructure.persistence.entity.CambioFechaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CambioFechaJpaRepository extends JpaRepository<CambioFechaEntity, UUID> {
  @Query("select c from CambioFechaEntity c where c.prestamoId in :prestamos order by c.propuestoEn, c.id")
  List<CambioFechaEntity> findByPrestamos(@Param("prestamos") Collection<UUID> prestamos);
}
