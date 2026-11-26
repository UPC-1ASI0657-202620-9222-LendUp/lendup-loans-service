CREATE TABLE loan_incident_projection (
  loan_id CHAR(36) NOT NULL,
  incident_id CHAR(36) NOT NULL,
  resolved BOOLEAN NOT NULL,
  PRIMARY KEY (loan_id, incident_id)
) ENGINE=InnoDB;
