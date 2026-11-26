package com.lendup.prestamos.interfaces.rest;

import com.lendup.prestamos.application.port.in.LoanQueryUseCases;
import com.lendup.prestamos.application.port.in.LoanUseCases;
import com.lendup.prestamos.application.service.BusinessClock;
import com.lendup.prestamos.config.security.ActorContext;
import com.lendup.prestamos.interfaces.rest.dto.RequestDtos.*;
import com.lendup.prestamos.interfaces.rest.dto.ResponseDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Préstamos Controller (Fig. 16): mismas rutas y cuerpos que el monolito para no romper el frontend. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Préstamos")
public class LoanController {
  private final LoanUseCases loans;
  private final LoanQueryUseCases queries;
  private final ActorContext actor;

  public LoanController(LoanUseCases loans, LoanQueryUseCases queries, ActorContext actor) { this.loans = loans; this.queries = queries; this.actor = actor; }

  @PostMapping("/prestamos/{id}/entrega")
  @Operation(summary = "Registrar la entrega", description = "Solo el prestamista, con la tarifa pagada y la garantía constituida si corresponde (RF19).")
  PrestamoResponse delivery(@PathVariable UUID id) { return PrestamoResponse.from(loans.registrarEntrega(actor.userId(), id, correlationId())); }

  @PostMapping("/prestamos/{id}/recepcion")
  @Operation(summary = "Confirmar la recepción", description = "Solo el prestatario. Activa el préstamo (RF20).")
  PrestamoResponse receipt(@PathVariable UUID id) { return PrestamoResponse.from(loans.confirmarRecepcion(actor.userId(), id, correlationId())); }

  @PostMapping("/prestamos/{id}/devolucion")
  @Operation(summary = "Registrar la devolución", description = "Solo el prestatario, con el préstamo activo o vencido (RF26).")
  PrestamoResponse returnLoan(@PathVariable UUID id) { return PrestamoResponse.from(loans.registrarDevolucion(actor.userId(), id, correlationId())); }

  @PostMapping("/prestamos/{id}/confirmacion-devolucion")
  @Operation(summary = "Confirmar la devolución", description = "Solo el prestamista. Finaliza el préstamo si no hay incidencias pendientes (RF27).")
  PrestamoResponse confirmReturn(@PathVariable UUID id) { return PrestamoResponse.from(loans.confirmarDevolucion(actor.userId(), id, correlationId())); }

  @PostMapping("/prestamos/{id}/confirmacion-recuperacion")
  @Operation(summary = "Confirmar recuperación antes de cancelar", description = "Solo el prestamista con recuperación pendiente y sin incidencias abiertas.")
  PrestamoResponse confirmRecovery(@PathVariable UUID id) { return PrestamoResponse.from(loans.confirmarRecuperacion(actor.userId(), id, correlationId())); }

  @PostMapping("/prestamos/{id}/extensiones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Solicitar una extensión", description = "Solo el prestatario, antes del vencimiento. El costo se calcula por bloques iniciados de 24 horas (RF23, R26).")
  CambioFechaResponse extension(@PathVariable UUID id, @Valid @RequestBody ExtensionRequest body) {
    return CambioFechaResponse.from(loans.solicitarExtension(actor.userId(), id, fecha(body.fecha_propuesta_en()), body.costo_adicional(), correlationId()));
  }

  @PostMapping("/prestamos/{id}/extensiones/{subid}/respuesta")
  @Operation(summary = "Responder una extensión", description = "Solo el prestamista. Con coordinación activa, la retención habilita el pago y la ocupación confirmada aplica la nueva fecha.")
  CambioFechaResponse extensionResponse(@PathVariable UUID id, @PathVariable UUID subid, @Valid @RequestBody ExtensionResponseRequest body) {
    return CambioFechaResponse.from(loans.responderExtension(actor.userId(), id, subid, body.estado(), body.motivo_rechazo(), correlationId()));
  }

  @PostMapping("/prestamos/{id}/extensiones/cotizacion")
  @Operation(summary = "Cotizar una extensión", description = "Con fecha_propuesta_en devuelve el costo adicional; sin cuerpo, solo la tarifa diaria.")
  ExtensionQuoteResponse extensionQuote(@PathVariable UUID id, @RequestBody(required = false) ExtensionQuoteRequest body) {
    String propuesta = body == null ? null : body.fecha_propuesta_en();
    return ExtensionQuoteResponse.from(queries.cotizarExtension(actor.userId(), id, propuesta == null || propuesta.isBlank() ? null : fecha(propuesta)));
  }

  @PostMapping("/prestamos/{id}/reprogramaciones")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Proponer una reprogramación", description = "Solo el prestamista, antes del vencimiento y sin costo (RF25).")
  CambioFechaResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest body) {
    return CambioFechaResponse.from(loans.proponerReprogramacion(actor.userId(), id, fecha(body.fecha_propuesta_en()), correlationId()));
  }

  @PostMapping("/prestamos/{id}/reprogramaciones/{subid}/respuesta")
  @Operation(summary = "Responder una reprogramación", description = "Solo el prestatario. Si la acepta, cambia la fecha vigente (RF25).")
  CambioFechaResponse rescheduleResponse(@PathVariable UUID id, @PathVariable UUID subid, @Valid @RequestBody RescheduleResponseRequest body) {
    return CambioFechaResponse.from(loans.responderReprogramacion(actor.userId(), id, subid, body.estado(), body.motivo_rechazo(), correlationId()));
  }

  @GetMapping("/prestamos")
  @Operation(summary = "Listar mis préstamos", description = "Como prestamista o prestatario; filtro opcional por estado (RF21, RF28).")
  List<PrestamoResponse> list(@RequestParam(required = false) String estado) { return queries.listar(actor.userId(), estado).stream().map(PrestamoResponse::from).toList(); }

  @GetMapping("/prestamos/{id}")
  @Operation(summary = "Ver un préstamo", description = "Solo participantes. Incluye los cambios de fecha y las evidencias.")
  PrestamoResponse detail(@PathVariable UUID id) { return PrestamoResponse.from(queries.detalle(actor.userId(), id, actor.bearerToken())); }

  @GetMapping("/prestamos/{id}/cambios-fecha")
  @Operation(summary = "Historial de extensiones y reprogramaciones", description = "Solo participantes (RF47).")
  List<CambioFechaResponse> dateChanges(@PathVariable UUID id) { return queries.cambiosDeFecha(actor.userId(), id).stream().map(CambioFechaResponse::from).toList(); }

  @GetMapping("/prestamos/{id}/cotizacion-pago")
  @Operation(summary = "Cotizar el pago", description = "Tarifa diaria, garantía e importe de la tarifa por bloques de 24 horas.")
  PaymentQuoteResponse paymentQuote(@PathVariable UUID id) { return PaymentQuoteResponse.from(queries.cotizarPago(actor.userId(), id)); }

  @GetMapping("/calendario")
  @Operation(summary = "Calendario de préstamos", description = "Reservas confirmadas y préstamos con sus fechas de entrega y devolución (RF29).")
  List<PrestamoResponse> calendar() { return queries.calendario(actor.userId()).stream().map(PrestamoResponse::from).toList(); }

  private static String correlationId() { return MDC.get("correlation_id"); }

  /** Las fechas pactadas son hora de Lima; si el cliente envía un offset (o Z) se convierte a Lima. */
  private static LocalDateTime fecha(String value) {
    try {
      if (value.endsWith("Z") || value.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
        return OffsetDateTime.parse(value).atZoneSameInstant(BusinessClock.ZONA).toLocalDateTime();
      }
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Fechas inválidas: use yyyy-MM-ddTHH:mm:ss");
    }
  }
}
