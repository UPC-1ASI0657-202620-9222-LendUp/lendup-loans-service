package com.lendup.prestamos.application.service;

import com.lendup.prestamos.application.exception.NotFoundException;
import com.lendup.prestamos.application.port.in.LoanEventUseCases;
import com.lendup.prestamos.application.port.in.LoanUseCases;
import com.lendup.prestamos.application.port.in.LoanViews.*;
import com.lendup.prestamos.application.port.out.PrestamoRepository;
import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Préstamos Application Service (Fig. 16): coordina entrega, cambios de fecha, devolución y cierre. Cada operación
 * bloquea el préstamo, aplica las reglas del agregado y guarda estado, auditoría y eventos en una sola transacción.
 */
@Service
public class LoanApplicationService implements LoanUseCases, LoanEventUseCases {
  @org.springframework.beans.factory.annotation.Value("${lendup.extensions.coordinated:false}")
  private boolean coordinatedExtensions;
  @org.springframework.beans.factory.annotation.Value("${lendup.cancellations.recovery:false}")
  private boolean cancellationRecovery;
  @org.springframework.beans.factory.annotation.Value("${lendup.guarantees.deferred:false}")
  private boolean deferredGuarantees;
  @org.springframework.beans.factory.annotation.Autowired
  private com.lendup.prestamos.application.port.out.LoanIncidentProjection incidentProjection;
  private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);
  private final PrestamoRepository repository;
  private final LoanEventRecorder recorder;
  private final PoliticaDeCosto politica;
  private final BusinessClock clock;
  private final TransactionOperations newTransaction;

  public LoanApplicationService(PrestamoRepository repository, LoanEventRecorder recorder, PoliticaDeCosto politica, BusinessClock clock,
                                TransactionOperations newTransaction) {
    this.repository = repository; this.recorder = recorder; this.politica = politica; this.clock = clock; this.newTransaction = newTransaction;
  }

  // ---- Operaciones de los participantes -------------------------------------------------------------------------

  @Override @Transactional
  public PrestamoView registrarEntrega(UUID actor, UUID prestamoId, String correlationId) {
    return operar(actor, prestamoId, "REGISTRAR_ENTREGA", correlationId, (p, now) -> p.registrarEntrega(actor, now));
  }

  @Override @Transactional
  public PrestamoView confirmarRecepcion(UUID actor, UUID prestamoId, String correlationId) {
    return operar(actor, prestamoId, "CONFIRMAR_RECEPCION", correlationId, (p, now) -> p.confirmarRecepcion(actor, now));
  }

  @Override @Transactional
  public PrestamoView registrarDevolucion(UUID actor, UUID prestamoId, String correlationId) {
    return operar(actor, prestamoId, "REGISTRAR_DEVOLUCION", correlationId, (p, now) -> {
      p.registrarDevolucion(actor, now);
      if (coordinatedExtensions) p.desistirRetencionesPorDevolucion(now);
    });
  }

  @Override @Transactional
  public PrestamoView confirmarDevolucion(UUID actor, UUID prestamoId, String correlationId) {
    return operar(actor, prestamoId, "CONFIRMAR_DEVOLUCION", correlationId, (p, now) ->
        p.confirmarDevolucion(actor, now, clock.ahoraLocal(), deferredGuarantees ? incidentProjection.resolved(p.id()) : null));
  }

  @Override @Transactional
  public PrestamoView confirmarRecuperacion(UUID actor, UUID prestamoId, String correlationId) {
    return operar(actor, prestamoId, "CONFIRMAR_RECUPERACION", correlationId, (p, now) -> p.confirmarRecuperacion(actor, now));
  }

  @Override @Transactional
  public CambioFechaView solicitarExtension(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta, BigDecimal costoInformado, String correlationId) {
    Prestamo prestamo = bloquear(prestamoId);
    String anterior = prestamo.estado().name();
    LocalDateTime vigente = prestamo.devolucionVigenteEn();
    CambioFecha cambio = prestamo.solicitarExtension(UUID.randomUUID(), actor, fechaPropuesta, politica, clock.instant(), clock.ahoraLocal());
    if (costoInformado != null && costoInformado.compareTo(cambio.costoAdicional()) != 0) {
      throw new ReglaDeNegocioException("El costo de la extensión es " + cambio.costoAdicional().toPlainString() + " " + cambio.moneda());
    }
    exigirSinSuperposicion(prestamo, vigente, fechaPropuesta);
    recorder.guardar(prestamo, "SOLICITAR_EXTENSION", anterior, actor, correlationId);
    return new CambioFechaView(prestamoId, cambio.datos());
  }

  @Override @Transactional
  public CambioFechaView responderExtension(UUID actor, UUID prestamoId, UUID cambioId, String respuesta, String motivo, String correlationId) {
    boolean aceptar = respuesta(respuesta, "ACEPTADA_PENDIENTE_PAGO");
    Prestamo prestamo = bloquear(prestamoId);
    prestamo.exigirParticipante(actor);
    String anterior = prestamo.estado().name();
    CambioFecha cambio = prestamo.responderExtension(exigirCambio(prestamo, cambioId, TipoCambioFecha.EXTENSION),
        actor, aceptar, motivo, clock.instant(), clock.ahoraLocal(), coordinatedExtensions);
    if (aceptar && !coordinatedExtensions) exigirSinSuperposicion(prestamo, prestamo.devolucionVigenteEn(), cambio.fechaPropuestaEn());
    recorder.guardar(prestamo, aceptar ? "ACEPTAR_EXTENSION" : "RECHAZAR_EXTENSION", anterior, actor, correlationId);
    return new CambioFechaView(prestamoId, cambio.datos());
  }

  @Override @Transactional
  public CambioFechaView proponerReprogramacion(UUID actor, UUID prestamoId, LocalDateTime fechaPropuesta, String correlationId) {
    Prestamo prestamo = bloquear(prestamoId);
    String anterior = prestamo.estado().name();
    LocalDateTime vigente = prestamo.devolucionVigenteEn();
    CambioFecha cambio = prestamo.proponerReprogramacion(UUID.randomUUID(), actor, fechaPropuesta, clock.instant(), clock.ahoraLocal());
    exigirSinSuperposicion(prestamo, vigente, fechaPropuesta);
    recorder.guardar(prestamo, "PROPONER_REPROGRAMACION", anterior, actor, correlationId);
    return new CambioFechaView(prestamoId, cambio.datos());
  }

  @Override @Transactional
  public CambioFechaView responderReprogramacion(UUID actor, UUID prestamoId, UUID cambioId, String respuesta, String motivo, String correlationId) {
    boolean aceptar = respuesta(respuesta, "APLICADA");
    Prestamo prestamo = bloquear(prestamoId);
    prestamo.exigirParticipante(actor);
    String anterior = prestamo.estado().name();
    LocalDateTime vigente = prestamo.devolucionVigenteEn();
    CambioFecha cambio = prestamo.responderReprogramacion(exigirCambio(prestamo, cambioId, TipoCambioFecha.REPROGRAMACION), actor, aceptar, motivo, clock.instant(), clock.ahoraLocal());
    if (aceptar) exigirSinSuperposicion(prestamo, vigente, cambio.fechaPropuestaEn());
    recorder.guardar(prestamo, aceptar ? "ACEPTAR_REPROGRAMACION" : "RECHAZAR_REPROGRAMACION", anterior, actor, correlationId);
    return new CambioFechaView(prestamoId, cambio.datos());
  }

  // ---- Reacciones a eventos -------------------------------------------------------------------------------------

  @Override @Transactional
  public void crearDesdeReserva(ReservaConfirmadaCommand c, String correlationId) {
    if (repository.findByReserva(c.reservaId()).isPresent()) return;
    ReservaAcordada reserva = new ReservaAcordada(c.reservaId(), c.publicacionId(), c.prestamistaUsuarioId(), c.prestatarioUsuarioId(),
        new PeriodoPrestamo(c.desde(), c.hasta()), c.tarifaDiaria(), c.garantiaMonetaria(), c.moneda());
    Prestamo prestamo = Prestamo.desdeReserva(UUID.randomUUID(), reserva, clock.instant());
    recorder.guardar(prestamo, "CREAR_DESDE_RESERVA", null, null, correlationId);
  }

  @Override @Transactional
  public void decidirCancelacion(UUID reservaId, String correlationId) {
    Prestamo prestamo = bloquearPorReserva(reservaId);
    String anterior = prestamo.estado().name();
    boolean cancelado = prestamo.decidirCancelacion(clock.instant(), cancellationRecovery);
    if (prestamo.estado() == EstadoPrestamo.RECUPERACION_PENDIENTE) {
      recorder.guardar(prestamo, "ESPERAR_RECUPERACION", anterior, null, correlationId);
      return;
    }
    recorder.guardar(prestamo, cancelado ? "CANCELAR" : "RECHAZAR_CANCELACION", anterior, null, correlationId);
  }

  @Override @Transactional
  public void registrarReservaCancelada(UUID reservaId, String correlationId) {
    Prestamo prestamo = bloquearPorReserva(reservaId);
    String anterior = prestamo.estado().name();
    if (prestamo.aplicarReservaCancelada(clock.instant())) recorder.guardar(prestamo, "CANCELAR", anterior, null, correlationId);
  }

  @Override @Transactional
  public void registrarTarifaPagada(UUID prestamoId, Instant pagadoEn, String correlationId) {
    reaccionar(prestamoId, "REGISTRAR_TARIFA_PAGADA", correlationId, (p, now) -> p.registrarTarifaPagada(pagadoEn, now));
  }

  @Override @Transactional
  public void registrarGarantiaConstituida(UUID prestamoId, Instant constituidaEn, String correlationId) {
    reaccionar(prestamoId, "REGISTRAR_GARANTIA_CONSTITUIDA", correlationId, (p, now) -> p.registrarGarantiaConstituida(constituidaEn, now));
  }

  @Override @Transactional
  public void registrarExtensionPagada(ExtensionPagadaCommand c, String correlationId) {
    Prestamo prestamo = bloquear(c.prestamoId());
    String anterior = prestamo.estado().name();
    exigirCambio(prestamo, c.cambioFechaId(), TipoCambioFecha.EXTENSION);
    boolean changed = coordinatedExtensions
        ? prestamo.solicitarConfirmacionOcupacion(c.cambioFechaId(), c.transaccionId(), c.pagadoEn(), clock.instant())
        : prestamo.aplicarExtensionPagada(c.cambioFechaId(), c.transaccionId(), c.pagadoEn(), clock.instant(), clock.ahoraLocal());
    if (changed) {
      recorder.guardar(prestamo, "APLICAR_EXTENSION_PAGADA", anterior, null, correlationId);
      return;
    }
    // El objeto ya se devolvió o el cambio no esperaba un pago: no se altera la fecha y queda para revisión.
    log.warn("El pago de la extensión {} no cambia la fecha del préstamo {} (estado {})", c.cambioFechaId(), prestamo.id(), prestamo.estado());
    recorder.auditar(prestamo, "PAGO_EXTENSION_SIN_EFECTO", anterior, null, correlationId);
  }

  @Override @Transactional
  public void registrarResultadoRetencion(UUID prestamoId, UUID cambioId, String resultado, String detalle, String correlationId) {
    Prestamo prestamo = bloquear(prestamoId);
    String anterior = prestamo.estado().name();
    Instant now = clock.instant();
    boolean changed = switch (resultado) {
      case "RETENIDA" -> prestamo.habilitarPagoExtension(cambioId, Instant.parse(detalle), now);
      case "CONFIRMADA" -> prestamo.confirmarOcupacionExtension(cambioId, now, clock.ahoraLocal());
      case "RECHAZADA" -> prestamo.rechazarRetencionExtension(cambioId, detalle, now);
      default -> throw new IllegalArgumentException("Resultado de retención desconocido");
    };
    if (changed) recorder.guardar(prestamo, "EXTENSION_" + resultado, anterior, null, correlationId);
  }

  @Override @Transactional
  public void registrarPagoRechazado(UUID prestamoId, String tipo, String correlationId) {
    // El préstamo no cambia: la entrega sigue sin habilitarse (o la extensión sin aplicarse) y el prestatario puede reintentar.
    Prestamo prestamo = bloquear(prestamoId);
    recorder.auditar(prestamo, "PAGO_RECHAZADO_" + tipo, prestamo.estado().name(), null, correlationId);
  }

  @Override @Transactional
  public void registrarIncidenciaReportada(UUID prestamoId, String correlationId) {
    reaccionar(prestamoId, "REGISTRAR_INCIDENCIA_REPORTADA", correlationId, Prestamo::registrarIncidenciaReportada);
  }

  @Override @Transactional
  public void registrarIncidenciaResuelta(UUID prestamoId, String correlationId) {
    reaccionar(prestamoId, "REGISTRAR_INCIDENCIA_RESUELTA", correlationId, Prestamo::registrarIncidenciaResuelta);
  }

  @Override @Transactional
  public void proyectarIncidencia(UUID prestamoId, UUID incidenciaId, boolean resuelta, String correlationId) {
    Prestamo p = bloquear(prestamoId);
    if (!p.estado().abierto()) return;
    int delta = incidentProjection.project(prestamoId, incidenciaId, resuelta);
    if (delta == 0) return;
    String anterior = p.estado().name();
    if (delta > 0) p.registrarIncidenciaReportada(clock.instant());
    else p.registrarIncidenciaResuelta(clock.instant());
    recorder.guardar(p, "PROYECTAR_INCIDENCIA", anterior, null, correlationId);
  }

  @Override
  public int marcarVencidos(int limite) {
    List<UUID> candidatos = repository.findActivosVencidos(clock.ahoraLocal(), limite);
    int vencidos = 0;
    for (UUID id : candidatos) {
      try {
        // Una transacción por préstamo: si uno falla, los demás igual se marcan.
        if (Boolean.TRUE.equals(newTransaction.execute(status -> marcarVencido(id)))) vencidos++;
      } catch (RuntimeException e) {
        log.error("No se pudo marcar como vencido el préstamo {}", id, e);
      }
    }
    return vencidos;
  }

  private boolean marcarVencido(UUID id) {
    Prestamo prestamo = bloquear(id);
    String anterior = prestamo.estado().name();
    if (!prestamo.vencer(clock.instant(), clock.ahoraLocal())) return false;
    recorder.guardar(prestamo, "MARCAR_VENCIDO", anterior, null, "vencimiento-" + id);
    return true;
  }

  // ---- Apoyo ----------------------------------------------------------------------------------------------------

  private PrestamoView operar(UUID actor, UUID prestamoId, String accion, String correlationId, BiConsumer<Prestamo, Instant> operacion) {
    Prestamo prestamo = bloquear(prestamoId);
    String anterior = prestamo.estado().name();
    operacion.accept(prestamo, clock.instant());
    recorder.guardar(prestamo, accion, anterior, actor, correlationId);
    return new PrestamoView(prestamo.datos(), prestamo.cambios().stream().map(CambioFecha::datos).toList(), List.of());
  }

  private interface Reaccion { boolean aplicar(Prestamo prestamo, Instant now); }

  private void reaccionar(UUID prestamoId, String accion, String correlationId, Reaccion reaccion) {
    Prestamo prestamo = bloquear(prestamoId);
    String anterior = prestamo.estado().name();
    if (reaccion.aplicar(prestamo, clock.instant())) recorder.guardar(prestamo, accion, anterior, null, correlationId);
  }

  private Prestamo bloquear(UUID prestamoId) { return repository.lock(prestamoId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado")); }
  private Prestamo bloquearPorReserva(UUID reservaId) { return repository.lockByReserva(reservaId).orElseThrow(() -> new NotFoundException("Préstamo no encontrado para la reserva")); }

  private static UUID exigirCambio(Prestamo prestamo, UUID cambioId, TipoCambioFecha tipo) {
    return prestamo.buscarCambio(cambioId).filter(c -> c.tipo() == tipo).map(CambioFecha::id).orElseThrow(() -> new NotFoundException("Cambio de fecha no encontrado"));
  }

  private void exigirSinSuperposicion(Prestamo prestamo, LocalDateTime vigente, LocalDateTime propuesta) {
    if (propuesta.isAfter(vigente) && repository.existeOtroEnPeriodo(prestamo.publicacionId(), prestamo.id(), vigente, propuesta)) {
      throw new ReglaDeNegocioException("La nueva fecha se superpone con otra reserva confirmada del objeto");
    }
  }

  private static boolean respuesta(String valor, String aceptada) {
    if (aceptada.equals(valor)) return true;
    if ("RECHAZADA".equals(valor)) return false;
    throw new IllegalArgumentException("Respuesta no válida: use " + aceptada + " o RECHAZADA");
  }
}
