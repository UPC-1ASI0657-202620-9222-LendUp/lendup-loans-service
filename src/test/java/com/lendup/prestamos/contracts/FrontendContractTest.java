package com.lendup.prestamos.contracts;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.application.port.in.*;
import com.lendup.prestamos.config.security.ActorContext;
import com.lendup.prestamos.domain.exception.*;
import com.lendup.prestamos.domain.model.*;
import com.lendup.prestamos.interfaces.rest.*;
import com.lendup.prestamos.support.LoansTestFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Contrato con el frontend (services/loans.service.ts y mapLoan de mappers/domain.ts): mismas rutas, cuerpos en
 * snake_case, códigos HTTP y formato de error {status, error, message} del monolito.
 */
class FrontendContractTest {
  private final LoansTestFixture f = new LoansTestFixture();
  private final ActorContext actor = mock(ActorContext.class);
  private MockMvc mvc;

  @BeforeEach void setup() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new LoanController(f.service, f.queries, actor), new InternalLoanController(f.queries))
        .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
    when(actor.bearerToken()).thenReturn("t");
  }

  private void como(UUID usuario) { when(actor.userId()).thenReturn(usuario); }

  @Test void elPrestamoTieneLosCamposQueLeeMapLoan() throws Exception {
    UUID id = f.activo();
    como(f.prestatario);
    mvc.perform(get("/api/v1/prestamos/{id}", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.reserva_id").exists()).andExpect(jsonPath("$.publicacion_id").value(f.publicacion.toString()))
        .andExpect(jsonPath("$.prestatario_usuario_id").value(f.prestatario.toString())).andExpect(jsonPath("$.prestamista_usuario_id").value(f.prestamista.toString()))
        .andExpect(jsonPath("$.estado").value("ACTIVO")).andExpect(jsonPath("$.entrega_programada_en").value("2026-11-10T09:00:00"))
        .andExpect(jsonPath("$.devolucion_original_en").value("2026-11-12T09:00:00")).andExpect(jsonPath("$.devolucion_vigente_en").value("2026-11-12T09:00:00"))
        .andExpect(jsonPath("$.tarifa_diaria_acordada").value(20.00)).andExpect(jsonPath("$.garantia_requerida").value(true))
        .andExpect(jsonPath("$.pago_tarifa_confirmado_en", endsWith("Z"))).andExpect(jsonPath("$.garantia_constituida_en", endsWith("Z")))
        .andExpect(jsonPath("$.entrega_registrada_en").value("2026-11-10T14:00:00Z")).andExpect(jsonPath("$.recepcion_confirmada_en").exists())
        .andExpect(jsonPath("$.creado_en").exists()).andExpect(jsonPath("$.evidencias").isArray()).andExpect(jsonPath("$.cambios_fecha").isArray());
  }

  @Test void lasAccionesSinCuerpoRespondenElPrestamoActualizado() throws Exception {
    UUID id = f.listoParaEntregar();
    f.clock.setLima(LoansTestFixture.DESDE);
    como(f.prestamista);
    mvc.perform(post("/api/v1/prestamos/{id}/entrega", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENTREGA_REGISTRADA"));
    como(f.prestatario);
    mvc.perform(post("/api/v1/prestamos/{id}/recepcion", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));
    mvc.perform(post("/api/v1/prestamos/{id}/devolucion", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DEVOLUCION_REGISTRADA"));
    como(f.prestamista);
    mvc.perform(post("/api/v1/prestamos/{id}/confirmacion-devolucion", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FINALIZADO")).andExpect(jsonPath("$.finalizado_en").exists());
  }

  @Test void laExtensionAceptaElCuerpoDelFrontend() throws Exception {
    UUID id = f.activo();
    como(f.prestatario);
    String cuerpo = mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fecha_propuesta_en\":\"2026-11-13T10:00:00\",\"costo_adicional\":40.00,\"moneda\":\"PEN\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("EXTENSION")).andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.prestamo_id").value(id.toString())).andExpect(jsonPath("$.costo_adicional").value(40.00)).andExpect(jsonPath("$.fecha_anterior_en").value("2026-11-12T09:00:00"))
        .andReturn().getResponse().getContentAsString();
    String cambio = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id");
    como(f.prestamista);
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones/{sub}/respuesta", id, cambio).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ACEPTADA_PENDIENTE_PAGO\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADA_PENDIENTE_PAGO"));
    mvc.perform(get("/api/v1/prestamos/{id}/cambios-fecha", id)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
  }

  @Test void unaFechaConZonaHorariaSeConvierteALima() throws Exception {
    UUID id = f.activo();
    como(f.prestatario);
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).contentType(MediaType.APPLICATION_JSON).content("{\"fecha_propuesta_en\":\"2026-11-13T15:00:00Z\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.fecha_propuesta_en").value("2026-11-13T10:00:00"));
  }

  @Test void reprogramacionYSuRespuesta() throws Exception {
    UUID id = f.activo();
    como(f.prestamista);
    String cuerpo = mvc.perform(post("/api/v1/prestamos/{id}/reprogramaciones", id).contentType(MediaType.APPLICATION_JSON).content("{\"fecha_propuesta_en\":\"2026-11-12T20:00:00\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("REPROGRAMACION")).andExpect(jsonPath("$.costo_adicional").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    String cambio = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id");
    como(f.prestatario);
    mvc.perform(post("/api/v1/prestamos/{id}/reprogramaciones/{sub}/respuesta", id, cambio).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"APLICADA\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APLICADA")).andExpect(jsonPath("$.fecha_resultante_en").value("2026-11-12T20:00:00"));
  }

  @Test void listadosCalendarioYCotizaciones() throws Exception {
    UUID id = f.activo();
    como(f.prestatario);
    mvc.perform(get("/api/v1/prestamos").param("estado", "ACTIVO")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
    mvc.perform(get("/api/v1/calendario")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    mvc.perform(get("/api/v1/prestamos/{id}/cotizacion-pago", id)).andExpect(status().isOk()).andExpect(jsonPath("$.tarifa_diaria").value(20.00))
        .andExpect(jsonPath("$.garantia").value(50.00)).andExpect(jsonPath("$.estado").value("COTIZACION_PRELIMINAR")).andExpect(jsonPath("$.importe_tarifa").value(40.00));
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones/cotizacion", id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COTIZACION_PRELIMINAR")).andExpect(jsonPath("$.mensaje").exists());
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones/cotizacion", id).contentType(MediaType.APPLICATION_JSON).content("{\"fecha_propuesta_en\":\"2026-11-12T10:00:00\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.costo_adicional").value(20.00));
  }

  @Test void pendienteDeIncidenciaSeVeComoDevolucionRegistrada() throws Exception {
    UUID id = f.devuelto();
    f.service.registrarIncidenciaReportada(id, "c");
    como(f.prestamista);
    mvc.perform(get("/api/v1/prestamos/{id}", id)).andExpect(jsonPath("$.estado").value("DEVOLUCION_REGISTRADA")).andExpect(jsonPath("$.estado_detalle").value("PENDIENTE_INCIDENCIA"))
        .andExpect(jsonPath("$.incidencias_pendientes").value(1));
  }

  @Test void erroresConElFormatoDelMonolito() throws Exception {
    UUID id = f.reservado();
    como(f.prestamista);
    mvc.perform(post("/api/v1/prestamos/{id}/entrega", id)).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("CONFLICTO_NEGOCIO")).andExpect(jsonPath("$.message").value("Falta confirmar el pago o la garantía"));
    como(f.intruso);
    mvc.perform(get("/api/v1/prestamos/{id}", id)).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("No participas en este préstamo"));
    mvc.perform(get("/api/v1/prestamos/{id}", UUID.randomUUID())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NO_ENCONTRADO"));
    mvc.perform(get("/api/v1/prestamos/no-es-uuid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("SOLICITUD_INVALIDA"));
    mvc.perform(get("/api/v1/prestamos").param("estado", "X")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("fecha_propuesta_en")));
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).contentType(MediaType.APPLICATION_JSON).content("{\"fecha_propuesta_en\":\"mañana\"}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Fechas inválidas")));
    mvc.perform(post("/api/v1/prestamos/{id}/extensiones", id).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
  }

  @Test void contratoInternoDeContactoQueEsperaReservas() throws Exception {
    UUID id = f.activo();
    UUID reserva = f.prestamo(id).reservaId();
    mvc.perform(get("/internal/v1/prestamos/reservas/{r}/participantes/{u}/contacto", reserva, f.prestatario)).andExpect(status().isOk()).andExpect(jsonPath("$.contacto_vigente").value(true));
    mvc.perform(get("/internal/v1/prestamos/reservas/{r}/participantes/{u}/contacto", reserva, f.intruso)).andExpect(jsonPath("$.contacto_vigente").value(false));
    mvc.perform(get("/internal/v1/prestamos/reservas/{r}/participantes/{u}/contacto", UUID.randomUUID(), f.prestatario)).andExpect(status().isNotFound());
  }
}
