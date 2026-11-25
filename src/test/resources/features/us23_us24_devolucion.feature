# language: es
Característica: US23 y US24 - Registrar y confirmar la devolución

  Escenario: El prestatario registra la devolución
    Dado un préstamo activo
    Cuando el prestatario registra la devolución
    Entonces el préstamo queda en estado "DEVOLUCION_REGISTRADA"
    Y se publica el evento "DevolucionRegistrada"

  Escenario: Un préstamo vencido también se puede devolver
    Dado un préstamo vencido
    Cuando el prestatario registra la devolución
    Entonces el préstamo queda en estado "DEVOLUCION_REGISTRADA"

  Escenario: El prestamista confirma y el préstamo finaliza
    Dado un préstamo con la devolución registrada
    Cuando el prestamista confirma la devolución
    Entonces el préstamo queda en estado "FINALIZADO"
    Y se publica el evento "PrestamoFinalizado"

  Escenario: No se finaliza con incidencias pendientes
    Dado un préstamo con la devolución registrada
    Y Evidencias reporta una incidencia
    Cuando el prestamista confirma la devolución
    Entonces la operación se rechaza con "Devolución o incidencias pendientes"

  Escenario: Al resolverse la incidencia se puede finalizar
    Dado un préstamo con la devolución registrada
    Y Evidencias reporta una incidencia
    Y Evidencias resuelve la incidencia
    Cuando el prestamista confirma la devolución
    Entonces el préstamo queda en estado "FINALIZADO"
