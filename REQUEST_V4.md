Trabaja sobre el repositorio existente https://github.com/Andresvelascofdez/P38Journal y corrige la app Android. Implementa los cambios, verifica lo necesario y entrega una APK. No te quedes en una propuesta.

CONTEXTO Y FORMA DE TRABAJO

La app es para mi Range Rover P38 Autobiography 4.6 V8 gasolina automático del año 2000. Quiero saber qué mantenimiento toca, registrar lo realizado, actualizar manualmente los kilómetros y guardar gastos.

La referencia operativa del plan es 175.000 km. Consérvala separada del odómetro actual y de los trabajos reales. No significa que se hicieran todas las operaciones a esos kilómetros. Conserva la fecha de referencia configurada; no inventes una fecha de compra. No vuelvas a introducir una puesta a cero, comprobaciones iniciales o tareas pendientes por desconocer el historial anterior.

Primero revisa el estado actual, AGENTS.md y los documentos del proyecto. Esta petición amplía y corrige el alcance anterior: los filtros de gastos por mes/año/tipo están autorizados, aunque algún documento prohíba “análisis por meses”. Actualiza los documentos para distinguir filtros de consulta de informes, que siguen fuera del alcance.

Utiliza agentes de implementación y revisión conforme a AGENTS.md, con responsabilidades claras. Haz las decisiones técnicas rutinarias y el trabajo de forma autónoma. Mi participación será instalar y probar. Conserva Kotlin, Compose, Room, WorkManager, applicationId y compatibilidad con datos, copias y firma existentes. No rehagas la app desde cero ni borres datos. No publiques ni contrates servicios.

DIAGNÓSTICO QUE DEBES COMPROBAR

La revisión del commit a842a6ca2d46743bc32609a00e9546c6f2419cef encontró lo siguiente. Contrástalo con la versión actual antes de modificar:

- Engine genera el próximo vencimiento individual de cada operación. ui/MaintenanceGroups.kt agrupa esas tareas por serviceGroup + dueDate + dueKm. Esto muestra grupos de próximos vencimientos, pero la interfaz los presenta como revisiones futuras completas.
- Con el catálogo y referencia de 175.000 km, inicialmente aparecen 29 operaciones a 195.000 km y solo 6 a 215.000 km. Si se registran las 29 primeras en su límite previsto, las 27 operaciones comunes pasan al grupo de 215.000 km, que entonces contiene 33. No parece una pérdida permanente de recurrencia: es una previsión incompleta y confusa.
- No todas las 29 operaciones deben repetirse cada 20.000 km. Las dos de fase B empiezan a +20.000 km/12 meses y repiten cada 40.000 km/24 meses. Las de fase C empiezan a +40.000 km/24 meses y repiten cada 40.000 km/24 meses. Las comunes a B y C repiten cada 20.000 km/12 meses. Respeta estas diferencias y las excepciones del catálogo.
- intervalLabel ignora firstKm/firstMonths. TaskDetail mezcla varias etiquetas de frecuencia bajo un único vencimiento. La personalización solo se ofrece en grupos de una operación y está escondida dentro de la fuente del fabricante.
- Ya existen edición/anulación de visitas, operaciones parciales, gastos y copias: reutiliza esas capacidades.
- Revisa Repository.voidExpense, saveExpense y saveVisit: una fila de gasto anulada puede conservar visitId y bloquear otro gasto por el índice único; editar una visita puede sobrescribir fecha, concepto y categoría de un gasto vinculado que se personalizó.

1. PLAN CORRECTO Y PREVISIÓN ENTENDIBLE

Separa explícitamente:
a) Próximos vencimientos reales por operación, usados para estados, avisos y registro.
b) Previsión de revisiones futuras, calculada sin crear trabajos ni fingir que se hicieron mantenimientos.

La previsión debe incluir las operaciones comunes que volverían a tocar si se realizan las revisiones anteriores en los límites previstos, junto con la fase correspondiente y las operaciones adicionales que coincidan. Explica brevemente esa condición. No copies toda la primera revisión en todos los hitos ni añadas todas las tareas antiguas indiscriminadamente.

Con la referencia inicial, la previsión a 215.000 km debe incluir las operaciones comunes de 20.000 km y las de fase C; las de fase B tendrán su siguiente aparición prevista a 235.000 km. Verifica cada operación con el catálogo/fuente aplicable.

Tras registrar trabajo real, recalcula desde sus kilómetros y fecha reales, por operación. Si el aceite se hace a 195.200 km, su próximo límite de 20.000 km es 215.200 km; no lo redondees a 215.000. No reinicies operaciones que no se realizaron. Si una revisión es parcial, sus operaciones pendientes deben seguir pendientes.

Kilómetros y tiempo vencen por lo que ocurra antes. Conserva vencimientos diferentes cuando proceda: agrupar visualmente no puede alterar la regla ni ocultar un plazo individual. La previsión no genera notificaciones de hitos que todavía no son el próximo vencimiento real.

2. INTERFAZ DE MANTENIMIENTO

Presenta tarjetas compactas con nombre claro, próximo límite, tiempo/kilómetros restantes y estado. Titula los grupos por su próximo hito, por ejemplo “Revisión a los 195.000 km”, sin aparentar una única frecuencia cuando hay reglas distintas.

El detalle debe permitir consultar qué incluye, registrar realizado y acceder a cada operación para editarla. Explica las fases con lenguaje normal: cuando corresponda, “Primera en este plan: 20.000 km; después cada 40.000 km”. La fuente del fabricante será información secundaria, sin nombres internos ni párrafos técnicos en el flujo principal.

Separa la consulta de operaciones del formulario para marcar las realizadas. Al registrar una visita nueva, exige una selección consciente: no presentes todas como realizadas por defecto. Ofrece “Seleccionar todas” y confirmación con fecha, kilómetros y selección antes de guardar. Al editar una visita, carga correctamente lo ya registrado.

Cuida textos en español, importes en euros, legibilidad móvil y navegación inferior, evitando etiquetas partidas como “Mantenimient/o”.

3. CREAR Y PERSONALIZAR MANTENIMIENTOS

Permite añadir una operación propia con nombre, notas, recurrencia por kilómetros, tiempo o ambos, o una tarea única con fecha y/o kilometraje concreto. No exijas una URL del fabricante ni inventes una fuente para tareas propias.

Permite editar nombre, notas, intervalos y activar/desactivar recordatorios de cada operación, también si pertenece a una revisión agrupada. Desactivar recordatorios conserva el historial. Diferencia una operación propia de una regla del fabricante personalizada y muestra “Personalizado”. Conserva la regla original del fabricante y permite restaurarla cuando exista.

Editar la frecuencia de una operación existente no debe crear otra copia. Si se sustituye una fase del fabricante por una periodicidad personalizada simple, define esa conversión explícitamente: no conserves firstKm/firstMonths que hagan ignorar el intervalo nuevo. Mantén intacta la referencia original del fabricante.

La creación o edición debe recalcular inmediatamente la operación y sus avisos. Una tarea única completada no se repite. Una cita o recordatorio puntual de taller puede cubrirse con esta misma función, sin crear un módulo aparte.

4. REGISTRAR, EDITAR Y BUSCAR TRABAJOS

Reutiliza las visitas existentes. Permite una revisión con varias operaciones, un cambio de aceite aislado o una reparación libre que no cree automáticamente una regla periódica. Fecha y kilómetros serán editables; taller, notas y gasto, opcionales.

Permite editar y anular registros con confirmación y trazabilidad. Recalcula solo las operaciones afectadas usando el último trabajo real válido. Cambiar únicamente notas no reinicia vencimientos ni duplica avisos. No conviertas la referencia de 175.000 km ni los hitos previstos en trabajos hechos.

Añade búsqueda sencilla en el historial por título, operaciones incluidas, taller y notas; buscar “aceite” debe encontrar una revisión que lo incluya aunque su título sea genérico.

5. GASTOS Y FILTROS

Mantén alta, edición y anulación de gastos manuales: taller, extras, gasolina y otros. No añadas consumo, informes, gráficos ni contabilidad.

Añade filtros combinables por mes, año y tipo, todos con opción “Todos” y acción para limpiar filtros. Usa meses en español y los años de los datos disponibles. Si se elige un mes con “Todos los años”, muestra ese mes en todos los años y deja clara la selección.

Filtra por la fecha del gasto, no por la fecha de creación o edición. Usa fechas de calendario sin desplazamientos por zona horaria. Conserva selección al navegar y al recrear la pantalla.

Muestra la lista filtrada y su subtotal. Distingue claramente “Total de estos gastos” de “Total acumulado” de todos los gastos activos. Lista, subtotal y total deben reaccionar a altas, ediciones y anulaciones. Si no hay resultados, muestra un estado vacío y 0 €. Usa céntimos enteros para cálculos; no Double.

Un gasto de gasolina no modifica kilómetros ni mantenimientos.

Corrige la vinculación visita-gasto:
- Una visita debe tener como máximo un gasto activo vinculado y su importe contarse una sola vez.
- Anular un gasto vinculado y añadir después otro debe funcionar, sin que una relación de una fila inactiva bloquee el índice único. Conserva la auditoría y contempla datos existentes y restaurados.
- Editar kilómetros, notas u operaciones de una visita no debe sobrescribir silenciosamente fecha, concepto o categoría personalizados del gasto vinculado.
- Mantén coherentes los importes de visita y gasto y la política existente de anulación de visitas.

6. AVISOS Y DATOS

Mantén notificaciones locales Android reales. Evalúa umbrales al actualizar manualmente el odómetro y los límites de fecha en segundo plano; recalcula tras editar trabajo o regla. La app solo conoce los kilómetros que yo introduzco: no inventes lectura automática.

Evita duplicados, cancela avisos obsoletos y conserva el recordatorio existente para actualizar kilómetros. Gestiona el permiso de notificaciones y muestra su estado de forma comprensible.

Preserva el historial, gastos, reglas personalizadas, referencias y preferencias en actualizaciones y copias/restauraciones. Amplía el formato o Room solo si hace falta, con migración y compatibilidad explícitas. No uses migraciones destructivas.

Si cambias el catálogo, modifica la fuente canónica data/maintenance-catalogue.json: el build copia esa fuente a assets. No alteres frecuencias del fabricante sin verificar la fuente exacta, modelo, año, motor y mercado.

7. VERIFICACIÓN Y ENTREGA

Añade pruebas útiles para:
- Previsión inicial completa a +40.000 km con operaciones comunes y fase C; fases B/C alternan correctamente.
- Registro parcial sin reiniciar ni ocultar las operaciones pendientes.
- Trabajo a 195.200 km: común a 215.200 km; fase B de 40.000 km a 235.200 km.
- Vencimiento por fecha antes que por kilómetros, y viceversa.
- Personalización de una fase a 10.000 km/6 meses sin quedar sometida al primer desfase de 20.000 km/12 meses; esto es una personalización de prueba, no una frecuencia del fabricante.
- Crear/editar/desactivar una regla propia, completar una tarea única y restaurar una regla del fabricante.
- Editar/anular un trabajo y recuperar correctamente el último registro válido de cada operación.
- Gastos: combinación de filtros, cambio de año, todos los años, vacíos, edición de fecha/tipo y anulaciones; subtotales exactos.
- Anular un gasto vinculado y volver a vincular otro; editar una visita preservando metadatos personalizados del gasto; ausencia de dobles importes.
- Actualización/restauración con datos previos y nuevas reglas; previsiones sin crear trabajos ficticios.
- Avisos tras cruzar un límite y tras modificar una regla, sin duplicados ni avisos obsoletos.

Ejecuta las pruebas y comprobaciones apropiadas, incluyendo testDebugUnitTest, lintDebug y assembleDebug; usa gradlew o gradlew.bat según el entorno. Ejecuta pruebas instrumentadas si hay dispositivo/emulador disponible y comprueba las pantallas principales.

Entrega código implementado, documentación coherente y APK con versión incrementada. Si está disponible la firma original, prepara una APK compatible con actualizar la instalación existente. Si falta, explica la limitación real y entrega el artefacto que puedas generar sin inventar una firma ni pedir borrar datos. No añadas claves al repositorio.

Indica qué cambió, las pruebas realmente ejecutadas, cualquier bloqueo concreto y dónde está la APK. No afirmes haber probado cosas que no ejecutaste.

Fuera de alcance: app multimarca, descarga masiva de manuales, backend, cuentas, OBD, diagnósticos, marketplace, informes de gastos y rediseño completo.
