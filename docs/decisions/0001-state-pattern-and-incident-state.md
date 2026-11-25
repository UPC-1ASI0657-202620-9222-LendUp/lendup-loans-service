# 0001. Patrón State y estado PENDIENTE_INCIDENCIA

## Contexto

El informe modela el ciclo del préstamo con el patrón State (Tabla 7, patrón 24) y la Fig. 9 incluye `PENDIENTE_INCIDENCIA`. El monolito usaba condicionales y no tenía ese estado: solo el contador `incidencias_pendientes`. El frontend tampoco lo conoce y mostraría un estado desconocido como "por entregar".

## Decisión

- Cada estado es una clase que implementa `PrestamoState`. Toda acción está prohibida por defecto y cada estado habilita solo las suyas.
- `PENDIENTE_INCIDENCIA` se implementa en el dominio y en la base: es "objeto devuelto con incidencias sin resolver". Al resolverse todas vuelve a `DEVOLUCION_REGISTRADA`.
- La API lo muestra como `DEVOLUCION_REGISTRADA` en `estado` y deja el valor real en `estado_detalle`. El filtro `?estado=DEVOLUCION_REGISTRADA` incluye ambos.

## Consecuencias

El frontend no cambia y el modelo coincide con el informe. Cuando el frontend quiera distinguirlo, puede leer `estado_detalle`.
