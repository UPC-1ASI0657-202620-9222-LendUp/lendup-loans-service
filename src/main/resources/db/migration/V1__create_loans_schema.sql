-- Esquema propio de Préstamos (lendup_prestamos, Fig. 9). Sin claves foráneas hacia tablas de otros servicios:
-- reserva_id, publicacion_id, usuarios y transaccion_pago_id son referencias externas por identificador.
-- Las fechas pactadas (entrega_programada_en, devolucion_original_en, devolucion_vigente_en y fecha_*_en) son hora
-- de negocio America/Lima sin offset, como en Reservas. El resto de columnas *_en son instantes UTC.

CREATE TABLE prestamos (
  id BINARY(16) NOT NULL,
  reserva_id BINARY(16) NOT NULL,
  publicacion_id BINARY(16) NOT NULL,
  prestamista_usuario_id BINARY(16) NOT NULL,
  prestatario_usuario_id BINARY(16) NOT NULL,
  estado VARCHAR(32) NOT NULL DEFAULT 'RESERVADO',
  entrega_programada_en DATETIME(6) NOT NULL,
  devolucion_original_en DATETIME(6) NOT NULL,
  devolucion_vigente_en DATETIME(6) NOT NULL,
  tarifa_diaria_acordada DECIMAL(12,2) NOT NULL,
  garantia_monetaria_acordada DECIMAL(12,2) NULL,
  moneda CHAR(3) NOT NULL,
  garantia_requerida BOOLEAN NOT NULL,
  pago_tarifa_confirmado_en DATETIME(6) NULL,
  garantia_constituida_en DATETIME(6) NULL,
  entrega_registrada_en DATETIME(6) NULL,
  recepcion_confirmada_en DATETIME(6) NULL,
  activado_en DATETIME(6) NULL,
  devolucion_registrada_en DATETIME(6) NULL,
  devolucion_confirmada_en DATETIME(6) NULL,
  incidencias_pendientes INT NOT NULL DEFAULT 0,
  vencido_en DATETIME(6) NULL,
  finalizado_en DATETIME(6) NULL,
  cancelado_en DATETIME(6) NULL,
  creado_en DATETIME(6) NOT NULL,
  actualizado_en DATETIME(6) NOT NULL,
  version BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_prestamos_reserva_id (reserva_id),
  INDEX idx_prestamos_0 (prestatario_usuario_id, estado),
  INDEX idx_prestamos_1 (prestamista_usuario_id, estado),
  INDEX idx_prestamos_2 (estado, devolucion_vigente_en),
  INDEX idx_prestamos_3 (publicacion_id, estado, entrega_programada_en),
  CONSTRAINT ck_prestamos_estado CHECK (estado IN ('RESERVADO','ENTREGA_REGISTRADA','ACTIVO','VENCIDO','DEVOLUCION_REGISTRADA','PENDIENTE_INCIDENCIA','FINALIZADO','CANCELADO')),
  CONSTRAINT ck_prestamos_periodo CHECK (entrega_programada_en < devolucion_original_en),
  CONSTRAINT ck_prestamos_tarifa CHECK (tarifa_diaria_acordada > 0),
  CONSTRAINT ck_prestamos_incidencias CHECK (incidencias_pendientes >= 0),
  CONSTRAINT ck_prestamos_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cambios_fecha_prestamo (
  id BINARY(16) NOT NULL,
  prestamo_id BINARY(16) NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
  propuesto_por_usuario_id BINARY(16) NOT NULL,
  fecha_anterior_en DATETIME(6) NOT NULL,
  fecha_propuesta_en DATETIME(6) NOT NULL,
  costo_adicional DECIMAL(12,2) NULL,
  moneda CHAR(3) NULL,
  propuesto_en DATETIME(6) NOT NULL,
  respondido_por_usuario_id BINARY(16) NULL,
  respondido_en DATETIME(6) NULL,
  motivo_rechazo VARCHAR(1000) NULL,
  transaccion_pago_id BINARY(16) NULL,
  pago_confirmado_en DATETIME(6) NULL,
  fecha_resultante_en DATETIME(6) NULL,
  aplicado_en DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_cambios_fecha_prestamo_0 (prestamo_id, propuesto_en),
  CONSTRAINT fk_cambios_fecha_prestamo_prestamo_id FOREIGN KEY (prestamo_id) REFERENCES prestamos (id) ON DELETE RESTRICT,
  CONSTRAINT ck_cambios_tipo CHECK (tipo IN ('EXTENSION','REPROGRAMACION')),
  CONSTRAINT ck_cambios_estado CHECK (estado IN ('PENDIENTE','RECHAZADA','ACEPTADA_PENDIENTE_PAGO','APLICADA')),
  CONSTRAINT ck_cambios_costo CHECK ((tipo = 'EXTENSION' AND costo_adicional > 0 AND moneda IS NOT NULL)
    OR (tipo = 'REPROGRAMACION' AND costo_adicional IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tablas técnicas con los mismos nombres y columnas que lendup-reservations-service y lendup-payments-service.
CREATE TABLE outbox_events (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  event_version INT NOT NULL,
  aggregate_version BIGINT NOT NULL,
  payload JSON NOT NULL,
  correlation_id VARCHAR(100) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  next_attempt_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_outbox_pending (published_at, attempts, occurred_at),
  INDEX idx_outbox_aggregate (aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processed_events (
  event_id BINARY(16) NOT NULL,
  consumer VARCHAR(100) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  processed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (event_id),
  INDEX idx_processed_aggregate (aggregate_id, aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Solo inserción (RNF21): la entidad JPA es inmutable y el usuario de producción solo recibe INSERT y SELECT
-- sobre esta tabla (ver docs/aws-handoff.md).
CREATE TABLE registro_auditoria (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  action VARCHAR(100) NOT NULL,
  previous_state VARCHAR(50) NULL,
  new_state VARCHAR(50) NOT NULL,
  actor_id BINARY(16) NULL,
  correlation_id VARCHAR(100) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_audit_aggregate (aggregate_type, aggregate_id, occurred_at),
  INDEX idx_audit_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
