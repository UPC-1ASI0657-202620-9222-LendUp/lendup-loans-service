package com.lendup.prestamos.application.service;

import java.time.*;
import org.springframework.stereotype.Component;

/**
 * Reloj del servicio: instantes UTC para los timestamps y hora de negocio de Lima para comparar las fechas pactadas
 * (entrega y devolución), que llegan sin offset desde Reservas y el frontend.
 */
@Component
public class BusinessClock {
  public static final ZoneId ZONA = ZoneId.of("America/Lima");
  private final Clock clock;

  public BusinessClock(Clock clock) { this.clock = clock; }

  public Instant instant() { return clock.instant(); }
  public LocalDateTime ahoraLocal() { return LocalDateTime.ofInstant(clock.instant(), ZONA); }
}
