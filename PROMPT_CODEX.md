# Corrección de la app Android existente — versión 3

La app construida no refleja lo que quiero: muestra muchas comprobaciones iniciales y detalles técnicos difíciles de leer. Corrígela sobre el proyecto actual. Esta especificación sustituye la anterior, especialmente la obligación de crear mantenimiento inicial por falta de historial. No borres código/datos, no cambies la firma y no me pidas rehacer el proyecto.

## Objetivo exacto

Mi Range Rover P38 4.6 V8 Autobiography es del año 2000, gasolina y automático. La referencia inicial para planificar es **175.000 km**. Quiero que la app me diga qué debo hacer al coche cada cierto número de kilómetros o plazo según el fabricante, que yo actualice los km manualmente y registre lo realizado, y que reciba avisos cuando se acerque o alcance un límite. También quiero gastos manuales y ver cuánto llevo gastado.

Los intervalos y operaciones provienen del fabricante; los 175.000 km son mi decisión de referencia operativa, no prueba de que todo se hizo entonces. No crees servicios ficticios ni me obligues a confirmar/revisar el pasado para que funcione el plan. No redondees al siguiente múltiplo absoluto del odómetro si la operación es recurrente desde una referencia.

## Cambios de comportamiento

1. Elimina la generación de «Mantenimiento inicial» y sus tarjetas/avisos por falta de historial. Deja de convertir datos desconocidos en acciones urgentes. Limpia de forma idempotente los pendientes automáticos antiguos, conservando eventos, gastos y tareas realmente creados/registrados por mí. Sigue MIGRATION.md.
2. Para una regla recurrente por componente sin trabajo posterior válido, próximo km = 175.000 + intervalo verificado. Tras registrar un trabajo que satisfaga esa regla, próximo km = km reales de ese trabajo + intervalo, si ese es el modelo de recurrencia del fabricante. Editar/anular recalcula correctamente. Los hitos únicos y fases deben conservar su semántica; no sumar sin revisar todas las reglas.
3. Conserva los plazos por tiempo prescritos. La fecha del plan es una referencia operativa editable en Ajustes, por defecto la fecha de creación del plan recuperable, o la de esta migración si no existe; no es fecha de compra ni de servicio. No pedir un cuestionario al inicio. Mostrar fecha y km cuando procedan, con «lo que ocurra antes». Una regla dependiente de antigüedad original conserva su base documental propia.
4. Los controles de una revisión de taller aparecen agrupados dentro de la revisión a su vencimiento. No mostrar en Inicio decenas de comprobaciones diarias/semanales, requisitos de aplicabilidad, pasos de desmontaje ni tareas de investigar. Mantener esas recomendaciones en el manual si sirven, sin alertas por defecto. No eliminar un mantenimiento real del fabricante por ser una inspección: situarlo dentro de su revisión correspondiente.
5. La app viene con catálogo completo aplicable. Reutiliza y audita el catálogo actual; corrige reglas erróneas desde SOURCES.md, matriz visual y notas. No reemplazarlo por solo el starter ni inventar intervalos. Documenta condiciones por versión/VIN sin exponer códigos internos como texto de interfaz.

## Interfaz requerida

Inicio / Mantenimiento / Historial / Gastos. Ajustes y Manuales por acceso secundario. Inicio corto: km actual, actualizar km, siguiente revisión y próximas/vencidas. Si nada toca, «Todo al día» y próximo límite, no llenar con tareas iniciales. Lista compacta con título comprensible, próximo km/fecha y cuánto falta; agrupar servicios con mismo límite sin ocultar sus operaciones.

Detalle: título concreto («Cambio de aceite y filtro»), intervalo, próximo límite, kilómetros restantes/fecha, última ejecución si existe, botón «Registrar realizado». Sección «Qué incluye» para revisar componentes y «Ver fuente del fabricante» desplegable. No mostrar known_pre_purchase_replaced, recurrence_basis, ids, glosarios largos ni frases «por hito documentado»/«establecer referencia operativa explícita». Ver SPEC.md.

Registrar desde una tarea precarga operación, fecha actual y km actuales editables. Taller, notas, coste y adjunto opcionales. Confirmar lo realmente hecho; un gasto o cita no completa trabajos. Una revisión agrupada permite seleccionar operaciones realizadas: no cerrar tareas que no se hicieron.

Gastos Taller/Extras/Gasolina/Otros: fecha, importe EUR, concepto, categoría; nota/recibo opcionales. Lista, editar/eliminar y total «Lo que llevo gastado». Sin informes, gráficos ni consumo, y sin exigir asociación a mantenimiento. Un pago de taller cuenta una sola vez.

## Notificaciones que quiero recibir

Avisos del sistema Android, visibles con la interfaz cerrada, permiso/canales y botón de prueba. Al guardar kilómetros, evaluar inmediatamente: si una regla pasa de futura a vencida, crear aviso del sistema y entrada en bandeja. No limitarse a pintar un badge ni esperar al próximo worker. Con lectura saltando varios límites, resumen con todas las operaciones pendientes, sin perder tareas ni repetir avisos por abrir la app.

Para fechas, WorkManager persiste la evaluación sin Internet y sin depender de abrir la pantalla. Preaviso por defecto 1.000 km/30 días cuando resulte proporcionado, configurable. Vencimiento y recordatorio moderado de pendientes, sin avisos iniciales inventados. Registrar realizado cancela aviso antiguo y programa siguiente ciclo. ENGINE.md define deduplicación y causas. No implantar un servidor solo porque llamo «push» a estas notificaciones.

## Ejecución y entrega

Usa agentes disponibles para dominio/migración, UI, catálogo/avisos y revisión independiente. Inspecciona antes de editar, define contratos/archivos y corrige todos los hallazgos. Si no hay subagentes, informa y ejecuta secuencialmente. Conserva stack Android, identidad y clave de firma actuales; no conviertas a web ni añadas infraestructura.

Haz copia antes de migrar. Prueba especialmente arranque sin pendientes iniciales, cálculo desde 175.000, actualizar km → aviso real, registrar trabajo → próximo límite, edición/anulación, gastos y actualización del APK conservando registros. Entrega APK release firmado actualizado, debug separado, informe honesto y guía breve. No me pidas escribir código, compilar ni configurar los intervalos uno a uno. No afirmar pruebas físicas si no se hicieron. Documenta bloqueos concretos y completa el trabajo independiente.
