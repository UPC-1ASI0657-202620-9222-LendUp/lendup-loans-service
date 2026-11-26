package com.lendup.prestamos.application.port.out;

import java.util.List;
import java.util.UUID;

public interface LoanIncidentProjection {
  int project(UUID loan, UUID incident, boolean resolved);
  List<UUID> resolved(UUID loan);
}
