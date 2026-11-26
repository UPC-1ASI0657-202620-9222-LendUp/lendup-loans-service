# language: es
Característica: Coordinar extensión y cancelación

  Escenario: La extensión pagada espera confirmación de ocupación
    Dado la coordinación de extensiones está activa
    Y un préstamo activo
    Cuando el prestatario solicita extender 24 horas
    Y el prestamista acepta la extensión
    Entonces la extensión queda en estado "ACEPTADA_PENDIENTE_RETENCION" con un costo de 20.00
    Y no se publica el evento "ExtensionAceptada"
    Cuando Reservas confirma la retención vigente
    Y Pagos confirma la extensión
    Entonces la fecha vigente de devolución no cambia
    Cuando Reservas consolida la ocupación de la extensión
    Entonces la fecha vigente de devolución avanza 24 horas

  Escenario: El objeto entregado debe recuperarse antes de cancelar
    Dado la cancelación admite confirmación de recuperación
    Y un préstamo con la entrega registrada
    Cuando Reservas solicita cancelar la reserva
    Entonces el préstamo queda en estado "RECUPERACION_PENDIENTE"
    Y no se publica el evento "PrestamoCancelado"
    Cuando el prestamista confirma que recuperó el objeto
    Entonces el préstamo queda en estado "CANCELADO"
    Y se publica el evento "PrestamoCancelado"

  Escenario: Una vez recibida la entrega solo queda el flujo de devolución
    Dado la cancelación admite confirmación de recuperación
    Y un préstamo activo
    Cuando Reservas solicita cancelar la reserva
    Entonces el préstamo queda en estado "ACTIVO"
    Y se publica el evento "CancelacionRechazada"
