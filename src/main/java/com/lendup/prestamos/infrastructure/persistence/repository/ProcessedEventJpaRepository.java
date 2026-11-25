package com.lendup.prestamos.infrastructure.persistence.repository;
import com.lendup.prestamos.infrastructure.persistence.entity.ProcessedEventEntity; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity,UUID>{}
