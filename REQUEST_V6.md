Amplía la app Android existente P38Journal con las funcionalidades aprobadas abajo. La app ya funciona bien: implementa esta ampliación sin rehacerla ni alterar el mantenimiento que ya funciona. Entrega los cambios y una APK nueva; no te quedes en un plan.

Repositorio: https://github.com/Andresvelascofdez/P38Journal

FORMA DE TRABAJO Y LÍMITES

Revisa primero el estado local actual, AGENTS.md y la documentación. Usa agentes conforme a las instrucciones del proyecto, con responsabilidades claras y revisión final. Trabaja sobre los últimos cambios locales, aunque todavía no estén publicados en GitHub.

Conserva Kotlin/Compose/Room/WorkManager, identidad de la aplicación, firma existente y todos los datos. Mantén el plan del fabricante, referencia operativa de 175.000 km, odómetro manual, proyección de revisiones, edición de trabajos, notificaciones y filtros de gastos por mes/año/tipo. La referencia del plan no equivale a un trabajo realizado.

Actualiza los documentos de alcance: el cálculo sencillo de consumo está ahora autorizado, aunque antes estuviera excluido. Implementa exclusivamente lo aprobado aquí. Resuelve decisiones rutinarias de forma autónoma; yo instalaré y probaré.

1. TRABAJOS Y MEJORAS MANUALES: PREVISTOS Y REALIZADOS

Quiero añadir cualquier cosa que haya hecho al coche o quiera hacer: ruedas, piezas, reparaciones, accesorios o mejoras. No lo plantees como una lista genérica de pendientes. Usa un nombre claro como “Trabajos y mejoras”, con vistas “Previstos” y “Realizados”.

Permite:
- Crear directamente una entrada prevista o realizada.
- Editar, anular/eliminar con confirmación y pasar una prevista a realizada.
- Nombre libre y notas. Para realizados, fecha y kilómetros cuando se conozcan; no inventes datos históricos. Para previstos, fecha objetivo y coste previsto opcionales.
- Vincular opcionalmente un gasto real y consultar la relación desde ambas pantallas.

Reutiliza los trabajos/visitas y gastos existentes. Un trabajo realizado debe tener una sola representación coherente en el historial, aunque pueda consultarse desde varios lugares. Convertir uno previsto en realizado no crea duplicados ni registra automáticamente un gasto por el coste previsto.

Una entrada libre no crea una periodicidad ni modifica una regla del fabricante. Solo actualiza un mantenimiento cuando el usuario lo vincula expresamente y proporciona los datos necesarios. Mantén la selección parcial y la edición/anulación ya existentes.

2. DOCUMENTACIÓN Y VENCIMIENTOS MANUALES

Permite crear, editar y desactivar entradas de documentación de cualquier nombre. Seguro, Road tax y MOT serán sugerencias, no categorías cerradas ni obligaciones precargadas.

Cada entrada tendrá nombre, fecha de vencimiento, notas opcionales y configuración del aviso: activado/desactivado y días de antelación. Permite vencimiento puntual o repetición configurable, por ejemplo anual.

Muestra los documentos próximos a vencer y vencidos. Al renovar, permite introducir la nueva fecha real y conservar la anterior como historial de renovación. No supongas que se renovó por llegar a la fecha; la repetición puede sugerir una nueva fecha, pero debe confirmarla el usuario. No generes un gasto ni declares un documento vigente automáticamente.

Usa notificaciones locales Android de fecha, abre la entrada correcta al tocarlas y cancela/reprograma avisos al editar, renovar o desactivar. Mantén estas fechas separadas del mantenimiento del fabricante.

3. ACCIONES RÁPIDAS EN INICIO

Añade accesos claros y compactos para:
- Actualizar kilómetros.
- Registrar mantenimiento realizado.
- Añadir gasto.
- Registrar repostaje.
- Añadir trabajo o mejora manual, permitiendo elegir previsto/realizado.

Reutiliza formularios existentes siempre que sea posible. Añade acceso sencillo a documentación y ruedas sin saturar la navegación inferior ni rehacer la interfaz.

4. REPOSTAJES Y CONSUMO MEDIO SENCILLO

Permite registrar fecha, kilómetros del odómetro, litros, importe total y si el depósito quedó lleno. Permite editar y anular registros.

Integra el importe con Gastos, categoría gasolina: debe existir un único gasto por repostaje y contarse una sola vez. Conserva los gastos de gasolina antiguos y permite registrar un gasto sin datos de consumo; no inventes litros ni kilómetros a partir del importe.

Calcula consumo en L/100 km mediante intervalos entre llenados completos:
- El primer llenado completo sirve de referencia; sus litros no se incluyen en el siguiente intervalo.
- Suma los litros de los repostajes posteriores, incluidos los parciales y el llenado completo final.
- Divide por los kilómetros entre ambos llenados completos y multiplica por 100.
- El consumo medio acumulado es litros totales de intervalos válidos / kilómetros totales de esos intervalos × 100. No hagas una media simple de sus consumos.
- Los parciales posteriores al último llenado completo quedan pendientes del siguiente cierre.
- No calcules con distancia nula/negativa ni intervalos con datos insuficientes. Muestra “Datos insuficientes”, no un consumo ficticio.
- Recalcula al editar/anular un repostaje.

En Inicio muestra únicamente consumo medio y acceso a repostajes. En el detalle indica el período y kilómetros utilizados. Sin gráficos, predicciones de autonomía ni informes.

Admite decimales con coma y punto. Guarda dinero en céntimos y litros con precisión decimal adecuada.

El kilometraje de un repostaje no debe sobrescribir silenciosamente el odómetro actual, especialmente al registrar algo antiguo. Puede ofrecerse actualizarlo de manera explícita cuando proceda.

5. RUEDAS: SEGUIMIENTO SENCILLO POR EJE

Quiero dos registros: “Ruedas delanteras” y “Ruedas traseras”. Sin ruedas individuales, posiciones izquierda/derecha, profundidad de dibujo, rotaciones ni gestión avanzada de neumáticos.

Cada eje tendrá:
- Kilómetros de montaje introducidos por mí.
- Intervalo de aviso tras X kilómetros, configurable por separado.
- Nota opcional.
- Kilómetros recorridos desde el montaje y kilómetros restantes para el próximo aviso.

Las actuales están nuevas. Permite registrarlas como tales con los kilómetros de montaje que yo introduzca. No uses automáticamente 175.000 km ni crees una sustitución histórica ficticia. No exijas marca, modelo, medidas o datos que no hacen falta.

Acciones:
- “Revisadas”: registra los kilómetros de revisión y programa el siguiente aviso tras el intervalo que elija. Conserva los kilómetros de montaje y los kilómetros de uso.
- “Cambiadas”: registra el cambio, conserva el historial del juego anterior y empieza el seguimiento nuevo desde los kilómetros reales del cambio.
- “Cambiar ambas”: actualiza ambos ejes en una sola operación. Si añado un gasto, contabilízalo una sola vez.

Permite corregir los registros de montaje, revisión o cambio y recalcular. Conserva un historial sencillo por eje, reutilizando los trabajos cuando corresponda, sin duplicarlos.

Al actualizar manualmente el odómetro y alcanzar el límite, envía una notificación indicando el eje: “Toca revisar las ruedas delanteras y valorar el cambio”. No declares automáticamente que están gastadas ni que hay que sustituirlas solo por haber recorrido X kilómetros.

El próximo aviso se calcula desde el montaje o desde la última revisión válida; el uso total siempre se calcula desde el montaje del juego actual. Ejemplo: montaje a 175.000 km y aviso cada 10.000; revisar a 185.200 programa el siguiente a 195.200, pero las ruedas siguen llevando 10.200 km.

6. DATOS, AVISOS Y VERIFICACIÓN

Incluye todos los nuevos datos y relaciones en las copias/restauraciones existentes. Usa migraciones compatibles y no destructivas. Conserva los gastos y trabajos antiguos sin exigir completar nuevos campos.

Mantén permisos y avisos Android existentes. Evita notificaciones duplicadas y cancela las obsoletas al editar/anular. La app solo conoce los kilómetros que introduzco manualmente.

Comprueba con pruebas útiles:
- Trabajo previsto convertido a realizado sin duplicar historial ni gasto, y trabajo libre sin modificar reglas del fabricante.
- Documentación de nombre libre, renovación manual y reprogramación/cancelación de avisos.
- Consumo: lleno a 175.000 km; parcial de 20 L a 175.200; lleno de 50 L a 175.500 → 14 L/100 km. Los litros del primer registro no cuentan.
- Media de consumo ponderada por distancia; registros incompletos, parciales abiertos y edición/anulación.
- Repostaje reflejado una sola vez en gastos y conservando los filtros.
- Revisar ruedas no reinicia uso; cambiar delanteras no reinicia traseras; cambiar ambas no duplica el gasto.
- Ejemplo de aviso de ruedas a 195.200 km, notificación al cruzarlo y cancelación tras registrar revisión/cambio.
- Migración y copia/restauración con datos anteriores y nuevos.
- Regresión del plan, fases, revisiones futuras y notificaciones de mantenimiento existentes.
- Accesos rápidos y formularios de las pantallas nuevas.

Ejecuta las pruebas apropiadas, lint y compilación; comprueba la interfaz en emulador/dispositivo si está disponible. Indica exactamente qué pruebas se ejecutaron y qué quedó bloqueado.

Entrega una APK nueva con versión incrementada e identificación visible del build. Conserva la firma original si está disponible; si falta, explica la limitación de actualización sin pedir borrar datos ni inventar una firma. Indica el archivo exacto generado.

Fuera de alcance de esta ampliación: ficha de consumibles, preparación de visitas al taller, garantías, proveedores, copias automáticas nuevas, OCR, OBD, backend, cuentas, app multimarca, informes y seguimiento avanzado de ruedas. Mantén cualquier capacidad existente, pero no añadas funcionalidades de la lista de ideas que no he aprobado.