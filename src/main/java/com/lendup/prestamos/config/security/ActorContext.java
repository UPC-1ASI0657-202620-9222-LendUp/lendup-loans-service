package com.lendup.prestamos.config.security;

import com.lendup.prestamos.application.exception.ForbiddenException;
import com.lendup.prestamos.application.port.out.IdentidadPort;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ActorContext {
  private final IdentidadPort identidad;

  public ActorContext(IdentidadPort identidad) { this.identidad = identidad; }

  /** usuario_id interno del usuario autenticado. Nunca se toma del cuerpo ni de headers públicos. */
  public UUID userId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof VerifiedToken)) {
      throw new ForbiddenException("Identidad no autenticada");
    }
    return identidad.resolverUsuarioId(authentication.getName(), bearerToken());
  }

  public String bearerToken() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return "";
    String header = attributes.getRequest().getHeader("Authorization");
    return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
  }
}
