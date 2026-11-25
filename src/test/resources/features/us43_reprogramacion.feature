# language: es
Característica: US43 - Reprogramar la devolución por parte del prestamista

  Escenario: La propuesta no cambia la fecha hasta que se acepta
    Dado un préstamo activo
    Cuando el prestamista propone devolver 6 horas después
    Entonces la reprogramación queda en estado "PENDIENTE" sin costo
    Y la fecha vigente de devolución no cambia

  Escenario: Al aceptarla cambia la fecha sin cobro
    Dado un préstamo activo
    Y el prestamista propone devolver 6 horas después
    Cuando el prestatario acepta la reprogramación
    Entonces la reprogramación queda en estado "APLICADA" sin costo
    Y la fecha vigente de devolución avanza 6 horas
    Y no se publica el evento "ExtensionAceptada"

  Escenario: Al rechazarla la fecha se mantiene
    Dado un préstamo activo
    Y el prestamista propone devolver 6 horas después
    Cuando el prestatario rechaza la reprogramación
    Entonces la reprogramación queda en estado "RECHAZADA" sin costo
    Y la fecha vigente de devolución no cambia
