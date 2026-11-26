package com.lendup.prestamos.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Credencial de servicio de las rutas /internal/**. Un token ausente o de menos de 32 caracteres deshabilita las
 * rutas internas; nunca se acepta un Firebase ID token como credencial de servicio.
 */
public class InternalServiceTokenFilter extends OncePerRequestFilter {
  private static final Pattern OPAQUE_CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{32,}");
  private final byte[] reservations;
  private final byte[] evidence;

  public InternalServiceTokenFilter(String reservationsToken, String evidenceToken) {
    boolean shared = reservationsToken != null && reservationsToken.equals(evidenceToken);
    reservations = shared ? null : credential(reservationsToken);
    evidence = shared ? null : credential(evidenceToken);
  }

  private static byte[] credential(String token) {
    return token == null || !OPAQUE_CREDENTIAL.matcher(token).matches()
        ? null : token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    byte[] received = header != null && header.startsWith("Bearer ") ? header.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
    boolean isReservations = reservations != null && MessageDigest.isEqual(reservations, received);
    boolean isEvidence = evidence != null && MessageDigest.isEqual(evidence, received);
    if (!isReservations && !isEvidence) {
      JsonErrorWriter.write(response, 401, "CREDENCIAL_DE_SERVICIO_INVALIDA", "Credencial de servicio inválida");
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(isReservations ? "reservations-service" : "evidence-service", null,
            List.of(new SimpleGrantedAuthority(isReservations ? "LOANS_CONTACT" : "LOANS_INCIDENTS"))));
    chain.doFilter(request, response);
  }
}
