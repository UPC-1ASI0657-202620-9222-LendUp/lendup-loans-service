package com.lendup.prestamos.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Valida el Firebase ID token en el propio servicio (revocación y correo verificado), igual que Reservas y Pagos. */
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
  private final TokenVerifier verifier;

  public FirebaseAuthenticationFilter(TokenVerifier verifier) { this.verifier = verifier; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      VerifiedToken token;
      try {
        token = verifier.verify(header.substring(7));
      } catch (Exception e) {
        JsonErrorWriter.write(response, 401, "TOKEN_INVALIDO", "Token Firebase no válido");
        return;
      }
      if (!token.emailVerified()) {
        JsonErrorWriter.write(response, 403, "EMAIL_NOT_VERIFIED", "Verifica tu correo para ingresar a LendUp");
        return;
      }
      var authorities = token.administrator() ? List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")) : List.<SimpleGrantedAuthority>of();
      var authentication = new UsernamePasswordAuthenticationToken(token.uid(), null, authorities);
      authentication.setDetails(token);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    chain.doFilter(request, response);
  }
}
