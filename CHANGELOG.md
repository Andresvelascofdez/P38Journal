# Cambios

## 3.0 — 11/10/2026

Referencia operativa explícita 175.000 km. Eliminado mantenimiento inicial automático por falta de historial. Calendario, registro, alertas y gastos como recorrido principal. Inspecciones de servicio agrupadas; controles rutinarios fuera del plan/avisos por defecto. Detalle con próximo límite/faltante y fuentes secundarias. Migración sobre app existente, conservando datos/firma. Verificación de aviso real al actualizar km.

Sustituye los criterios contradictorios de v2, que exigía una revisión inicial y ofrecía demasiadas tareas/textos técnicos. No implica que el coche recibiera todos los servicios a 175.000 km.
# Implementación verificada 3.0.0 — 11/10/2026

- Plan persistido desde 175.000 km; referencia temporal explícita editable y ciclos posteriores desde operaciones reales, sin historial fabricado.
- Room esquema 3 con respaldo previo verificado, conservación de datos y retiro exclusivo de derivados automáticos antiguos; ZIP v2/v3 e importación idempotente.
- Inicio/Mantenimiento/Historial/Gastos, revisión agrupada y parcial, detalle amigable y referencias/fuentes secundarias. Catálogo revisado de 62 reglas con cobertura de 59 filas del fabricante.
- Avisos reales al guardar km, WorkManager por fecha, deduplicación y actualización silenciosa del resumen parcial; gastos exactos.
- APK release y debug 3.0.0 firmados, identidad/certificado release conservados. 32 unitarias y 48 Android aprobadas; actualización, restauración, avisos offline y reinicio verificados. Evidencias en docs/TEST_REPORT.md.

## Especificación recibida
