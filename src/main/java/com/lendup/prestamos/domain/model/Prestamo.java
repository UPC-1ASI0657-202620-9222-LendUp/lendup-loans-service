package com.lendup.prestamos.domain.model;

import com.lendup.prestamos.domain.event.PrestamoEvento;
import com.lendup.prestamos.domain.exception.OperacionNoPermitidaException;
import com.lendup.prestamos.domain.exception.ReglaDeNegocioException;
import com.lendup.prestamos.domain.state.PrestamoState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Prestamo Aggregate (Fig. 16): protege las reglas del ciclo del préstamo. Las transiciones las decide el patrón
 * State; el agregado agrega las reglas de participación, pago, garantía, fechas e incidencias, y registra los
 * eventos de dominio. Cada cambio incrementa {@code version}, que viaja como aggregate_version.
 */
public final class Prestamo {
  /** Estado completo, para reconstruir el agregado desde la base de datos y para exponerlo. */
  public record Datos(UUID id, UUID reservaId, UUID publicacionId, UUID prestamistaUsuarioId, UUID prestatarioUsuarioId, EstadoPrestamo estado,
                      LocalDateTime entregaProgramadaEn, LocalDateTime devolucionOriginalEn, LocalDateTime devolucionVigenteEn, BigDecimal tarifaDiariaAcordada,
                      BigDecimal garantiaMonetariaAcordada, String moneda, Instant pagoTarifaConfirmadoEn, Instant garantiaConstituidaEn, Instant entregaRegistradaEn,
                      Instant recepcionConfirmadaEn, Instant activadoEn, Instant devolucionRegistradaEn, Instant devolucionConfirmadaEn, int incidenciasPendientes,
                      Instant vencidoEn, Instant finalizadoEn, Instant canceladoEn, Instant creadoEn, Instant actualizadoEn, long version) {
    public boolean garantiaRequerida() { return garantiaMonetariaAcordada != null && garantiaMonetariaAcordada.signum() > 0; }
  }

  private final UUID id;
  private final UUID reservaId;
  private final UUID publicacionId;
  private final UUID prestamistaUsuarioId;
  private final UUID prestatarioUsuarioId;
  private final LocalDateTime entregaProgramadaEn;
  private final LocalDateTime devolucionOriginalEn;
  private final BigDecimal tarifaDiariaAcordada;
  private final BigDecimal garantiaMonetariaAcordada;
  private final String moneda;
  private final Instant creadoEn;
  private final List<CambioFecha> cambios;
  private final List<PrestamoEvento> eventos = new ArrayList<>();
  private EstadoPrestamo estado;
  private LocalDateTime devolucionVigenteEn;
  private Instant pagoTarifaConfirmadoEn;
  private Instant garantiaConstituidaEn;
  private Instant entregaRegistradaEn;
  private Instant recepcionConfirmadaEn;
  private Instant activadoEn;
  private Instant devolucionRegistradaEn;
  private Instant devolucionConfirmadaEn;
  private int incidenciasPendientes;
  private Instant vencidoEn;
  private Instant finalizadoEn;
  private Instant canceladoEn;
  private Instant actualizadoEn;
  private long version;

  private Prestamo(Datos d, List<CambioFecha> cambios) {
    id = d.id(); reservaId = d.reservaId(); publicacionId = d.publicacionId(); prestamistaUsuarioId = d.prestamistaUsuarioId();
    prestatarioUsuarioId = d.prestatarioUsuarioId(); estado = d.estado(); entregaProgramadaEn = d.entregaProgramadaEn();
    devolucionOriginalEn = d.devolucionOriginalEn(); devolucionVigenteEn = d.devolucionVigenteEn(); tarifaDiariaAcordada = d.tarifaDiariaAcordada();
    garantiaMonetariaAcordada = d.garantiaMonetariaAcordada(); moneda = d.moneda(); pagoTarifaConfirmadoEn = d.pagoTarifaConfirmadoEn();
    garantiaConstituidaEn = d.garantiaConstituidaEn(); entregaRegistradaEn = d.entregaRegistradaEn(); recepcionConfirmadaEn = d.recepcionConfirmadaEn();
    activadoEn = d.activadoEn(); devolucionRegistradaEn = d.devolucionRegistradaEn(); devolucionConfirmadaEn = d.devolucionConfirmadaEn();
    incidenciasPendientes = d.incidenciasPendientes(); vencidoEn = d.vencidoEn(); finalizadoEn = d.finalizadoEn(); canceladoEn = d.canceladoEn();
    creadoEn = d.creadoEn(); actualizadoEn = d.actualizadoEn(); version = d.version();
    this.cambios = new ArrayList<>(cambios);
  }

  public static Prestamo rehidratar(Datos datos, List<CambioFecha> cambios) { return new Prestamo(datos, cambios); }

  /** Una reserva confirmada crea el préstamo en estado RESERVADO (saga de reserva, paso 2). */
  public static Prestamo desdeReserva(UUID id, ReservaAcordada r, Instant now) {
    Prestamo prestamo = new Prestamo(new Datos(id, r.reservaId(), r.publicacionId(), r.prestamistaUsuarioId(), r.prestatarioUsuarioId(), EstadoPrestamo.RESERVADO,
        r.periodo().desde(), r.periodo().hasta(), r.periodo().hasta(), r.tarifaDiaria(), r.garantiaMonetaria(), r.moneda(), null, null, null, null, null, null, null,
        0, null, null, null, now, now, 1L), List.of());
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("entrega_programada_en", prestamo.entregaProgramadaEn);
    extra.put("devolucion_original_en", prestamo.devolucionOriginalEn);
    extra.put("tarifa_diaria_acordada", prestamo.tarifaDiariaAcordada);
    extra.put("garantia_requerida", prestamo.garantiaRequerida());
    if (prestamo.garantiaMonetariaAcordada != null) extra.put("garantia_monetaria_acordada", prestamo.garantiaMonetariaAcordada);
    extra.put("moneda", prestamo.moneda);
    prestamo.registrarEvento(PrestamoEvento.CREADO, extra);
    return prestamo;
  }

  // ---- Entrega y recepción (RF19, RF20) -------------------------------------------------------------------------

  public void registrarEntrega(UUID actor, Instant now) {
    exigirPrestamista(actor);
    EstadoPrestamo siguiente = state().registrarEntrega();
    if (pagoTarifaConfirmadoEn == null || (garantiaRequerida() && garantiaConstituidaEn == null)) {
      throw new ReglaDeNegocioException("Falta confirmar el pago o la garantía");
    }
    estado = siguiente; entregaRegistradaEn = now;
    actualizar(now);
    registrarEvento(PrestamoEvento.OBJETO_ENTREGADO, Map.of("entrega_registrada_en", now));
  }

  public void confirmarRecepcion(UUID actor, Instant now) {
    exigirPrestatario(actor);
    estado = state().confirmarRecepcion();
    recepcionConfirmadaEn = now; activadoEn = now;
    actualizar(now);
    registrarEvento(PrestamoEvento.ACTIVADO, Map.of("activado_en", now, "devolucion_vigente_en", devolucionVigenteEn));
  }

  // ---- Devolución y cierre (RF26, RF27) -------------------------------------------------------------------------

  public void registrarDevolucion(UUID actor, Instant now) {
    exigirPrestatario(actor);
    estado = state().registrarDevolucion(incidenciasPendientes > 0);
    devolucionRegistradaEn = now;
    actualizar(now);
    registrarEvento(PrestamoEvento.DEVOLUCION_REGISTRADA, Map.of("devolucion_registrada_en", now));
  }

  /** @param ahoraLocal hora de negocio (America/Lima), para saber si se devolvió antes del fin del periodo reservado. */
  public void confirmarDevolucion(UUID actor, Instant now, LocalDateTime ahoraLocal) {
    confirmarDevolucion(actor, now, ahoraLocal, null);
  }

  public void confirmarDevolucion(UUID actor, Instant now, LocalDateTime ahoraLocal, List<UUID> incidenciasResueltas) {
    exigirPrestamista(actor);
    EstadoPrestamo siguiente = state().confirmarDevolucion();
    if (incidenciasPendientes > 0) throw new ReglaDeNegocioException("Devolución o incidencias pendientes");
    estado = siguiente; devolucionConfirmadaEn = now; finalizadoEn = now;
    actualizar(now);
    var cierre = new LinkedHashMap<String, Object>();
    cierre.put("finalizado_en", now);
    if (incidenciasResueltas != null) cierre.put("incidencias_resueltas", incidenciasResueltas);
    registrarEvento(PrestamoEvento.FINALIZADO, cierre);
    if (ahoraLocal.isAfter(entregaProgramadaEn) && ahoraLocal.isBefore(devolucionVigenteEn)) {
      // Devolución anticipada: Reservas acorta el periodo ocupado (contrato prestamos.periodo-liberado.v1).
      registrarEvento(PrestamoEvento.PERIODO_LIBERADO, Map.of("nuevo_hasta", ahoraLocal.truncatedTo(ChronoUnit.SECONDS), "decision", "LIBERACION_CONFIRMADA",
          "devolucion_confirmada_por_prestamista", true, "incidencias_pendientes", false));
    }
  }

  /** RF53: vence si llegó la fecha vigente sin devolución. Devuelve false si todavía no corresponde. */
  public boolean vencer(Instant now, LocalDateTime ahoraLocal) {
    if (estado != EstadoPrestamo.ACTIVO || devolucionVigenteEn.isAfter(ahoraLocal)) return false;
    estado = state().vencer();
    vencidoEn = now;
    actualizar(now);
    registrarEvento(PrestamoEvento.VENCIDO, Map.of("vencido_en", now, "devolucion_vigente_en", devolucionVigenteEn));
    return true;
  }

  // ---- Extensiones y reprogramaciones (RF23, RF24, RF25) --------------------------------------------------------

  public BigDecimal cotizarExtension(LocalDateTime fechaPropuesta, PoliticaDeCosto politica) {
    exigirPosteriorALaVigente(fechaPropuesta);
    return politica.importe(new PeriodoPrestamo(devolucionVigenteEn, fechaPropuesta), tarifaDiariaAcordada);
  }

  public CambioFecha solicitarExtension(UUID cambioId, UUID actor, LocalDateTime fechaPropuesta, PoliticaDeCosto politica, Instant now, LocalDateTime ahoraLocal) {
    exigirPrestatario(actor);
    exigirCambioDeFecha(ahoraLocal);
    exigirSinCambioEnCurso();
    BigDecimal costo = cotizarExtension(fechaPropuesta, politica);
    CambioFecha cambio = CambioFecha.extension(cambioId, actor, devolucionVigenteEn, fechaPropuesta, costo, moneda, now);
    cambios.add(cambio);
    actualizar(now);
    registrarEvento(PrestamoEvento.EXTENSION_SOLICITADA, Map.of("cambio_fecha_prestamo_id", cambio.id(), "fecha_anterior_en", devolucionVigenteEn,
        "fecha_propuesta_en", cambio.fechaPropuestaEn(), "costo_adicional", cambio.costoAdicional(), "moneda", moneda));
    return cambio;
  }

  public CambioFecha responderExtension(UUID cambioId, UUID actor, boolean aceptar, String motivo, Instant now, LocalDateTime ahoraLocal) {
    return responderExtension(cambioId, actor, aceptar, motivo, now, ahoraLocal, false);
  }

  public CambioFecha responderExtension(UUID cambioId, UUID actor, boolean aceptar, String motivo, Instant now,
      LocalDateTime ahoraLocal, boolean coordinada) {
    exigirPrestamista(actor);
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    cambio.exigirPendiente();
    if (!aceptar) return rechazar(cambio, actor, motivo, now, PrestamoEvento.EXTENSION_RECHAZADA);
    exigirCambioDeFecha(ahoraLocal);
    if (coordinada) {
      cambio.esperarRetencion(actor, now);
      enviarComandoOcupacion(PrestamoEvento.EXTENSION_RETENCION_SOLICITADA, cambio, now);
      return cambio;
    }
    cambio.aceptarPendienteDePago(actor, now);
    actualizar(now);
    registrarEvento(PrestamoEvento.EXTENSION_ACEPTADA, Map.of("cambio_fecha_prestamo_id", cambio.id(), "costo_adicional", cambio.costoAdicional(), "moneda", moneda,
        "fecha_propuesta_en", cambio.fechaPropuestaEn()));
    return cambio;
  }

  public boolean habilitarPagoExtension(UUID cambioId, Instant expiraEn, Instant now) {
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    if (cambio.estado() != EstadoCambioFecha.ACEPTADA_PENDIENTE_RETENCION || !now.isBefore(expiraEn)) return false;
    cambio.habilitarPago();
    actualizar(now);
    registrarEvento(PrestamoEvento.EXTENSION_ACEPTADA, Map.of("cambio_fecha_prestamo_id", cambio.id(),
        "costo_adicional", cambio.costoAdicional(), "moneda", moneda, "fecha_propuesta_en", cambio.fechaPropuestaEn(),
        "retencion_expira_en", expiraEn.toString()));
    return true;
  }

  public boolean solicitarConfirmacionOcupacion(UUID cambioId, UUID transaccionId, Instant pagadoEn, Instant now) {
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    if (cambio.estado() != EstadoCambioFecha.ACEPTADA_PENDIENTE_PAGO
        || (estado != EstadoPrestamo.ACTIVO && estado != EstadoPrestamo.VENCIDO)) return false;
    cambio.esperarOcupacion(Objects.requireNonNull(transaccionId), pagadoEn);
    enviarComandoOcupacion(PrestamoEvento.EXTENSION_CONFIRMACION_SOLICITADA, cambio, now);
    return true;
  }

  public boolean confirmarOcupacionExtension(UUID cambioId, Instant now, LocalDateTime ahoraLocal) {
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    if (cambio.estado() != EstadoCambioFecha.PAGADA_PENDIENTE_OCUPACION) return false;
    return aplicarExtension(cambio, cambio.datos().transaccionPagoId(), cambio.datos().pagoConfirmadoEn(), now, ahoraLocal);
  }

  public boolean rechazarRetencionExtension(UUID cambioId, String motivo, Instant now) {
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    if (cambio.estado() == EstadoCambioFecha.PENDIENTE || !cambio.estado().enCurso()) return false;
    cambio.rechazarRetencion(motivo);
    actualizar(now);
    registrarEvento(PrestamoEvento.EXTENSION_RECHAZADA, Map.of("cambio_fecha_prestamo_id", cambioId, "motivo_rechazo", motivo));
    return true;
  }

  public void desistirRetencionesPorDevolucion(Instant now) {
    if (cambios.stream().anyMatch(c -> c.estado() == EstadoCambioFecha.PAGADA_PENDIENTE_OCUPACION)) {
      throw new ReglaDeNegocioException("La extensión pagada espera confirmación de ocupación");
    }
    for (CambioFecha cambio : cambios) {
      if (cambio.estado() == EstadoCambioFecha.ACEPTADA_PENDIENTE_RETENCION
          || cambio.estado() == EstadoCambioFecha.ACEPTADA_PENDIENTE_PAGO) {
        enviarComandoOcupacion(PrestamoEvento.EXTENSION_RETENCION_CANCELADA, cambio, now);
        rechazarRetencionExtension(cambio.id(), "DEVOLUCION_REGISTRADA", now);
      }
    }
  }

  private void enviarComandoOcupacion(String tipo, CambioFecha cambio, Instant now) {
    actualizar(now);
    registrarEvento(tipo, Map.of("cambio_fecha_prestamo_id", cambio.id(), "fecha_anterior_en",
        cambio.datos().fechaAnteriorEn(), "fecha_propuesta_en", cambio.fechaPropuestaEn()));
  }

  public CambioFecha proponerReprogramacion(UUID cambioId, UUID actor, LocalDateTime fechaPropuesta, Instant now, LocalDateTime ahoraLocal) {
    exigirPrestamista(actor);
    exigirCambioDeFecha(ahoraLocal);
    exigirSinCambioEnCurso();
    if (fechaPropuesta == null || !fechaPropuesta.isAfter(ahoraLocal) || fechaPropuesta.equals(devolucionVigenteEn)) {
      throw new IllegalArgumentException("La nueva fecha debe ser futura y distinta de la devolución vigente");
    }
    CambioFecha cambio = CambioFecha.reprogramacion(cambioId, actor, devolucionVigenteEn, fechaPropuesta, now);
    cambios.add(cambio);
    actualizar(now);
    registrarEvento(PrestamoEvento.REPROGRAMACION_PROPUESTA, Map.of("cambio_fecha_prestamo_id", cambio.id(), "fecha_anterior_en", devolucionVigenteEn,
        "fecha_propuesta_en", cambio.fechaPropuestaEn()));
    return cambio;
  }

  public CambioFecha responderReprogramacion(UUID cambioId, UUID actor, boolean aceptar, String motivo, Instant now, LocalDateTime ahoraLocal) {
    exigirPrestatario(actor);
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.REPROGRAMACION);
    cambio.exigirPendiente();
    if (!aceptar) return rechazar(cambio, actor, motivo, now, PrestamoEvento.REPROGRAMACION_RECHAZADA);
    exigirCambioDeFecha(ahoraLocal);
    if (!cambio.fechaPropuestaEn().isAfter(ahoraLocal)) throw new ReglaDeNegocioException("La fecha propuesta ya pasó");
    cambio.aceptarSinCosto(actor, now);
    moverDevolucion(cambio, now);
    return cambio;
  }

  /**
   * Pago de la extensión confirmado por Pagos: recién aquí cambia la fecha vigente (RF24). Devuelve false si el
   * cambio no espera un pago o el objeto ya se devolvió; en ese caso la fecha no cambia.
   */
  public boolean aplicarExtensionPagada(UUID cambioId, UUID transaccionId, Instant pagadoEn, Instant now, LocalDateTime ahoraLocal) {
    CambioFecha cambio = cambio(cambioId, TipoCambioFecha.EXTENSION);
    if (cambio.estado() != EstadoCambioFecha.ACEPTADA_PENDIENTE_PAGO || (estado != EstadoPrestamo.ACTIVO && estado != EstadoPrestamo.VENCIDO)) return false;
    return aplicarExtension(cambio, transaccionId, pagadoEn, now, ahoraLocal);
  }

  private boolean aplicarExtension(CambioFecha cambio, UUID transaccionId, Instant pagadoEn, Instant now, LocalDateTime ahoraLocal) {
    estado = state().aplicarNuevaFecha(cambio.fechaPropuestaEn().isAfter(ahoraLocal));
    if (estado == EstadoPrestamo.ACTIVO) vencidoEn = null;
    cambio.pagar(transaccionId, pagadoEn, now);
    moverDevolucion(cambio, now);
    return true;
  }

  // ---- Reacciones a Pagos, Reservas y Evidencias ----------------------------------------------------------------

  public boolean registrarTarifaPagada(Instant pagadoEn, Instant now) {
    if (pagoTarifaConfirmadoEn != null || !estado.abierto()) return false;
    pagoTarifaConfirmadoEn = pagadoEn;
    actualizar(now);
    return true;
  }

  public boolean registrarGarantiaConstituida(Instant constituidaEn, Instant now) {
    if (garantiaConstituidaEn != null || !estado.abierto()) return false;
    garantiaConstituidaEn = constituidaEn;
    actualizar(now);
    return true;
  }

  /**
   * Saga de cancelación, paso 2: si sigue RESERVADO se cancela; si el objeto ya se entregó, se rechaza. Repetir el
   * pedido sobre un préstamo ya cancelado vuelve a informar la cancelación (idempotente para Reservas).
   */
  public boolean decidirCancelacion(Instant now) {
    return decidirCancelacion(now, false);
  }

  public boolean decidirCancelacion(Instant now, boolean admiteRecuperacion) {
    if (estado == EstadoPrestamo.RECUPERACION_PENDIENTE) return false;
    if (admiteRecuperacion && estado == EstadoPrestamo.ENTREGA_REGISTRADA) {
      estado = EstadoPrestamo.RECUPERACION_PENDIENTE;
      actualizar(now);
      registrarEvento(PrestamoEvento.RECUPERACION_PENDIENTE, Map.of());
      return false;
    }
    if (estado == EstadoPrestamo.RESERVADO) {
      estado = state().cancelar();
      canceladoEn = now;
    }
    actualizar(now);
    if (estado == EstadoPrestamo.CANCELADO) {
      registrarEvento(PrestamoEvento.CANCELADO, Map.of("cancelado_en", canceladoEn));
      return true;
    }
    registrarEvento(PrestamoEvento.CANCELACION_RECHAZADA, Map.of("motivo",
        admiteRecuperacion ? "La recepción ya fue confirmada" : "El objeto ya fue entregado"));
    return false;
  }

  public void confirmarRecuperacion(UUID actor, Instant now) {
    exigirPrestamista(actor);
    if (estado == EstadoPrestamo.CANCELADO && entregaRegistradaEn != null) return;
    if (estado != EstadoPrestamo.RECUPERACION_PENDIENTE || incidenciasPendientes > 0) {
      throw new ReglaDeNegocioException("La recuperación no está pendiente o quedan incidencias abiertas");
    }
    estado = EstadoPrestamo.CANCELADO;
    canceladoEn = now;
    actualizar(now);
    registrarEvento(PrestamoEvento.CANCELADO, Map.of("cancelado_en", canceladoEn));
  }

  /** La reserva se canceló por fuera de la saga: solo tiene efecto si el préstamo no ha iniciado. */
  public boolean aplicarReservaCancelada(Instant now) {
    if (estado != EstadoPrestamo.RESERVADO) return false;
    return decidirCancelacion(now);
  }

  public boolean registrarIncidenciaReportada(Instant now) {
    if (!estado.abierto()) return false;
    incidenciasPendientes++;
    estado = state().reportarIncidencia();
    actualizar(now);
    return true;
  }

  public boolean registrarIncidenciaResuelta(Instant now) {
    if (incidenciasPendientes == 0) return false;
    incidenciasPendientes--;
    if (incidenciasPendientes == 0) estado = state().resolverIncidencias();
    actualizar(now);
    return true;
  }

  // ---- Consultas ------------------------------------------------------------------------------------------------

  public boolean esParticipante(UUID usuarioId) { return prestamistaUsuarioId.equals(usuarioId) || prestatarioUsuarioId.equals(usuarioId); }
  public void exigirParticipante(UUID usuarioId) {
    if (!esParticipante(usuarioId)) throw new OperacionNoPermitidaException("No participas en este préstamo");
  }
  /** El teléfono de la contraparte solo se muestra a los participantes mientras el préstamo está vigente (RF52). */
  public boolean contactoVigentePara(UUID usuarioId) { return esParticipante(usuarioId) && estado.abierto(); }
  public boolean garantiaRequerida() { return garantiaMonetariaAcordada != null && garantiaMonetariaAcordada.signum() > 0; }

  public Optional<CambioFecha> buscarCambio(UUID cambioId) { return cambios.stream().filter(c -> c.id().equals(cambioId)).findFirst(); }
  public List<CambioFecha> cambios() { return List.copyOf(cambios); }

  /** Entrega los eventos registrados desde la última lectura y los limpia. */
  public List<PrestamoEvento> extraerEventos() {
    List<PrestamoEvento> pendientes = List.copyOf(eventos);
    eventos.clear();
    return pendientes;
  }

  public Datos datos() {
    return new Datos(id, reservaId, publicacionId, prestamistaUsuarioId, prestatarioUsuarioId, estado, entregaProgramadaEn, devolucionOriginalEn, devolucionVigenteEn,
        tarifaDiariaAcordada, garantiaMonetariaAcordada, moneda, pagoTarifaConfirmadoEn, garantiaConstituidaEn, entregaRegistradaEn, recepcionConfirmadaEn, activadoEn,
        devolucionRegistradaEn, devolucionConfirmadaEn, incidenciasPendientes, vencidoEn, finalizadoEn, canceladoEn, creadoEn, actualizadoEn, version);
  }

  public UUID id() { return id; }
  public UUID reservaId() { return reservaId; }
  public UUID publicacionId() { return publicacionId; }
  public EstadoPrestamo estado() { return estado; }
  public LocalDateTime devolucionVigenteEn() { return devolucionVigenteEn; }
  public long version() { return version; }

  // ---- Reglas internas ------------------------------------------------------------------------------------------

  private PrestamoState state() { return PrestamoState.de(estado); }

  private CambioFecha cambio(UUID cambioId, TipoCambioFecha tipo) {
    return buscarCambio(cambioId).filter(c -> c.tipo() == tipo).orElseThrow(() -> new NoSuchElementException("Cambio de fecha no encontrado"));
  }

  private CambioFecha rechazar(CambioFecha cambio, UUID actor, String motivo, Instant now, String tipoEvento) {
    cambio.rechazar(actor, motivo, now);
    actualizar(now);
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("cambio_fecha_prestamo_id", cambio.id());
    if (cambio.datos().motivoRechazo() != null) extra.put("motivo_rechazo", cambio.datos().motivoRechazo());
    registrarEvento(tipoEvento, extra);
    return cambio;
  }

  /** Cambia la fecha vigente y lo publica: Notificaciones mueve el recordatorio y Reservas podrá ampliar el periodo. */
  private void moverDevolucion(CambioFecha cambio, Instant now) {
    LocalDateTime anterior = devolucionVigenteEn;
    devolucionVigenteEn = cambio.fechaPropuestaEn();
    actualizar(now);
    registrarEvento(PrestamoEvento.FECHA_DEVOLUCION_ACTUALIZADA, Map.of("cambio_fecha_prestamo_id", cambio.id(), "tipo", cambio.tipo().name(),
        "devolucion_anterior_en", anterior, "devolucion_vigente_en", devolucionVigenteEn));
  }

  private void exigirPrestamista(UUID actor) {
    exigirParticipante(actor);
    if (!prestamistaUsuarioId.equals(actor)) throw new OperacionNoPermitidaException("Solo el prestamista puede realizar esta operación");
  }

  private void exigirPrestatario(UUID actor) {
    exigirParticipante(actor);
    if (!prestatarioUsuarioId.equals(actor)) throw new OperacionNoPermitidaException("Solo el prestatario puede realizar esta operación");
  }

  private void exigirCambioDeFecha(LocalDateTime ahoraLocal) {
    state().exigirCambioDeFecha();
    if (!devolucionVigenteEn.isAfter(ahoraLocal)) throw new ReglaDeNegocioException("El préstamo ya llegó a su fecha de devolución");
  }

  private void exigirSinCambioEnCurso() {
    if (cambios.stream().anyMatch(c -> c.estado().enCurso())) throw new ReglaDeNegocioException("Ya hay un cambio de fecha en curso");
  }

  private void exigirPosteriorALaVigente(LocalDateTime fecha) {
    if (fecha == null || !fecha.isAfter(devolucionVigenteEn)) throw new IllegalArgumentException("La nueva fecha debe ser posterior a la devolución vigente");
  }

  private void actualizar(Instant now) { version = Math.incrementExact(version); actualizadoEn = now; }

  private void registrarEvento(String tipo, Map<String, Object> extra) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("prestamo_id", id);
    payload.put("reserva_id", reservaId);
    payload.put("publicacion_id", publicacionId);
    payload.put("prestatario_usuario_id", prestatarioUsuarioId);
    payload.put("prestamista_usuario_id", prestamistaUsuarioId);
    payload.putAll(extra);
    eventos.add(new PrestamoEvento(tipo, version, payload));
  }
}
