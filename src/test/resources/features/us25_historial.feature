# language: es
Característica: US25 - Consultar el historial de préstamos

  Escenario: El historial muestra los préstamos finalizados
    Dado un préstamo finalizado
    Cuando el prestatario consulta sus préstamos con estado "FINALIZADO"
    Entonces la lista tiene 1 préstamo

  Escenario: Un préstamo activo todavía no está en el historial
    Dado un préstamo activo
    Cuando el prestatario consulta sus préstamos con estado "FINALIZADO"
    Entonces la lista está vacía

  Escenario: El detalle conserva participantes, objeto, fechas y estado final
    Dado un préstamo finalizado
    Cuando el prestamista consulta el detalle del préstamo
    Entonces el detalle muestra participantes, objeto, fechas y estado "FINALIZADO"

  Escenario: Un estudiante ajeno no ve el historial de otros
    Dado un préstamo finalizado
    Cuando un estudiante ajeno consulta sus préstamos con estado "FINALIZADO"
    Entonces la lista está vacía
