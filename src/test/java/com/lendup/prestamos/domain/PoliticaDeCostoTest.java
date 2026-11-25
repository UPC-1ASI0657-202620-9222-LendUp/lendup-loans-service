package com.lendup.prestamos.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.prestamos.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R26: cada bloque de 24 horas iniciado se cobra completo. Debe coincidir con extensionCost del frontend. */
class PoliticaDeCostoTest {
  private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 12, 9, 0);

  @ParameterizedTest(name = "{0} minutos → {1}")
  @CsvSource({"1, 20.00", "60, 20.00", "1440, 20.00", "1441, 40.00", "2880, 40.00", "4320, 60.00", "4321, 80.00"})
  void cobraPorBloquesIniciados(long minutos, BigDecimal esperado) {
    assertEquals(esperado, PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS.importe(new PeriodoPrestamo(BASE, BASE.plusMinutes(minutos)), new BigDecimal("20.00")));
  }

  @Test void redondeaADosDecimales() {
    assertEquals(new BigDecimal("9.99"), PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS.importe(new PeriodoPrestamo(BASE, BASE.plusDays(3)), new BigDecimal("3.33")));
  }

  @Test void rechazaPeriodoOTarifaInvalidos() {
    assertThrows(IllegalArgumentException.class, () -> new PeriodoPrestamo(BASE, BASE));
    PeriodoPrestamo periodo = new PeriodoPrestamo(BASE, BASE.plusDays(1));
    assertThrows(IllegalArgumentException.class, () -> PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS.importe(periodo, BigDecimal.ZERO));
  }
}
