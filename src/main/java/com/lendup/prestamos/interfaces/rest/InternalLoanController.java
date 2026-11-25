package com.lendup.prestamos.interfaces.rest;

import com.lendup.prestamos.application.port.in.LoanQueryUseCases;
import com.lendup.prestamos.interfaces.rest.dto.ResponseDtos.ContactAccessResponse;
import io.swagger.v3.oas.annotations.Hidden;
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
}
