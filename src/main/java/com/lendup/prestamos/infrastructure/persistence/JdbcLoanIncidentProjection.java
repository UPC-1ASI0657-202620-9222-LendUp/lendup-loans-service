package com.lendup.prestamos.infrastructure.persistence;

import com.lendup.prestamos.application.port.out.LoanIncidentProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoanIncidentProjection implements LoanIncidentProjection {
  private final JdbcClient db;

  public JdbcLoanIncidentProjection(JdbcClient db) { this.db = db; }

  /** El bloqueo del préstamo protege esta transición incluso con event_id distintos. */
  @Override public int project(UUID loan, UUID incident, boolean resolved) {
    var previous = db.sql("SELECT resolved FROM loan_incident_projection WHERE loan_id = ? AND incident_id = ?")
        .params(loan.toString(), incident.toString()).query(Boolean.class).optional();
    if (previous.isEmpty()) {
      db.sql("INSERT INTO loan_incident_projection VALUES (?, ?, ?)")
          .params(loan.toString(), incident.toString(), resolved).update();
      return resolved ? 0 : 1;
    }
    if (!resolved || previous.get()) return 0;
    db.sql("UPDATE loan_incident_projection SET resolved = true WHERE loan_id = ? AND incident_id = ?")
        .params(loan.toString(), incident.toString()).update();
    return -1;
  }

  @Override public List<UUID> resolved(UUID loan) {
    return db.sql("SELECT incident_id FROM loan_incident_projection WHERE loan_id = ? AND resolved = true ORDER BY incident_id")
        .param(loan.toString()).query((row, n) -> UUID.fromString(row.getString(1))).list();
  }
}
