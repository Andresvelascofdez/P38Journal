# Producto: calendario sencillo del P38

## Alcance

Android nativo, español, km/EUR, vehículo único P38 4.6 gasolina automático año 2000. Funciones principales: calendario del fabricante, kilometraje manual, registro/historial, notificaciones y gastos. Manuales y copias como accesos secundarios. No ampliar con gestores de piezas, averías, diagnósticos o paneles de incertidumbre. Si ya existen datos de esos módulos, conservarlos y su exportación sin hacerlos centro de la navegación.

## Referencias

Base operativa de km = 175.000, elegida expresamente por el propietario. En instalación nueva puede mostrarse «175.000 km · valor inicial» hasta la primera lectura manual, sin adjudicarle una fecha de lectura real. En la app existente conservar última lectura real, aunque sea superior. Base y odómetro actual son campos distintos: actualizar km no mueve la base. Ajustes muestra «Referencia del plan: 175.000 km» sin exigir una confirmación de servicios previos.

Fecha del plan editable, por defecto creación recuperable del plan o fecha de migración; identificada como referencia del calendario, sin fingir fecha de compra/intervención. Intervalos oficiales conservados, incluido tiempo si procede. Históricos recuperados permanecen en Historial; no generan pendientes iniciales ni modifican silenciosamente la base elegida.

## Navegación e Inicio

Barra inferior: Inicio, Mantenimiento, Historial y Gastos. Icono secundario para Ajustes/Manuales/Copias. Inicio contiene:

- Lectura actual visible y «Actualizar kilómetros».
- Próxima revisión o mantenimiento: título, próximo km, plazo si existe y «Te faltan X km».
- «Pendiente» solo cuando un límite real del calendario operativo se alcanza; «Próximamente» para preavisos.
- «Registrar trabajo» accesible; aviso de permisos solo si procede.

Tarjetas compactas y listas legibles, sin repetir párrafos. Nada de «Mantenimiento inicial», «no consta una referencia precisa», «Por aclarar» o «Punto de partida» en flujos ordinarios. Si no hay pendientes, mostrar «Todo al día» con próximos mantenimientos; no generar tareas para rellenar la pantalla.

## Mantenimiento y detalle

Lista ordenada por vencimiento con filtro sencillo Todos/Próximos/Pendientes. Cada fila: nombre natural, km/plazo próximo y estado. Revisiones con operaciones coincidentes agrupadas; expandir para ver «Qué incluye». No crear alertas separadas para todos los puntos de una revisión.

Detalle prioritario:

1. Título: «Cambio de aceite y filtro», «Cambio de líquido de frenos», etc.
2. «Cada X km o Y meses» cuando ambas unidades estén documentadas.
3. «Próximo: Z km» y fecha si aplica, más «Faltan N km» o motivo «Pendiente por kilometraje»/«Pendiente por fecha».
4. «Último registrado: fecha · km» o «Plan calculado desde 175.000 km» cuando todavía no hay ejecución; esta última frase solo secundaria.
5. Botón claro «Registrar realizado».
6. «Qué incluye», notas útiles y «Fuente del fabricante» desplegables.

No volcar JSON, códigos ingleses ni párrafos de razonamiento técnico. Traducir y resumir contenido fuente con etiquetas naturales. La información documental sigue disponible, pero fuera del bloque principal. Fuentes distinguen páginas impresas/PDF. Condiciones no resueltas no crean una tarea inicial urgente; solo reglas aplicables producen alertas. La cobertura técnica explica la limitación.

## Registro

Desde la tarea: operación ya seleccionada, fecha hoy y última lectura precargadas/editables. Guardar únicamente lo realmente realizado. Campos secundarios taller, notas, coste/recibo opcionales. Una visita puede satisfacer varias operaciones explícitas. Una revisión parcial no cierra todas. Un gasto aislado no satisface reglas. Actualizar kilometraje desde un trabajo actual es posible con confirmación simple; un trabajo retroactivo no reduce la lectura actual.

Historial cronológico con trabajo, fecha/km, notas/gasto/documento si existen; editar/anular recalcula. Datos de pruebas en debug separado. No servicios inventados a 175.000 km.

## Gastos

Total «Lo que llevo gastado» y lista cronológica. Añadir gasto: fecha, euros, concepto, categoría Taller/Extras/Gasolina/Otros; nota/foto opcionales. Editar/eliminar actualiza total. Sin exigencia de km, factura, avería o tarea. Importes exactos y pago de taller contado una vez. Sin informes, gráficos, consumo/litros ni análisis por meses. Presupuestos/importes desconocidos no suman.

## Ajustes, documentos y avisos

Preferencias de preaviso, permiso/canales y prueba de aviso, fecha del plan y km de referencia, idioma/zona horaria. Defaults de producto separados de intervalos oficiales. Acceso secundario a bandeja, manuales con enlaces/visor/búsqueda textual disponible y exportación/restauración completa. Android Compose responsive 320–430 dp, tema oscuro, fuentes grandes, accesibilidad, teclado/insets/Back. Cálculo/datos/avisos locales sin servidor. No añadir nuevas funciones antes de corregir este recorrido.
