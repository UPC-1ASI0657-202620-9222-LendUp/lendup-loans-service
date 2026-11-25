# language: es
Característica: US20 y US44 - Consultar préstamos, vencimiento y calendario

  Escenario: Un préstamo vence al llegar su fecha vigente sin devolución
    Dado un préstamo activo
    Cuando llega la fecha vigente de devolución
    Entonces el préstamo queda en estado "VENCIDO"
    Y se publica el evento "PrestamoVencido"

  Escenario: Un préstamo con la fecha extendida no vence en la fecha original
    Dado un préstamo activo
    Y una extensión de 24 horas aceptada y pagada
    Cuando llega la fecha original de devolución
    Entonces el préstamo queda en estado "ACTIVO"

  Escenario: Solo los participantes consultan el préstamo
    Dado un préstamo activo
    Cuando un estudiante ajeno consulta el préstamo
    Entonces la operación se rechaza con "No participas en este préstamo"

  Escenario: El calendario muestra las fechas programadas
    Dado un préstamo activo
    Cuando el prestatario consulta su calendario
    Entonces ve 1 operación programada

  Escenario: Sin operaciones el calendario está vacío
    Dado un préstamo activo
    Cuando un estudiante ajeno consulta su calendario
    Entonces ve 0 operaciones programadas
