package com.lendup.prestamos.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.prestamos.application.port.in.LoanEventUseCases;
import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.config.EventRoutingProperties;
import com.lendup.prestamos.infrastructure.persistence.entity.ProcessedEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Usa los mensajes de contracts/events/v1/examples.json, que son los mismos que documentan los otros servicios. */
class LoanEventConsumerTest {
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  private final ProcessedEventJpaRepository processed = mock(ProcessedEventJpaRepository.class);
  private final LoanEventUseCases loans = mock(LoanEventUseCases.class);
  private final LoanEventConsumer consumer = new LoanEventConsumer(json, processed, loans, EventRoutingProperties.defaults(), Clock.systemUTC());
  private final UUID prestamo = UUID.fromString("c3333333-3333-4333-8333-333333333333"), reserva = UUID.fromString("22222222-2222-4222-8222-222222222222");

  private byte[] ejemplo(String clave) throws IOException {
    JsonNode nodo = json.readTree(Files.readString(Path.of("contracts/events/v1/examples.json"))).get("consumed").get(clave);
    assertNotNull(nodo, clave);
    return json.writeValueAsBytes(nodo);
  }

  @Test void reservaConfirmada() throws Exception {
    consumer.handle("reservas.confirmada.v1", ejemplo("reservas.confirmada.v1"));
    verify(loans).crearDesdeReserva(eq(new ReservaConfirmadaCommand(reserva, UUID.fromString("44444444-4444-4444-8444-444444444444"), UUID.fromString("55555555-5555-4555-8555-555555555555"),
        UUID.fromString("66666666-6666-4666-8666-666666666666"), LocalDateTime.of(2026, 11, 10, 9, 0), LocalDateTime.of(2026, 11, 12, 9, 0), new BigDecimal("20.0"),
        new BigDecimal("50.0"), "PEN")), eq("req-001"));
    verify(processed).save(any(ProcessedEventEntity.class));
  }

  @Test void cancelacionSolicitadaYReservaCancelada() throws Exception {
    consumer.handle("reservas.cancelacion-solicitada.v1", ejemplo("reservas.cancelacion-solicitada.v1"));
    consumer.handle("reservas.cancelada.v1", ejemplo("reservas.cancelada.v1"));
    verify(loans).decidirCancelacion(eq(reserva), anyString());
    verify(loans).registrarReservaCancelada(eq(reserva), anyString());
  }

  @Test void pagosYGarantia() throws Exception {
    consumer.handle("pagos.pago-confirmado.v1", ejemplo("pagos.pago-confirmado.v1 (tarifa)"));
    verify(loans).registrarTarifaPagada(eq(prestamo), eq(Instant.parse("2026-11-01T15:00:00Z")), anyString());
    consumer.handle("pagos.pago-confirmado.v1", ejemplo("pagos.pago-confirmado.v1 (extension)"));
    verify(loans).registrarExtensionPagada(eq(new ExtensionPagadaCommand(prestamo, UUID.fromString("88888888-8888-4888-8888-888888888888"),
        UUID.fromString("b2222222-2222-4222-8222-222222222223"), Instant.parse("2026-11-11T12:30:00Z"))), anyString());
    consumer.handle("pagos.garantia-constituida.v1", ejemplo("pagos.garantia-constituida.v1"));
    verify(loans).registrarGarantiaConstituida(eq(prestamo), eq(Instant.parse("2026-11-01T15:01:40Z")), anyString());
    consumer.handle("pagos.pago-rechazado.v1", ejemplo("pagos.pago-rechazado.v1"));
    verify(loans).registrarPagoRechazado(eq(prestamo), eq("PAGO_TARIFA"), anyString());
  }

  @Test void incidencias() throws Exception {
    consumer.handle("evidencias.incidencia-reportada.v1", ejemplo("evidencias.incidencia-reportada.v1 (pendiente)"));
    consumer.handle("evidencias.incidencia-resuelta.v1", ejemplo("evidencias.incidencia-resuelta.v1 (pendiente)"));
    verify(loans).registrarIncidenciaReportada(eq(prestamo), anyString());
    verify(loans).registrarIncidenciaResuelta(eq(prestamo), anyString());
  }

  @Test void unEventoRepetidoSeDescarta() throws Exception {
    when(processed.existsById(any())).thenReturn(true);
    consumer.handle("reservas.confirmada.v1", ejemplo("reservas.confirmada.v1"));
    verifyNoInteractions(loans);
    verify(processed, never()).save(any());
  }

  @Test void siElCasoDeUsoFallaElEventoNoQuedaComoProcesado() throws Exception {
    doThrow(new IllegalStateException("sin préstamo")).when(loans).registrarTarifaPagada(any(), any(), any());
    byte[] cuerpo = ejemplo("pagos.pago-confirmado.v1 (tarifa)");
    assertThrows(IllegalStateException.class, () -> consumer.handle("pagos.pago-confirmado.v1", cuerpo));
    verify(processed, never()).save(any());
  }

  @Test void mensajesInvalidosSeRechazan() {
    assertThrows(RuntimeException.class, () -> consumer.handle("reservas.confirmada.v1", "{}".getBytes(StandardCharsets.UTF_8)));
    byte[] sinPayload = ("{\"event_id\":\"" + UUID.randomUUID() + "\"}").getBytes(StandardCharsets.UTF_8);
    assertThrows(IllegalArgumentException.class, () -> consumer.handle("reservas.confirmada.v1", sinPayload));
    byte[] desconocido = ("{\"event_id\":\"" + UUID.randomUUID() + "\",\"payload\":{}}").getBytes(StandardCharsets.UTF_8);
    assertThrows(IllegalArgumentException.class, () -> consumer.handle("otra.cosa.v1", desconocido));
  }
}
