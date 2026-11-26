package com.lendup.prestamos.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lendup.prestamos.application.port.in.LoanQueryUseCases;
import com.lendup.prestamos.interfaces.rest.dto.ResponseDtos.ContactAccessResponse;
import com.lendup.prestamos.interfaces.rest.dto.ResponseDtos.PrestamoResponse;
import io.swagger.v3.oas.annotations.Hidden;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/**
 * Contrato interno que Reservas ya espera (LOAN_CONTACT_ACCESS_URI_TEMPLATE): decide si el teléfono de la
 * contraparte sigue visible (RF52). Protegido con credencial de servicio; no se publica en el API Gateway.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1/prestamos")
public class InternalLoanController {
  private final LoanQueryUseCases queries;

  public InternalLoanController(LoanQueryUseCases queries) { this.queries = queries; }

  @GetMapping("/reservas/{reservaId}/participantes/{usuarioId}/contacto")
  ContactAccessResponse contactAccess(@PathVariable UUID reservaId, @PathVariable UUID usuarioId) {
    return new ContactAccessResponse(queries.contactoVigente(reservaId, usuarioId));
  }

  record IncidentLoanResponse(UUID id, @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal garantia_monetaria_acordada,
      String moneda, UUID prestamista_usuario_id, UUID prestatario_usuario_id) {}

  @GetMapping("/{id}/incidencias")
  IncidentLoanResponse incidentLoan(@PathVariable UUID id) {
    var datos = queries.datosParaIncidencias(id);
    return new IncidentLoanResponse(datos.id(), datos.garantia(), datos.moneda(), datos.prestamista(), datos.prestatario());
  }

  record ReportAdmissionResponse(boolean admite_reportes) {}

  @GetMapping("/{id}/admision-incidencias")
  ReportAdmissionResponse reportAdmission(@PathVariable UUID id) {
    return new ReportAdmissionResponse(queries.admiteReportes(id));
  }

  @GetMapping("/{id}/participantes/{usuarioId}")
  PrestamoResponse participantLoan(@PathVariable UUID id, @PathVariable UUID usuarioId) {
    return PrestamoResponse.from(queries.datosDelParticipante(usuarioId, id));
  }
}
