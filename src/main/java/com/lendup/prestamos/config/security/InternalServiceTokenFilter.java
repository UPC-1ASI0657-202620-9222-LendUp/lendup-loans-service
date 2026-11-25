package com.lendup.prestamos.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Credencial de servicio de las rutas /internal/**. Un token ausente o de menos de 32 caracteres deshabilita las
 * rutas internas; nunca se acepta un Firebase ID token como credencial de servicio.
 */
public class InternalServiceTokenFilter extends OncePerRequestFilter {
  private static final int MIN_LENGTH = 32;
  private final byte[] expected;

  public InternalServiceTokenFilter(String serviceToken) {
    expected = serviceToken == null || serviceToken.length() < MIN_LENGTH ? null : serviceToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    byte[] received = header != null && header.startsWith("Bearer ") ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
    if (expected == null || !MessageDigest.isEqual(expected, received)) {
      JsonErrorWriter.write(response, 401, "CREDENCIAL_DE_SERVICIO_INVALIDA", "Credencial de servicio inválida");
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("reservations-service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICIO"))));
    chain.doFilter(request, response);
  }
}
