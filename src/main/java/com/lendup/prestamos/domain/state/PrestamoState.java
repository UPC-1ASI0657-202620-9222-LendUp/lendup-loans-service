package com.lendup.prestamos.domain.state;

import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.EstadoPrestamo;

/**
 * Prestamo State (Fig. 16, patrón State del informe): cada estado define qué acciones permite y a qué estado llevan.
 * Por defecto toda acción está prohibida; cada estado concreto habilita solo las suyas.
 */
public interface PrestamoState {
  EstadoPrestamo estado();

  default EstadoPrestamo registrarEntrega() { throw new ReglaDeNegocioException("Estado no permite entrega"); }
  default EstadoPrestamo confirmarRecepcion() { throw new ReglaDeNegocioException("Falta registrar la entrega"); }
  default EstadoPrestamo cancelar() { throw new ReglaDeNegocioException("El préstamo ya inició y no se puede cancelar"); }
  default EstadoPrestamo vencer() { throw new ReglaDeNegocioException("Solo un préstamo activo puede vencer"); }
  default EstadoPrestamo registrarDevolucion(boolean conIncidencias) { throw new ReglaDeNegocioException("El préstamo no está activo ni vencido"); }
  default EstadoPrestamo confirmarDevolucion() { throw new ReglaDeNegocioException("Devolución o incidencias pendientes"); }

  /** Solo un préstamo activo admite proponer o aceptar extensiones y reprogramaciones (RF23, RF25). */
  default void exigirCambioDeFecha() { throw new ReglaDeNegocioException("Solo un préstamo activo admite cambios de fecha"); }

  /** La extensión pagada o la reprogramación aceptada fija una nueva fecha de devolución. */
  default EstadoPrestamo aplicarNuevaFecha(boolean nuevaFechaEnElFuturo) { throw new ReglaDeNegocioException("El préstamo ya no admite una nueva fecha de devolución"); }

  default EstadoPrestamo reportarIncidencia() { return estado(); }
  default EstadoPrestamo resolverIncidencias() { return estado(); }

  static PrestamoState de(EstadoPrestamo estado) {
    return switch (estado) {
      case RESERVADO -> new ReservadoState();
      case ENTREGA_REGISTRADA -> new EntregaRegistradaState();
      case ACTIVO -> new ActivoState();
      case VENCIDO -> new VencidoState();
      case DEVOLUCION_REGISTRADA -> new DevolucionRegistradaState();
      case PENDIENTE_INCIDENCIA -> new PendienteIncidenciaState();
      case FINALIZADO -> () -> EstadoPrestamo.FINALIZADO;
      case CANCELADO -> () -> EstadoPrestamo.CANCELADO;
    };
  }
}
