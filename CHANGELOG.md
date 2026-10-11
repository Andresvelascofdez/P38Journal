# 4.1.0 / código5

Mantenimiento abre revisiones completas proyectadas, con contador y detalle sobre las mismas apariciones;29/33/31 derivados de reglas. Vencimientos reales para avisos y registro separados. Registro parcial exacto y edición conservados, versión/build visibles. Pruebas de interfaz de cada revisión y actualizaciones en emulador; evidencia final en docs/TEST_REPORT.md.

# Cambios

## 4.0 — 11/10/2026

Separados vencimientos reales y previsión condicional de cuatro hitos, con fases B/C y operaciones adicionales propias de cada plazo. Detalle por operación, explicación del primer desfase, personalización y restauración del fabricante. Creación propia recurrente o única sin URL; recordatorios individuales opcionales. Visitas nuevas sin selección automática y confirmación de fecha/km/operaciones; búsqueda también por operaciones, taller y notas.

Filtros combinables de gastos por mes/año/tipo, selección persistente, subtotal filtrado y total activo con céntimos enteros. Normalización auditada de enlaces inactivos, preservación de metadatos personalizados del pago y reparto histórico al editar visitas. Actualización de generación de avisos sin repetir ciclos v3 ya emitidos; recordatorio opcional de odómetro recuperado.

Versión 4.0.0/code 4, Room esquema 3, datos y firma conservados. Catálogo canónico de 62 reglas/revisión 3 sin cambios de frecuencias. Encargo completo en REQUEST_V4.md; pruebas reales de esta entrega en docs/TEST_REPORT.md. Fuente y artefactos se entregan localmente, sin publicación del encargo v4.

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
