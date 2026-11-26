package com.lendup.prestamos.config.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityFiltersTest {
  private static final String SERVICE_TOKEN = "token-de-servicio-de-prueba-con-mas-de-32-caracteres";
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  private String cuerpo() { return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8); }

  @Test void tokenValidoAutenticaConElUid() throws Exception {
    request.addHeader("Authorization", "Bearer abc");
    new FirebaseAuthenticationFilter(t -> new VerifiedToken("uid-1", true, false)).doFilter(request, response, chain);
    assertEquals("uid-1", SecurityContextHolder.getContext().getAuthentication().getName());
    assertNotNull(chain.getRequest());
  }

  @Test void tokenInvalidoDa401ConTildesCorrectas() throws Exception {
    request.addHeader("Authorization", "Bearer abc");
    new FirebaseAuthenticationFilter(t -> { throw new IllegalArgumentException("x"); }).doFilter(request, response, chain);
    assertEquals(401, response.getStatus());
    assertTrue(cuerpo().contains("Token Firebase no válido"), cuerpo());
    assertEquals("UTF-8", response.getCharacterEncoding());
    assertNull(chain.getRequest());
  }

  @Test void correoNoVerificadoDa403() throws Exception {
    request.addHeader("Authorization", "Bearer abc");
    new FirebaseAuthenticationFilter(t -> new VerifiedToken("uid-1", false, false)).doFilter(request, response, chain);
    assertEquals(403, response.getStatus());
    assertTrue(cuerpo().contains("EMAIL_NOT_VERIFIED"));
  }

  @Test void sinTokenNoAutenticaYLosRolesDeHeadersSeIgnoran() throws Exception {
    request.addHeader("X-Role", "ADMINISTRADOR");
    new FirebaseAuthenticationFilter(t -> new VerifiedToken("uid-1", true, true)).doFilter(request, response, chain);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test void laRutaInternaExigeLaCredencialDeServicioExacta() throws Exception {
    request.addHeader("Authorization", "Bearer " + SERVICE_TOKEN);
    new InternalServiceTokenFilter(SERVICE_TOKEN, "").doFilter(request, response, chain);
    assertEquals(200, response.getStatus());
    assertNotNull(chain.getRequest());
  }

  @Test void credencialDeServicioAusenteDistintaODebilSeRechaza() throws Exception {
    for (String[] caso : new String[][] {{SERVICE_TOKEN, null}, {SERVICE_TOKEN, "Bearer otro"}, {"corta", "Bearer corta"}, {"", "Bearer "}}) {
      MockHttpServletRequest rq = new MockHttpServletRequest();
      MockHttpServletResponse rs = new MockHttpServletResponse();
      MockFilterChain ch = new MockFilterChain();
      if (caso[1] != null) rq.addHeader("Authorization", caso[1]);
      new InternalServiceTokenFilter(caso[0], "").doFilter(rq, rs, ch);
      assertEquals(401, rs.getStatus());
      assertNull(ch.getRequest());
    }
  }

  @Test void tokenCompartidoYTokenConFormatoJwtSeRechazan() throws Exception {
    for (String token : new String[] {SERVICE_TOKEN, "aaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbb.cccccccccccccccc"}) {
      var rq = new MockHttpServletRequest();
      var rs = new MockHttpServletResponse();
      rq.addHeader("Authorization", "Bearer " + token);
      new InternalServiceTokenFilter(SERVICE_TOKEN, token).doFilter(rq, rs, new MockFilterChain());
      assertEquals(401, rs.getStatus());
    }
  }
}
