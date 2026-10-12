# P38 Journal — alcance aprobado5.0 / build6

REQUEST_V6.md contiene el encargo vigente completo y sustituye exclusiones anteriores de consumo. Se amplía la app Android local existente para un solo P38, sin rehacer mantenimiento ni navegación inferior. El usuario instala y prueba los APK generados.

## Capacidades conservadas

Referencia operativa175.000 km separada del odómetro manual y trabajos. Mantenimiento del fabricante, fases B/C, próximos vencimientos reales, proyección completa de revisiones desde reglas y detalles/contadores consistentes. Registro parcial, edición/anulación, avisos locales y filtros de gastos por mes/año/tipo. Manuales/documentos importados, adjuntos y copias existentes conservados.

## Trabajos y mejoras

Vistas Previstos/Realizados; alta directa, edición, anulación confirmada y conversión. Visit es identidad canónica, también visible en Historial al realizar; no duplicados. Nombre libre/notas, fecha/km reales cuando conocidos; desconocidos explícitos sin fechas ficticias. Objetivo/estimado opcionales en previstos; el estimado no genera gasto. Relación opcional con un gasto real consultable en ambos sentidos. Solo la vinculación expresa con mantenimiento y datos suficientes genera Event, sin nuevas periodicidades.

## Documentación y vencimientos

Entradas de nombre libre, fecha, notas, aviso activado/antelación y repetición puntual/configurable. Seguro/Road tax/MOT sugeridos, sin obligaciones sembradas. Próximos y vencidos; renovación manual guarda fecha anterior y exige nueva fecha confirmada. Repetición solo sugiere, no declara vigente ni crea gasto. Avisos locales separados del fabricante, destinos correctos, cancelar/reprogramar al editar/renovar/desactivar.

## Inicio y navegación

Cuatro pestañas conservadas. Acciones compactas: kilómetros, mantenimiento realizado, gasto, repostaje y trabajo/mejora previsto o realizado. Accesos sencillos a documentación y ruedas por navegación secundaria. Inicio muestra únicamente consumo medio y acceso a repostajes, junto al calendario habitual.

## Repostajes y consumo sencillo

Fecha, km, litros, importe total y lleno; editar/anular. Exactamente un gasto gasolina por repostaje, visible desde ambos. Gastos gasolina anteriores siguen sin datos de consumo inventados. Dinero céntimos y litros mililitros; coma/punto.

Primer lleno referencia sin incluir sus litros. Cada cierre completo suma litros posteriores parciales+lleno final y usa distancia positiva entre llenos. Media acumulada ponderada por distancias, nunca media simple de tasas. Parciales abiertos pendientes; tramos incompletos no calculados. Mostrar Datos insuficientes si no hay tramo válido. Detalle con período/km usados, sin gráficos/autonomía/informes. Editar/anular recalcula. Km de repostaje solo actualiza lectura mediante opción expresa válida, nunca silenciosamente ni al registrar histórico.

## Ruedas

Dos ejes: delanteras/traseras. Montaje/km de aviso configurables por separado, nota opcional, uso desde montaje y restante. Montajes introducidos por usuario, sin175.000 automático ni sustituciones ficticias. Revisadas registra km y próximo intervalo sin resetear uso; Cambiadas archiva juego anterior y arranca desde km reales; Cambiar ambas una operación/visita y un solo gasto si existe. Corregir/anular datos recalcula por secuencia persistida. Aviso al cruce manual: revisar el eje y valorar cambio, sin declarar desgaste o sustitución automática.

## Compatibilidad y entrega

Room4 no destructivo; copias2/3/4, todas relaciones nuevas y anteriores. Identidad/firma original. Version5.0.0/code6 visible. Probar contratos, UI real en emulador disponible, actualización/restore, notificaciones y regresión del fabricante. Documentar resultados y bloqueos concretos en TEST_REPORT. Fuera de alcance la lista final de REQUEST_V6; no añadir ideas ajenas.
