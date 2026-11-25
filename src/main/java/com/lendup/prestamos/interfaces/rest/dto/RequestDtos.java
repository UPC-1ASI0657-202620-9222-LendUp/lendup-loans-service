package com.lendup.prestamos.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Cuerpos de solicitud con el mismo contrato que el monolito (snake_case). */
public final class RequestDtos {
  private RequestDtos() {}

  @Schema(name = "ExtensionRequest")
  public record ExtensionRequest(
      @Schema(example = "2026-11-09T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String fecha_propuesta_en,
      @Schema(description = "Costo que se mostró al usuario. Si no coincide con el calculado por Préstamos se responde 409", example = "20.00") @Positive @Digits(integer = 10, fraction = 2) BigDecimal costo_adicional,
      @Schema(example = "PEN") String moneda) {}

  @Schema(name = "ExtensionQuoteRequest")
  public record ExtensionQuoteRequest(@Schema(description = "Opcional: con ella se calcula costo_adicional", example = "2026-11-09T18:00:00") String fecha_propuesta_en) {}

  @Schema(name = "RescheduleRequest")
  public record RescheduleRequest(@Schema(example = "2026-11-09T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String fecha_propuesta_en) {}

  @Schema(name = "ExtensionResponseRequest")
  public record ExtensionResponseRequest(
      @Schema(allowableValues = {"RECHAZADA", "ACEPTADA_PENDIENTE_PAGO"}, requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String estado,
      @Size(max = 1000) String motivo_rechazo) {}

  @Schema(name = "RescheduleResponseRequest")
  public record RescheduleResponseRequest(
      @Schema(allowableValues = {"RECHAZADA", "APLICADA"}, requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String estado,
      @Size(max = 1000) String motivo_rechazo) {}
}
