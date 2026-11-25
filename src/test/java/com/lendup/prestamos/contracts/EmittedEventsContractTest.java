package com.lendup.prestamos.contracts;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.application.port.in.LoanViews.ExtensionPagadaCommand;
import com.lendup.prestamos.domain.event.EventEnvelope;
import com.lendup.prestamos.infrastructure.messaging.EventRoute;
import com.lendup.prestamos.support.LoansTestFixture;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los eventos que emite el código deben coincidir con contracts/events/v1 (esquema y ejemplos): son los contratos que
 * consumen Reservas, Pagos, Notificaciones y Reputación.
 */
class EmittedEventsContractTest {
  private static final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  private static final Map<String, JsonNode> emitidos = new HashMap<>();
  private static JsonNode schema, ejemplos;

  @BeforeAll static void emitirTodos() throws IOException {
    schema = json.readTree(Files.readString(Path.of("contracts/events/v1/loans-events.schema.json")));
    ejemplos = json.readTree(Files.readString(Path.of("contracts/events/v1/examples.json"))).get("emitted");
    LoansTestFixture f = new LoansTestFixture();
    UUID conExtension = f.activo();
    UUID cambio = f.service.solicitarExtension(f.prestatario, conExtension, LoansTestFixture.HASTA.plusDays(1), null, "c").cambio().id();
    f.service.responderExtension(f.prestamista, conExtension, cambio, "ACEPTADA_PENDIENTE_PAGO", null, "c");
    f.service.registrarExtensionPagada(new ExtensionPagadaCommand(conExtension, cambio, UUID.randomUUID(), f.clock.instant()), "c");
    UUID rechazada = f.service.solicitarExtension(f.prestatario, conExtension, LoansTestFixture.HASTA.plusDays(2), null, "c").cambio().id();
    f.service.responderExtension(f.prestamista, conExtension, rechazada, "RECHAZADA", "Lo necesito", "c");
    UUID reprogramacion = f.service.proponerReprogramacion(f.prestamista, conExtension, LoansTestFixture.HASTA.plusDays(2), "c").cambio().id();
    f.service.responderReprogramacion(f.prestatario, conExtension, reprogramacion, "RECHAZADA", "Viajo ese día", "c");
    f.service.decidirCancelacion(f.prestamo(conExtension).reservaId(), "c");
    f.service.decidirCancelacion(f.prestamo(f.reservado()).reservaId(), "c");
    f.finalizado();
    f.vencido();
    for (EventEnvelope e : f.eventos) emitidos.put(e.eventType(), json.readTree(json.writeValueAsString(e.payload())));
  }

  @ParameterizedTest @EnumSource(EventRoute.class)
  void cadaEventoTieneLosMismosCamposQueSuEjemplo(EventRoute ruta) {
    JsonNode ejemplo = ejemplos.get(ruta.routingKey());
    assertNotNull(ejemplo, "Falta el ejemplo de " + ruta.routingKey());
    assertEquals(ruta.eventType(), ejemplo.get("event_type").asString());
    assertNotNull(emitidos.get(ruta.eventType()), "El código no emitió " + ruta.eventType());
    assertEquals(new TreeSet<>(ejemplo.get("payload").propertyNames()), new TreeSet<>(emitidos.get(ruta.eventType()).propertyNames()));
  }

  @Test void cadaEventoCumpleLosCamposObligatoriosDeSuEsquema() {
    Set<String> revisados = new HashSet<>();
    for (JsonNode regla : schema.get("allOf")) {
      if (!regla.has("if")) continue;
      String tipo = regla.get("if").get("properties").get("event_type").get("const").asString();
      String definicion = regla.get("then").get("properties").get("payload").get("$ref").asString().replace("#/$defs/", "");
      List<JsonNode> obligatorios = new ArrayList<>();
      schema.get("$defs").get("base").get("required").forEach(obligatorios::add);
      schema.get("$defs").get(definicion).get("required").forEach(obligatorios::add);
      for (JsonNode campo : obligatorios) assertTrue(emitidos.get(tipo).hasNonNull(campo.asString()), tipo + " sin " + campo.asString());
      revisados.add(tipo);
    }
    assertEquals(EventRoute.values().length, revisados.size());
  }

  @Test void camposQueExigenLosConsumidoresReales() {
    exige("PrestamoCreado", "prestamo_id", "reserva_id"); // Pagos
    exige("PrestamoActivado", "prestamo_id", "prestatario_usuario_id", "prestamista_usuario_id"); // Pagos y Notificaciones
    exige("ExtensionAceptada", "prestamo_id", "cambio_fecha_prestamo_id", "costo_adicional", "prestatario_usuario_id", "prestamista_usuario_id"); // Pagos y Notificaciones
    exige("ObjetoEntregado", "prestamo_id", "reserva_id", "prestatario_usuario_id"); // Notificaciones
    exige("DevolucionRegistrada", "prestamo_id", "reserva_id", "prestamista_usuario_id"); // Notificaciones y Pagos
    exige("PrestamoVencido", "prestamo_id", "devolucion_vigente_en", "prestatario_usuario_id", "prestamista_usuario_id"); // Notificaciones
    exige("ExtensionSolicitada", "cambio_fecha_prestamo_id", "fecha_propuesta_en", "prestamista_usuario_id"); // Notificaciones
    exige("ExtensionRechazada", "cambio_fecha_prestamo_id", "prestatario_usuario_id"); // Notificaciones
    exige("ReprogramacionPropuesta", "cambio_fecha_prestamo_id", "fecha_propuesta_en", "prestatario_usuario_id"); // Notificaciones
    exige("ReprogramacionRechazada", "cambio_fecha_prestamo_id", "prestamista_usuario_id"); // Notificaciones
    exige("FechaDevolucionActualizada", "reserva_id", "cambio_fecha_prestamo_id", "devolucion_vigente_en", "prestatario_usuario_id", "prestamista_usuario_id"); // Notificaciones
    exige("PrestamoFinalizado", "prestamo_id", "prestatario_usuario_id", "prestamista_usuario_id", "finalizado_en"); // Pagos, Notificaciones y Reputación
    exige("PrestamoCancelado", "reserva_id"); // Reservas
    exige("CancelacionRechazada", "reserva_id", "motivo"); // Reservas
    exige("PeriodoPrestamoLiberado", "reserva_id", "nuevo_hasta", "decision", "devolucion_confirmada_por_prestamista", "incidencias_pendientes"); // Reservas
  }

  @Test void lasFechasPactadasVanSinZonaYLosInstantesEnUtc() {
    assertTrue(emitidos.get("PrestamoCreado").get("entrega_programada_en").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));
    assertTrue(emitidos.get("PeriodoPrestamoLiberado").get("nuevo_hasta").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));
    assertTrue(emitidos.get("PrestamoFinalizado").get("finalizado_en").asString().endsWith("Z"));
  }

  private static void exige(String tipo, String... campos) { for (String c : campos) assertTrue(emitidos.get(tipo).hasNonNull(c), tipo + " sin " + c); }
}
