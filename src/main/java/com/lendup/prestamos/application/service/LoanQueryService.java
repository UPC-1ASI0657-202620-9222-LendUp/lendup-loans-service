package com.lendup.prestamos.application.service;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.application.port.in.LoanQueryUseCases;
import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.application.port.out.EvidenciasPort;
import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.domain.model.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Consulta Préstamos Service (Fig. 16): listados, detalle, calendario, historial de cambios y cotizaciones. */
@Service
@Transactional(readOnly = true)
public class LoanQueryService implements LoanQueryUseCases {
  private static final Set<EstadoPrestamo> TODOS = EnumSet.allOf(EstadoPrestamo.class);
  private static final Set<EstadoPrestamo> EN_CALENDARIO = EnumSet.complementOf(EnumSet.of(EstadoPrestamo.CANCELADO));
  private final PrestamoRepository repository;
  private final EvidenciasPort evidencias;
  private final PoliticaDeCosto politica;

  public LoanQueryService(PrestamoRepository repository, EvidenciasPort evidencias, PoliticaDeCosto politica) {
    this.repository = repository; this.evidencias = evidencias; this.politica = politica;
  }

  @Override
  public List<PrestamoView> listar(UUID actor, String estado) {
    return repository.findByParticipante(actor, estados(estado)).stream().map(p -> vista(p, List.of())).toList();
  }

  /** Sin transacción: la consulta de evidencias es una llamada remota y no debe retener una conexión a la base. */
  @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PrestamoView detalle(UUID actor, UUID prestamoId, String bearerToken) {
    Prestamo prestamo = delParticipante(actor, prestamoId);
    return vista(prestamo, evidencias.evidenciasDelPrestamo(prestamoId, bearerToken));
  }

  @Override
  public List<PrestamoView> calendario(UUID actor) {
    return repository.findByParticipante(actor, EN_CALENDARIO).stream()
        .sorted(Comparator.comparing((Prestamo p) -> p.datos().entregaProgramadaEn())).map(p -> vista(p, List.of())).toList();
  }

  @Override
  public List<CambioFechaView> cambiosDeFecha(UUID actor, UUID prestamoId) {
    return delParticipante(actor, prestamoId).cambios().stream().map(c -> new CambioFechaView(prestamoId, c.datos())).toList();
  }

  @Override
  public CotizacionExtensionView cotizarExtension(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta) {
    Prestamo prestamo = delParticipante(actor, prestamoId);
    Prestamo.Datos d = prestamo.datos();
    return new CotizacionExtensionView(prestamoId, d.tarifaDiariaAcordada(), d.moneda(), d.devolucionVigenteEn(), fechaPropuesta,
        fechaPropuesta == null ? null : prestamo.cotizarExtension(fechaPropuesta, politica));
  }

  @Override
  public CotizacionPagoView cotizarPago(UUID actor, UUID prestamoId) {
    Prestamo.Datos d = delParticipante(actor, prestamoId).datos();
    return new CotizacionPagoView(prestamoId, d.tarifaDiariaAcordada(), d.garantiaMonetariaAcordada(), d.moneda(),
        politica.importe(new PeriodoPrestamo(d.entregaProgramadaEn(), d.devolucionOriginalEn()), d.tarifaDiariaAcordada()));
  }

  @Override
  public boolean contactoVigente(UUID reservaId, UUID usuarioId) {
    return repository.findByReserva(reservaId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado para la reserva")).contactoVigentePara(usuarioId);
  }

  private Prestamo delParticipante(UUID actor, UUID prestamoId) {
    Prestamo prestamo = repository.findById(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado"));
    prestamo.exigirParticipante(actor);
    return prestamo;
  }

  /** El filtro usa los estados que ve el frontend: DEVOLUCION_REGISTRADA incluye PENDIENTE_INCIDENCIA. */
  private static Set<EstadoPrestamo> estados(String estado) {
    if (estado == null || estado.isBlank()) return TODOS;
    Set<EstadoPrestamo> elegidos = TODOS.stream().filter(e -> e.name().equals(estado) || e.publico().name().equals(estado)).collect(Collectors.toSet());
    if (elegidos.isEmpty()) throw new IllegalArgumentException("Estado de préstamo no válido");
    return elegidos;
  }

  private static PrestamoView vista(Prestamo prestamo, List<Map<String, Object>> evidencias) {
    return new PrestamoView(prestamo.datos(), prestamo.cambios().stream().map(CambioFecha::datos).toList(), evidencias);
  }
}
