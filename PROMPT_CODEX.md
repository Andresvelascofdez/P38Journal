# Encargo completo — P38 Journal para Android

Construye una aplicación **Android nativa e instalable mediante APK** para el mantenimiento de mi Range Rover P38 4.6 V8 Autobiography del año 2000, gasolina y automático, comprado con 175.000 km. He detenido el desarrollo anterior y empiezo con un workspace nuevo. Esta documentación sustituye íntegramente la anterior. Quiero que Codex y sus agentes preparen el entorno, diseñen, programen, revisen los manuales, prueben, corrijan e integren todo. Mi trabajo será instalar/probar el APK y después registrar el uso y mantenimiento del coche.

Lee todos los archivos de este paquete. No necesitas recuperar la conversación para ejecutarlo. No entregues solo un plan, una maqueta o código sin compilar. Las decisiones aquí especificadas no requieren volver a preguntarlas.

## Plataforma fijada

Kotlin + Jetpack Compose/Material 3, Room/SQLite, DataStore y WorkManager, con Coroutines/Flow. Aplicación local de un propietario, sin cuenta, backend ni alojamiento obligatorio. El catálogo viene incluido y las funciones principales trabajan sin Internet. No convertir el encargo en una web, PWA o WebView. No incluir una API de IA, Firebase ni servicios externos salvo que una necesidad real y autorizada lo justifique; el alcance actual se resuelve localmente.

Prepara el proyecto completo con Gradle Wrapper, versiones estables compatibles verificadas, scripts reproducibles y dependencias fijadas. Sigue ANDROID.md. Compila y entrega APK debug y APK release firmado, con identidad y clave de firma persistentes para que las actualizaciones conserven datos. No publicar en Google Play ni contratar nada.

## Mantenimiento precargado

La app debe saber qué comprobar, ajustar, lubricar, sustituir o revisar profesionalmente y avisar por fecha, kilómetros o condición documentada. Yo no tengo que crear las tareas ni introducir intervalos. Cada regla tendrá fuente, edición, páginas, aplicabilidad y revisión. No usar intervalos genéricos como si fueran del P38.

Completa el catálogo desde SOURCES.md. Lee visualmente matriz, glosario y notas de la hoja de servicio; extraer texto no conserva todas las columnas. Filtra gasolina/caja automática/año y condiciones por VIN/equipamiento, sin inventar VIN. Documenta filas incluidas, excluidas y condicionales, con revisión independiente. `maintenance-starter.json` es solo un inicio. Una limitación documental debe quedar identificada dentro de la operación y en la cobertura, sin inventar reglas ni abandonar las demás funciones.

Inicio muestra «Hacer ahora», «Próximamente» y «Programado». No incluir «Por aclarar», «Punto de partida», tarjetas de incertidumbre ni un asistente para investigar el pasado. Crear mantenimiento inicial accionable para establecer referencias desde ahora. Respetar lo conocido: no cambiar de nuevo neumáticos o balonas solo por tener historial parcial. Conservar evidencia y precisión en el detalle/historial. Comprar el coche o abrir la app no equivale a realizar un servicio.

## Funciones completas

- Español, km, EUR, zona horaria Asia/Nicosia editable. Diseño sencillo verde oscuro, tonos cálidos y modo oscuro. Nombre provisional P38 Journal.
- Navegación inferior Inicio/Plan/Historial/Más. Más: averías, manuales/documentos, gastos, piezas, bandeja de avisos y ajustes.
- Kilometraje con fecha y acción rápida. 175.000 km son los de compra; no son lectura actual. Sin lectura actual siguen funcionando los avisos por tiempo.
- Plan por sistema/estado, detalle con acción, motivo de vencimiento, próximos límites, última operación válida y fuente. Programar fecha de taller no equivale a completar trabajo.
- Visitas con múltiples trabajos, fecha/km, taller opcional, operaciones satisfechas, piezas, costes, notas y fotos/PDF. Guardado atómico, edición/anulación auditadas, doble toque sin duplicados. Una factura cuenta una vez.
- Recalcular solo operaciones realmente satisfechas. Comprobar un nivel no equivale a cambiar el líquido. Trabajos históricos no desplazan otros posteriores.
- Averías: síntomas, diagnóstico separado, prioridad, seguimiento y relación con reparación. Piezas: referencias, proveedor/enlace, coste, estado y compatibilidad con evidencia. Sin compras automáticas.
- Gastos manuales Taller/Extras/Gasolina/Otros. Fecha, importe y concepto; categoría simple, notas/recibo opcionales. Sin exigir factura, km o mantenimiento asociado. Lista cronológica, edición/eliminación y total «Lo que llevo gastado». Evitar duplicar gastos de taller. Sin informes, gráficos, comparativas, litros ni consumo. Añadir gasto no cambia odómetro ni completa mantenimiento.
- Biblioteca privada con PDF/fotos, visor, búsqueda cuando el PDF contenga texto, índice/favoritos y referencias de página. No fingir OCR ni resultados. Enlaces de manuales e importación legítima, sin redistribución pública de PDFs.
- Copias completas con registros, gastos y adjuntos, exportación/restauración validada, migraciones sin borrado de datos. Uso del selector de archivos Android y permisos mínimos.

## Avisos esenciales

Implementa notificaciones locales de Android y bandeja persistente. WorkManager evalúa tareas aunque la interfaz esté cerrada; no depender de un temporizador en pantalla ni de que yo abra la app. No exige Internet para evaluar/enviar un aviso local. Permiso de notificaciones cuando corresponda tras «Activar avisos», estado de canales, acceso a Ajustes y «Probar aviso». Tocar la notificación abre la tarea correcta.

Aviso previo, vencimiento y recordatorio moderado de pendientes. Defaults de producto: 30 días/1.000 km cuando sea proporcionado; adaptar controles frecuentes y agruparlos en resumen. Hora preferida inicial 09:00 local como ventana, sin prometer precisión que Android no garantice. Evitar duplicados, recomputar al registrar/corregir un trabajo, invalidar avisos antiguos y recuperarse de reinicios. No solicitar alarmas exactas ni usar un servicio permanente.

No inventar kilómetros conducidos. Los avisos por km usan lecturas reales introducidas; no hay OBD/GPS en esta versión. Documentar límites de cierre forzado/ahorro de batería y pruebas reales, sin afirmar que una prueba simulada demuestra recepción visible.

## Datos y agentes

Usa data/vehicle.json sin inventar fechas, matrícula, VIN, importes ni kilometraje actual. Las intervenciones previas proceden de resúmenes de conversaciones, no de facturas vistas en este encargo. No resetear intervalos a los kilómetros de compra. Incidencias históricas no prueban una avería activa hoy. Datos de prueba aislados de mi app real.

Usa agentes explícitamente: coordinador, documentación/catálogo, dominio/persistencia, interfaz Android, avisos/compilación y revisión/pruebas independiente. Define contratos y propiedad de archivos antes de repartir trabajo. Integra y resuelve los hallazgos. Ajusta concurrencia a disponibilidad; si este entorno no permite subagentes, indícalo y ejecuta secuencialmente sin simular delegación.

## Autonomía y entrega

Haz las decisiones técnicas reversibles y prepara SDK/JDK/dependencias cuando el entorno lo permita. No me pidas escribir código, depurar, preparar datos ni compilar. Si hay limitaciones de red, permisos, emulador o hardware, identifica el bloqueo exacto, completa lo independiente y deja una continuación reproducible. No declarar terminado algo sin verificarlo.

Sigue QA.md y HANDOFF.md. Entrega los APK reales, informe de pruebas, catálogo revisado, instrucciones breves de instalación/actualización/copias y 5–8 pasos para probar. Conserva identidad y material de firma privado; no subir claves al repositorio ni incluir secretos en APK/logs. Mantén progreso y decisiones en docs/PROGRESS.md para reanudar sin repetir el trabajo. No acceder al coche, publicar datos, enviar mensajes ni contratar servicios.
