# language: es
Característica: US21, US22 y US42 - Extensiones del préstamo

  Escenario: El costo se calcula por bloques iniciados de 24 horas
    Dado un préstamo activo
    Cuando el prestatario solicita extender 25 horas
    Entonces la extensión queda en estado "PENDIENTE" con un costo de 40.00
    Y la fecha vigente de devolución no cambia

  Escenario: Al aceptar, la extensión queda pendiente de pago
    Dado un préstamo activo
    Y el prestatario solicita extender 24 horas
    Cuando el prestamista acepta la extensión
    Entonces la extensión queda en estado "ACEPTADA_PENDIENTE_PAGO" con un costo de 20.00
    Y se publica el evento "ExtensionAceptada"
    Y la fecha vigente de devolución no cambia

  Escenario: El pago confirmado actualiza la fecha vigente y conserva la original
    Dado un préstamo activo
    Y el prestatario solicita extender 24 horas
    Y el prestamista acepta la extensión
    Cuando Pagos confirma la extensión
    Entonces la extensión queda en estado "APLICADA" con un costo de 20.00
    Y la fecha vigente de devolución avanza 24 horas

  Escenario: El rechazo queda en el historial
    Dado un préstamo activo
    Y el prestatario solicita extender 24 horas
    Cuando el prestamista rechaza la extensión
    Entonces la extensión queda en estado "RECHAZADA" con un costo de 20.00
    Y el historial tiene 1 cambio de fecha

  Escenario: No se puede superponer con otra reserva del objeto
    Dado un préstamo activo
    Y otra reserva confirmada del mismo objeto 3 horas después
    Cuando el prestatario solicita extender 24 horas
    Entonces la operación se rechaza con "La nueva fecha se superpone con otra reserva confirmada del objeto"

  Escenario: Un préstamo vencido no admite extensiones
    Dado un préstamo vencido
    Cuando el prestatario solicita extender 24 horas
    Entonces la operación se rechaza con "Solo un préstamo activo admite cambios de fecha"
