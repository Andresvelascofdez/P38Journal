# Producto: calendario sencillo del P38

## Ampliación vigente v4

[REQUEST_V4.md](REQUEST_V4.md) prevalece sobre los criterios anteriores. La consulta ofrece vencimientos reales y una previsión hipotética de los primeros cuatro hitos; solo los reales generan avisos y permiten registrar. La previsión supone realizar los trabajos previos en sus límites, sin crear historial. La referencia y su fecha siguen separadas del odómetro.

Cada operación se consulta y personaliza desde el detalle del grupo. Una visita nueva empieza sin operaciones marcadas y requiere confirmar fecha, km y selección. Se pueden crear operaciones propias recurrentes o únicas sin URL; editar una recurrente no la convierte en una tarea única: para otra cita se crea una operación nueva. Restaurar el fabricante conserva identidad e historial.

Gastos permite filtros combinables de mes/año/tipo, Todos y limpiar; conserva selección al navegar y recrear. Lista, subtotal filtrado y total acumulado usan gastos activos y céntimos enteros. Son filtros de consulta; informes, gráficos y consumo quedan fuera del alcance. Historial busca también operaciones, taller y notas.

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

Total acumulado y lista cronológica. Filtros combinables de consulta por mes, año y tipo, cada uno con Todos y acción para limpiar. El mes con Todos los años abarca ese mes en cualquier año; los años disponibles proceden de gastos activos. Se filtra por fecha de calendario del gasto. Lista y «Total de estos gastos» reaccionan a altas/ediciones/anulaciones, junto con «Total acumulado» de todos los activos, calculados en céntimos. Selección conservada al navegar/recrear pantalla. Añadir gasto: fecha, euros, concepto, categoría Taller/Extras/Gasolina/Otros; nota/foto opcionales. Sin exigencia de km, factura, avería o tarea. Pago de taller contado una vez. Los filtros de consulta están autorizados; informes, gráficos y consumo/litros permanecen excluidos. Presupuestos/importes desconocidos no suman.

## Ajustes, documentos y avisos

Preferencias de preaviso, permiso/canales y prueba de aviso, fecha del plan y km de referencia, idioma/zona horaria. Defaults de producto separados de intervalos oficiales. Acceso secundario a bandeja, manuales con enlaces/visor/búsqueda textual disponible y exportación/restauración completa. Android Compose responsive 320–430 dp, tema oscuro, fuentes grandes, accesibilidad, teclado/insets/Back. Cálculo/datos/avisos locales sin servidor. No añadir nuevas funciones antes de corregir este recorrido.
