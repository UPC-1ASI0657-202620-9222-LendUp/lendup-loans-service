# 0002. Superposición de una extensión con otra reserva

## Contexto

RF24 y RF25 piden que la nueva fecha no se superponga con otra reserva confirmada del mismo objeto. La agenda del objeto es de Reservas, que no ofrece una consulta ni un evento para ampliar un periodo (solo para acortarlo).

## Decisión

Cada reserva confirmada crea un préstamo, así que Préstamos ya conoce todas las reservas del objeto. La superposición se valida contra los préstamos en curso de la misma publicación, al solicitar y otra vez al aceptar.

## Alternativas

- Consultar a Reservas por REST: agrega acoplamiento síncrono y requiere un contrato que no existe.
- Pedir a Reservas que amplíe el periodo con un evento: es lo correcto a futuro, pero necesita acuerdo con ese equipo.

## Consecuencias

Hay consistencia eventual: si una reserva se confirma en el mismo instante en que se acepta una extensión, puede pasar la validación porque su evento aún no llegó. Además Reservas no bloquea el periodo extendido y podría aceptar una reserva en esos días. Queda como contrato pendiente con Reservas, que ya puede usar `prestamos.fecha-devolucion-actualizada.v1` para ampliarlo.
