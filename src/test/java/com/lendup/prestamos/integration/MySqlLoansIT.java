package com.lendup.prestamos.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.application.service.*;
import com.lendup.prestamos.domain.model.EstadoPrestamo;
import com.lendup.prestamos.infrastructure.messaging.LoanEventConsumer;
import com.lendup.prestamos.infrastructure.persistence.entity.OutboxEventEntity;
import com.lendup.prestamos.infrastructure.persistence.repository.*;
import com.lendup.prestamos.support.TestAuthConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** MySQL real (Flyway + validate) y la API completa con seguridad. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestAuthConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "firebase.project-id=test",
    "lendup.outbox.relay-delay-ms=3600000", "lendup.overdue.check-delay-ms=3600000", "lendup.security.reservations-service-token=" + MySqlLoansIT.SERVICE_TOKEN,
    "lendup.security.evidence-service-token=" + MySqlLoansIT.EVIDENCE_TOKEN})
class MySqlLoansIT {
  static final String SERVICE_TOKEN = "token-de-servicio-de-prueba-con-mas-de-32-caracteres";
  static final String EVIDENCE_TOKEN = "credencial-exclusiva-para-evidencias-de-prueba";
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("lendup_prestamos").withUsername("test").withPassword("test");

  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired WebApplicationContext context; @Autowired LoanApplicationService loans; @Autowired LoanEventConsumer consumer; @Autowired PrestamoRepository repository;
  @Autowired PrestamoJpaRepository prestamos; @Autowired CambioFechaJpaRepository cambios; @Autowired OutboxJpaRepository outbox; @Autowired ProcessedEventJpaRepository processed;
  @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactions;
  MockMvc mvc; UUID reserva, publicacion;
  final UUID prestamista = TestAuthConfiguration.usuario("prestamista"), prestatario = TestAuthConfiguration.usuario("prestatario");
  LocalDateTime desde, hasta;

  @BeforeEach void setup() {
    processed.deleteAll(); outbox.deleteAll(); cambios.deleteAll(); prestamos.deleteAll();
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    LocalDateTime ahora = LocalDateTime.now(BusinessClock.ZONA).withNano(0);
    desde = ahora.minusHours(2); hasta = ahora.plusDays(2);
    reserva = UUID.randomUUID(); publicacion = UUID.randomUUID();
  }

  private UUID crear() { return crear(reserva, desde, hasta); }
  private UUID crear(UUID reservaId, LocalDateTime d, LocalDateTime h) {
    loans.crearDesdeReserva(new ReservaConfirmadaCommand(reservaId, publicacion, prestamista, prestatario, d, h, new BigDecimal("20.00"), new BigDecimal("50.00"), "PEN"), "it");
    return repository.findByReserva(reservaId).orElseThrow().id();
  }
  private UUID listo() {
    UUID id = crear();
    loans.registrarTarifaPagada(id, Instant.now(), "it");
    loans.registrarGarantiaConstituida(id, Instant.now(), "it");
    return id;
  }
  private UUID activo() throws Exception {
    UUID id = listo();
    accion("prestamista", id, "entrega").andExpect(status().isOk());
    accion("prestatario", id, "recepcion").andExpect(status().isOk());
    return id;
  }
  private org.springframework.test.web.servlet.ResultActions accion(String uid, UUID id, String accion) throws Exception {
    return mvc.perform(post("/api/v1/prestamos/{id}/" + accion, id).header("Authorization", "Bearer " + uid));
  }
  private List<String> eventos() { return outbox.findAll().stream().sorted(Comparator.comparing(OutboxEventEntity::getOccurredAt).thenComparing(OutboxEventEntity::getAggregateVersion)).map(OutboxEventEntity::getEventType).toList(); }

  @Test void cicloCompletoPorLaApi() throws Exception {
    UUID id = activo();
    accion("prestatario", id, "devolucion").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DEVOLUCION_REGISTRADA"));
    String confirmado = accion("prestamista", id, "confirmacion-devolucion")
        .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FINALIZADO"))
        .andReturn().getResponse().getContentAsString();
    accion("prestamista", id, "confirmacion-devolucion").andExpect(status().isOk())
        .andExpect(jsonPath("$.devolucion_confirmada_en").value(json.readTree(confirmado).get("devolucion_confirmada_en").asString()));
    assertEquals(List.of("PrestamoCreado", "ObjetoEntregado", "PrestamoActivado", "DevolucionRegistrada"), eventos().subList(0, 4));
    assertTrue(eventos().containsAll(List.of("PrestamoFinalizado", "PeriodoPrestamoLiberado")));
    assertEquals(1L, eventos().stream().filter("PrestamoFinalizado"::equals).count());
    mvc.perform(get("/api/v1/prestamos").param("estado", "FINALIZADO").header("Authorization", "Bearer prestatario")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
    assertEquals(7, jdbc.queryForObject("select count(*) from registro_auditoria where aggregate_id=uuid_to_bin(?)", Integer.class, id.toString()));
  }

  @Test void contratoPrivadoDistingueGarantiaPersistidaDeGarantiaDesconocida() throws Exception {
    UUID id = crear();
    var ruta = "/internal/v1/prestamos/{id}/incidencias";
    var response = mvc.perform(get(ruta, id).header("Authorization", "Bearer " + EVIDENCE_TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.garantia_monetaria_acordada").value(50))
        .andExpect(jsonPath("$.moneda").value("PEN")).andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.prestamista_usuario_id").value(prestamista.toString()))
        .andExpect(jsonPath("$.prestatario_usuario_id").value(prestatario.toString()))
        .andReturn().getResponse();
    assertEquals(5, json.readTree(response.getContentAsString()).size());
    mvc.perform(get(ruta, id).header("Authorization", "Bearer " + SERVICE_TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(ruta, id).header("Authorization", "Bearer prestamista")).andExpect(status().isUnauthorized());
    mvc.perform(get(ruta, UUID.randomUUID()).header("Authorization", "Bearer " + EVIDENCE_TOKEN)).andExpect(status().isNotFound());
    mvc.perform(get("/internal/v1/prestamos/reservas/{r}/participantes/{p}/contacto", reserva, prestamista)
        .header("Authorization", "Bearer " + EVIDENCE_TOKEN)).andExpect(status().isForbidden());

    var reservaAnterior = UUID.randomUUID();
    loans.crearDesdeReserva(new ReservaConfirmadaCommand(reservaAnterior, publicacion, prestamista, prestatario,
        desde, hasta, new BigDecimal("20.00"), null, "PEN"), "it");
    var sinDato = mvc.perform(get(ruta, repository.findByReserva(reservaAnterior).orElseThrow().id())
        .header("Authorization", "Bearer " + EVIDENCE_TOKEN)).andExpect(status().isOk()).andReturn().getResponse();
    assertTrue(json.readTree(sinDato.getContentAsString()).get("garantia_monetaria_acordada").isNull());
  }

  @Test void lasFechasPactadasConservanLaHoraDeLima() throws Exception {
    UUID id = crear();
    var guardado = repository.findById(id).orElseThrow().datos();
    assertEquals(desde, guardado.entregaProgramadaEn());
    assertEquals(hasta, guardado.devolucionVigenteEn());
    assertEquals(hasta, LocalDateTime.parse(jdbc.queryForObject("select date_format(devolucion_vigente_en,'%Y-%m-%dT%H:%i:%s') from prestamos", String.class)));
  }

  @Test void extensionCompletaConPagoPorEvento() throws Exception {
    UUID id = activo();
    String cuerpo = mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fecha_propuesta_en\":\"" + hasta.plusDays(1) + "\",\"costo_adicional\":20.00,\"moneda\":\"PEN\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn().getResponse().getContentAsString();
    String cambio = json.readTree(cuerpo).get("id").asString();
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones/{c}/respuesta", id, cambio).header("Authorization", "Bearer prestamista").contentType(MediaType.APPLICATION_JSON)
        .content("{\"estado\":\"ACEPTADA_PENDIENTE_PAGO\"}")).andExpect(status().isOk());
    byte[] evento = evento("PagoConfirmado", id, Map.of("prestamo_id", id, "tipo", "PAGO_EXTENSION", "cambio_fecha_prestamo_id", cambio, "transaccion_id", UUID.randomUUID(), "confirmada_en", Instant.now()));
    consumer.handle("pagos.pago-confirmado.v1", evento);
    consumer.handle("pagos.pago-confirmado.v1", evento);
    mvc.perform(get("/api/v1/prestamos/{id}", id).header("Authorization", "Bearer prestamista")).andExpect(status().isOk())
        .andExpect(jsonPath("$.devolucion_vigente_en").value(json.readTree(json.writeValueAsString(hasta.plusDays(1))).asString()))
        .andExpect(jsonPath("$.cambios_fecha[0].estado").value("APLICADA"));
    assertEquals(1, processed.count());
  }

  @Test void unEventoRepetidoNoDuplicaElPrestamo() {
    byte[] evento = evento("ReservaConfirmada", reserva, Map.of("reserva_id", reserva, "publicacion_id", publicacion, "prestamista_usuario_id", prestamista, "prestatario_usuario_id", prestatario,
        "desde", desde, "hasta", hasta, "tarifa_diaria_acordada", 20.00, "moneda_acordada", "PEN"));
    consumer.handle("reservas.confirmada.v1", evento);
    consumer.handle("reservas.confirmada.v1", evento);
    assertEquals(1, prestamos.count()); assertEquals(1, outbox.count()); assertEquals(1, processed.count());
  }

  @Test void siFallaElProcesamientoNoQuedaNadaGuardado() {
    byte[] evento = evento("PagoConfirmado", UUID.randomUUID(), Map.of("prestamo_id", UUID.randomUUID(), "tipo", "PAGO_TARIFA", "confirmada_en", Instant.now()));
    assertThrows(RuntimeException.class, () -> consumer.handle("pagos.pago-confirmado.v1", evento));
    assertEquals(0, processed.count());
    assertEquals(0, outbox.count());
  }

  @Test void elSchedulerMarcaSoloLosVencidos() throws Exception {
    UUID vigente = activo();
    UUID reservaVencida = UUID.randomUUID();
    LocalDateTime ahora = LocalDateTime.now(BusinessClock.ZONA);
    publicacion = UUID.randomUUID();
    UUID vencido = crear(reservaVencida, ahora.minusDays(3).withNano(0), ahora.minusMinutes(1).withNano(0));
    loans.registrarTarifaPagada(vencido, Instant.now(), "it");
    loans.registrarGarantiaConstituida(vencido, Instant.now(), "it");
    loans.registrarEntrega(prestamista, vencido, "it");
    loans.confirmarRecepcion(prestatario, vencido, "it");
    assertEquals(1, loans.marcarVencidos(100));
    assertEquals(EstadoPrestamo.VENCIDO, repository.findById(vencido).orElseThrow().estado());
    assertEquals(EstadoPrestamo.ACTIVO, repository.findById(vigente).orElseThrow().estado());
    assertTrue(eventos().contains("PrestamoVencido"));
  }

  /** Entrega y cancelación simultáneas: la fila se bloquea y solo una gana; nunca quedan estados contradictorios. */
  @Test void entregaYCancelacionSimultaneasNoDejanEstadosContradictorios() throws Exception {
    for (int intento = 0; intento < 5; intento++) {
      setup();
      UUID id = listo();
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch salida = new CountDownLatch(1);
      Future<?> entrega = pool.submit(() -> { salida.await(); return loans.registrarEntrega(prestamista, id, "it"); });
      Future<?> cancelacion = pool.submit(() -> { salida.await(); loans.decidirCancelacion(reserva, "it"); return null; });
      salida.countDown();
      boolean entregado;
      try { entrega.get(10, TimeUnit.SECONDS); entregado = true; } catch (ExecutionException e) { entregado = false; }
      cancelacion.get(10, TimeUnit.SECONDS);
      pool.shutdown();
      EstadoPrestamo estado = repository.findById(id).orElseThrow().estado();
      assertEquals(entregado ? EstadoPrestamo.ENTREGA_REGISTRADA : EstadoPrestamo.CANCELADO, estado);
      assertTrue(eventos().contains(entregado ? "CancelacionRechazada" : "PrestamoCancelado"));
    }
  }

  @Test void recepcionYCancelacionSeSerializanSinActivarUnObjetoEnRecuperacion() throws Exception {
    org.springframework.test.util.ReflectionTestUtils.setField(loans, "cancellationRecovery", true);
    try {
      UUID id = listo();
      loans.registrarEntrega(prestamista, id, "it");
      try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
        var start = new CountDownLatch(1);
        var receipt = pool.submit(() -> {
          start.await();
          try {
            loans.confirmarRecepcion(prestatario, id, "it");
            return true;
          } catch (com.lendup.prestamos.domain.exception.ReglaDeNegocioException expected) {
            return false;
          }
        });
        var cancellation = pool.submit(() -> { start.await(); loans.decidirCancelacion(reserva, "it"); return true; });
        start.countDown();
        boolean accepted = receipt.get(15, TimeUnit.SECONDS);
        assertTrue(cancellation.get(15, TimeUnit.SECONDS));
        assertEquals(accepted ? EstadoPrestamo.ACTIVO : EstadoPrestamo.RECUPERACION_PENDIENTE,
            repository.findById(id).orElseThrow().estado());
        assertTrue(eventos().contains(accepted ? "CancelacionRechazada" : "RecuperacionPendiente"));
      }
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(loans, "cancellationRecovery", false);
    }
  }

  @Test void elOutboxRespetaElOrdenPorPrestamo() {
    UUID id = listo();
    loans.registrarEntrega(prestamista, id, "it");
    List<String> primero = pendientes();
    assertEquals(List.of("PrestamoCreado"), primero, "ObjetoEntregado espera a PrestamoCreado");
    publicar("PrestamoCreado");
    assertEquals(List.of("ObjetoEntregado"), pendientes());
  }

  @Test void unEventoAgotadoNoBloqueaParaSiempreALosSiguientes() {
    UUID id = listo();
    loans.registrarEntrega(prestamista, id, "it");
    jdbc.update("update outbox_events set attempts=10 where event_type='PrestamoCreado'");
    assertEquals(List.of("ObjetoEntregado"), pendientes());
  }

  private List<String> pendientes() {
    return new TransactionTemplate(transactions).execute(s -> outbox.pending(Instant.now().plusSeconds(1), 10, PageRequest.of(0, 50)).stream().map(OutboxEventEntity::getEventType).toList());
  }
  private void publicar(String tipo) { jdbc.update("update outbox_events set published_at=utc_timestamp(6) where event_type=?", tipo); }

  @Test void noSePermiteSuperponerUnaExtensionConOtraReserva() throws Exception {
    UUID id = activo();
    crear(UUID.randomUUID(), hasta.plusHours(5), hasta.plusDays(3));
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).header("Authorization", "Bearer prestatario").contentType(MediaType.APPLICATION_JSON)
        .content("{\"fecha_propuesta_en\":\"" + hasta.plusDays(1) + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("La nueva fecha se superpone con otra reserva confirmada del objeto"));
    assertEquals(0, cambios.count());
  }

  @Test void seguridad() throws Exception {
    UUID id = crear();
    mvc.perform(get("/api/v1/prestamos")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Autenticación requerida"));
    mvc.perform(get("/api/v1/prestamos/{id}", id).header("Authorization", "Bearer intruso")).andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/env").header("Authorization", "Bearer prestatario")).andExpect(status().isForbidden());
    String interna = "/internal/v1/prestamos/reservas/" + reserva + "/participantes/" + prestatario + "/contacto";
    mvc.perform(get(interna)).andExpect(status().isUnauthorized());
    mvc.perform(get(interna).header("Authorization", "Bearer prestatario")).andExpect(status().isUnauthorized());
    mvc.perform(get(interna).header("Authorization", "Bearer " + SERVICE_TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.contacto_vigente").value(true));
    mvc.perform(get("/api/v1/prestamos").header("Authorization", "Bearer " + SERVICE_TOKEN).param("estado", "ACTIVO")).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  private byte[] evento(String tipo, UUID agregado, Map<String, Object> payload) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("event_id", UUID.randomUUID()); e.put("event_type", tipo); e.put("event_version", 1); e.put("aggregate_version", 1); e.put("aggregate_id", agregado);
    e.put("occurred_at", Instant.now()); e.put("correlation_id", "it"); e.put("payload", payload);
    return json.writeValueAsString(e).getBytes(StandardCharsets.UTF_8);
  }
}
