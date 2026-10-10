# Especificación funcional

## Propósito y alcance

Una aplicación personal y privada para un coche, ampliable internamente sin interfaz de flota. La necesidad central es recordar el mantenimiento aplicable al P38 y conservar los trabajos realmente hechos. El propietario registra kilómetros y servicios; el software determina los avisos. No hay telemetría automática, OBD, compras, seguimiento GPS, comunidad, marketplace, chatbot ni OCR obligatorio en esta versión.

## Experiencia Android

Aplicación Android nativa de teléfono. Diseño responsive con Jetpack Compose para anchos de 360–430 dp, usable desde 320 dp y con fuentes ampliadas. Respetar barras del sistema, safe insets, teclado y navegación atrás de Android. P38 Journal es nombre provisional. Verde oscuro, superficies cálidas, iconos claros y modo oscuro. Inicio con una ficha discreta del coche y kilometraje actual/fecha, seguida de «Hacer ahora», «Próximamente» y «Programado». Botón principal «Registrar trabajo»; actualizar kilómetros debe costar pocos toques. Sin tarjeta de análisis del pasado, porcentajes de salud inventados ni puntuaciones de fiabilidad.

Navegación inferior: Inicio, Plan, Historial, Más. Más ofrece Averías, Manuales/documentos, Gastos, Piezas, Bandeja de avisos y Ajustes. Badges de estado acompañados de texto. Acciones táctiles accesibles, formularios legibles, estados vacíos útiles y mensajes claros de error. No copiar el contenido de la maqueta previa: solo su dirección visual.

## Inicio y plan

Cada tarea posee sistema (motor, refrigeración, transmisión, frenos, suspensión, electricidad, carrocería u otro), acción (comprobar, ajustar, lubricar, sustituir, revisión profesional), prioridad, regla, fuente y próximos límites. Una tarea puede vencer por más de un motivo y mostrarse una sola vez. Ordenar por prioridad y vencimiento, sin ocultar tareas por límite de cantidad. Grupos y filtros por sistema, estado y horizonte.

Al empezar, generar «Mantenimiento inicial» con acciones aplicables para crear una referencia fiable. Es una decisión de producto derivada del historial parcial, no una afirmación de que el fabricante prescriba una sustitución a los 175.000 km. Registrar hechos previos en Historial y tenerlos en cuenta para evitar cambios indiscriminados. Elementos conocidos renovados se inspeccionan y mantienen según corresponda; no se sustituyen de nuevo automáticamente.

Los datos desconocidos se describen, si hace falta, en el detalle: «Incluido en la revisión inicial porque no consta una intervención posterior». No usar «Por aclarar» como trabajo principal ni exigir al usuario investigar para operar. Una condición técnica no resuelta debe convertirse en una actuación concreta y fundada, como revisar con el taller la aplicabilidad, conservando el dato incierto en el detalle. No asumir compatibilidad por faltar VIN.

Registrar mantenimiento inicial por etapas. Al completar una etapa, solo se establecen referencias para sus operaciones. La revisión inicial completa no queda cerrada por registrar una factura que solo contiene aceite.

## Registro de trabajos

Una visita agrupa varias líneas de trabajo. Campos: fecha, kilómetros reales/precisión, taller/contacto opcional, descripción, categoría, operaciones satisfechas, piezas/cantidades, costes y adjuntos. Diferenciar presupuesto de importe pagado. Factura total computada una vez; reparto opcional entre líneas debe sumar el total. Desconocido no equivale a cero. Admitir trabajo gratuito con importe cero explícito. Dinero en céntimos enteros o decimal exacto.

Confirmar que realmente se ha realizado antes de cerrar. Guardado atómico de visita, líneas y eventos de mantenimiento. Evitar duplicados por doble toque, reintento de guardado o importación repetida. Editar y anular con registro de auditoría y recomputar fechas/kilómetros/avisos. Un servicio histórico introducido más tarde no desplaza la referencia de un servicio posterior válido. Adjuntos compartidos entre líneas sin duplicar su contenido.

## Kilometraje

175.000 km es lectura de compra, sin fecha exacta de compra. Nunca mostrarla como lectura actual tomada hoy. Primer uso permite introducir la lectura actual con fecha, sin bloquear el resto. Lecturas posteriores con validación; retroceso implica corrección explícita, no pérdida silenciosa del historial. Si falta lectura actual, los avisos temporales siguen funcionando y los kilométricos muestran su base real. Puede recordarse actualizar kilómetros semanalmente, desactivable. No estimar uso como si se hubiera leído el coche.

## Averías y piezas

Avería: síntoma observado, fecha/km si se conocen, sistema, fotos, hipótesis separadas de diagnóstico confirmado, prioridad, taller, presupuesto, estado y relación con reparaciones. Estados abierta, programada, en taller y resuelta. Incidencias antiguas del seed son notas históricas para revisión; no probaron que el coche tenga hoy el mismo fallo.

Pieza: referencia, fabricante, proveedor, enlace, unidades, coste, estado (considerada, pedida, recibida, montada), relación con visita/avería. Compatibilidad conocida o pendiente con evidencia. No existe compra automática ni recomendación de piezas sin comprobar versión.

## Documentación

Biblioteca privada de facturas, presupuestos, fotos y manuales. Etiquetas y relaciones con vehículo, avería o trabajo. Visor PDF con navegación de página y búsqueda textual cuando exista texto. Numeración impresa y número de página PDF diferenciados. Para documentos escaneados sin OCR, indicar búsqueda no disponible y permitir índice/favoritos; no fingir resultados.

Fuentes del fabricante accesibles desde cada tarea. Conservar enlaces aunque no se permita hospedar el PDF. Si una URL externa falla, no eliminar la regla ni inventar contenido. Procesar los documentos de forma que la app no dependa de descargar el manual en cada cálculo.

## Gastos manuales — trazabilidad sencilla

El propietario quiere guardar lo que va pagando por el coche y consultar cuánto dinero le lleva, sin informes ni análisis. Pantalla «Gastos» con un total «Lo que llevo gastado», lista cronológica y botón «Añadir gasto». Categorías: Taller, Extras, Gasolina y Otros. Campos obligatorios: fecha (hoy por defecto), importe EUR y concepto; categoría con selección simple. Notas y recibo/foto opcionales. No exigir factura, kilometraje, taller identificado ni vínculo a una avería o mantenimiento. Registro en pocos toques, también para gasolina y compras pequeñas.

Editar o eliminar registros con auditoría y actualizar el total inmediatamente. Total acumulado = suma de importes reales registrados no anulados, incluyendo una sola vez los gastos de visitas de taller. Cada gasto puede vincularse a una visita mediante identificador único; el total de la factura y sus líneas no se suman por separado. Al añadir a mano un gasto que ya corresponde a una visita, permitir vincularlo al existente; no fusionar pagos distintos solo por coincidir fecha o importe. Presupuestos y costes desconocidos no se suman como pagos reales. Un gasto manual tiene importe informado; los históricos sin importe siguen siendo desconocidos.

Añadir o modificar un gasto NO marca tareas como realizadas, NO cambia el kilometraje actual y NO reinicia los avisos. Gasolina es únicamente un gasto: no añadir cálculos de consumo, litros, €/100 km ni autonomía. Extras no necesita crear una pieza ni un trabajo. No desarrollar gráficos de gastos, informes, comparaciones mensuales/anuales, desglose analítico por talleres ni una contabilidad. El precio de compra del coche no se precarga ni se suma; el propietario puede registrar una compra como gasto solo si lo decide expresamente. No rellenar importes anteriores por estimación.

## Copias y exportación

Mantener exportación/restauración completa con esquema/versionado, registros (incluidos gastos manuales) y adjuntos. CSV de trabajos si procede, sin exigir informes de gastos. Restauración validada y transaccional, con vista previa y sin sobrescritura irreversible inadvertida. Copias automáticas internas con rotación y comprobación de restauración. Respaldo externo mediante el selector de Android; una copia en el teléfono no protege frente a perder ese teléfono.

## Notificaciones y ajustes de Android

Notificaciones locales del sistema y bandeja interna. El cálculo y los avisos trabajan con datos del teléfono, sin conexión obligatoria ni servidor. WorkManager realiza comprobaciones persistentes en segundo plano. Ajustes: español, zona horaria Asia/Nicosia editable, ventana preferida de envío, avisos previos, recordatorio de pendientes, resumen de comprobaciones y recordatorio de kilómetros. La hora de preferencia no es una alarma de precisión garantizada. Cada aviso lleva tarea y motivo; tocarlo abre su detalle con la navegación atrás correcta. Completar un trabajo invalida avisos de la generación anterior.

Permiso de notificaciones, cuando Android lo requiera, solicitado tras pulsar «Activar avisos». Mostrar estado de permiso y canales; ofrecer acceso a ajustes del sistema y botón «Probar aviso». La bandeja sigue funcionando aunque se deniegue permiso. Android decide cuándo ejecuta el trabajo diferible; documentar pruebas y no prometer avisos durante un cierre forzado desde Ajustes. No pedir permisos de alarmas exactas ni mantener un servicio permanente para estas tareas de mantenimiento.

## Privacidad, archivos y persistencia local

Datos del propietario almacenados en Room/SQLite y archivos privados de la app. No crear registro de cuenta ni login de servidor para una app de un solo propietario. El catálogo verificado viene dentro del APK; el motor no depende de descargar manuales. Adjuntos seleccionados con herramientas del sistema y copiados de forma controlada al almacenamiento privado; no pedir acceso completo al almacenamiento ni contactos/localización.

Fotos y PDFs disponibles sin conexión después de importar. Respaldos completos elegidos por el usuario mediante selector de archivos/carpeta Android. Copias internas con rotación como recuperación rápida, claramente diferenciadas del respaldo externo que sobrevivirá a desinstalar o perder el móvil. Copias programadas a una carpeta externa solo tras elegirla y otorgar acceso persistente; no prometer acceso a una carpeta que no existe o cuyo permiso se revocó. Restaurar en una transacción, con vista previa y validación; no borrar historial por reinstalar un seed o actualizar el APK.

No agregar sincronización en nube, telemetría ni envío de gastos/documentos. Abrir enlace de proveedor o manual solo por acción del usuario. Los componentes exportados de Android deben limitarse a lo necesario y los adjuntos se comparten por un proveedor seguro con permisos temporales. No guardar claves de firma ni contraseñas dentro del APK.

## Prioridades

Primero: catálogo verificado, motor, persistencia local, Inicio/Plan, registro, kilometraje y notificaciones Android reales. Después: resto de funciones de este alcance. Todas forman parte de la entrega; la priorización no autoriza a abandonarlas ni presentarlas como terminadas con botones vacíos.
