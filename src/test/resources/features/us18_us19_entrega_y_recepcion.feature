# language: es
Característica: US18 y US19 - Confirmar la entrega y la recepción del objeto

  Escenario: No se puede entregar sin el pago confirmado
    Dado un préstamo reservado con garantía de 50.00
    Cuando el prestamista registra la entrega
    Entonces la operación se rechaza con "Falta confirmar el pago o la garantía"
    Y el préstamo queda en estado "RESERVADO"

  Escenario: No se puede entregar si falta la garantía
    Dado un préstamo reservado con garantía de 50.00
    Y Pagos confirma la tarifa
    Cuando el prestamista registra la entrega
    Entonces la operación se rechaza con "Falta confirmar el pago o la garantía"

  Escenario: Entrega con pago y garantía confirmados
    Dado un préstamo reservado con garantía de 50.00
    Y Pagos confirma la tarifa
    Y Pagos confirma la garantía
    Cuando el prestamista registra la entrega
    Entonces el préstamo queda en estado "ENTREGA_REGISTRADA"
    Y se publica el evento "ObjetoEntregado"

  Escenario: Sin garantía acordada basta el pago de la tarifa
    Dado un préstamo reservado sin garantía
    Y Pagos confirma la tarifa
    Cuando el prestamista registra la entrega
    Entonces el préstamo queda en estado "ENTREGA_REGISTRADA"

  Escenario: El prestatario no puede registrar la entrega
    Dado un préstamo listo para entregar
    Cuando el prestatario registra la entrega
    Entonces la operación se rechaza con "Solo el prestamista puede realizar esta operación"

  Escenario: La recepción activa el préstamo
    Dado un préstamo con la entrega registrada
    Cuando el prestatario confirma la recepción
    Entonces el préstamo queda en estado "ACTIVO"
    Y se publica el evento "PrestamoActivado"

  Escenario: No se puede confirmar la recepción antes de la entrega
    Dado un préstamo listo para entregar
    Cuando el prestatario confirma la recepción
    Entonces la operación se rechaza con "Falta registrar la entrega"
