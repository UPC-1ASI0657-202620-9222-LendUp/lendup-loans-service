package com.lendup.prestamos.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lendup.prestamos.application.exception.*;
import com.lendup.prestamos.config.IntegrationProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;

/** Contratos HTTP con Identidad (interno y /estudiantes/me) y con Evidencias (opcional). */
class RestAdaptersTest {
  private static final WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private final UUID usuario = UUID.randomUUID(), prestamo = UUID.randomUUID();

  @BeforeAll static void start() { server.start(); }
  @AfterAll static void stop() { server.stop(); }
  @BeforeEach void reset() { server.resetAll(); }

  private IdentidadRestAdapter identidad(String token, String template) {
    return new IdentidadRestAdapter(new IntegrationProperties(new IntegrationProperties.Identity(server.baseUrl(), token, template, 500, 500), null));
  }

  private EvidenciasRestAdapter evidencias(String template) {
    return new EvidenciasRestAdapter(new IntegrationProperties(null, new IntegrationProperties.Evidence(server.baseUrl(), template, 500, 500)));
  }

  @Test void contratoInternoDeIdentidadConTokenDeServicio() {
    server.stubFor(get("/internal/v1/identidad/firebase/uid-1").withHeader("Authorization", equalTo("Bearer servicio")).willReturn(okJson("{\"usuario_id\":\"" + usuario + "\"}")));
    assertEquals(usuario, identidad("servicio", "/internal/v1/identidad/firebase/{uid}").resolverUsuarioId("uid-1", "token-usuario"));
  }

  @Test void sinTokenDeServicioReenviaElTokenDelUsuario() {
    server.stubFor(get("/api/v1/estudiantes/me").withHeader("Authorization", equalTo("Bearer token-usuario")).willReturn(okJson("{\"usuario\":{\"id\":\"" + usuario + "\"}}")));
    assertEquals(usuario, identidad("", "/api/v1/estudiantes/me").resolverUsuarioId("uid-1", "token-usuario"));
  }

  @Test void erroresDeIdentidad() {
    server.stubFor(get(anyUrl()).willReturn(notFound()));
    assertThrows(ForbiddenException.class, () -> identidad("s", "/x/{uid}").resolverUsuarioId("u", "t"));
    server.stubFor(get(anyUrl()).willReturn(serverError()));
    assertThrows(DependencyUnavailableException.class, () -> identidad("s", "/x/{uid}").resolverUsuarioId("u", "t"));
    server.stubFor(get(anyUrl()).willReturn(okJson("{}")));
    assertThrows(DependencyUnavailableException.class, () -> identidad("s", "/x/{uid}").resolverUsuarioId("u", "t"));
    server.stubFor(get(anyUrl()).willReturn(okJson("{}").withFixedDelay(1500)));
    assertThrows(DependencyUnavailableException.class, () -> identidad("s", "/x/{uid}").resolverUsuarioId("u", "t"));
    assertThrows(DependencyUnavailableException.class, () -> new IdentidadRestAdapter(new IntegrationProperties(null, null)).resolverUsuarioId("u", "t"));
  }

  @Test void lasEvidenciasSonOpcionalesYNuncaBloqueanElDetalle() {
    server.stubFor(get("/prestamos/" + prestamo + "/evidencias").withHeader("Authorization", equalTo("Bearer t")).willReturn(okJson("[{\"id\":\"e1\",\"etapa\":\"ENTREGA\"}]")));
    assertEquals("ENTREGA", evidencias("/prestamos/{id}/evidencias").evidenciasDelPrestamo(prestamo, "t").getFirst().get("etapa"));
    assertEquals(List.of(), evidencias("").evidenciasDelPrestamo(prestamo, "t"), "sin configurar");
    server.resetAll();
    server.stubFor(get(anyUrl()).willReturn(serverError()));
    assertEquals(List.of(), evidencias("/prestamos/{id}/evidencias").evidenciasDelPrestamo(prestamo, "t"), "caído");
  }
}
