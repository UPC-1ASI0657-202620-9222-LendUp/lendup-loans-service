# language: es
Característica: US45 - Cancelar una reserva antes de la entrega (saga coreografiada, paso de Préstamos)

  Escenario: Se cancela si el objeto no se ha entregado
    Dado un préstamo listo para entregar
    Cuando Reservas solicita cancelar la reserva
    Entonces el préstamo queda en estado "CANCELADO"
    Y se publica el evento "PrestamoCancelado"

  Escenario: Se rechaza si el objeto ya se entregó
    Dado un préstamo con la entrega registrada
    Cuando Reservas solicita cancelar la reserva
    Entonces el préstamo queda en estado "ENTREGA_REGISTRADA"
    Y se publica el evento "CancelacionRechazada"

  Escenario: Un pedido repetido no rompe la saga
    Dado un préstamo listo para entregar
    Y Reservas solicita cancelar la reserva
    Cuando Reservas solicita cancelar la reserva
    Entonces el préstamo queda en estado "CANCELADO"
