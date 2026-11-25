package com.lendup.prestamos.infrastructure.persistence.repository;
import com.lendup.prestamos.infrastructure.persistence.entity.AuditEntity; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditJpaRepository extends JpaRepository<AuditEntity,UUID>{}
