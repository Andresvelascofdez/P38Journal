# Ampliación vigente5.0 / build6

REQUEST_V6.md y docs/CONTRACT_V6.md prevalecen sobre las exclusiones de consumo y esquema anteriores. El consumo sencillo está autorizado: llenos completos, litros posteriores y media ponderada, sin gráficos/informes. Trabajos previstos/realizados reutilizan Visit; documentación libre, renovación manual y ruedas2ejes separados del fabricante. Room4/Snapshot4 compatibles con2/3 y todos los nuevos datos/relaciones en copias. No inventar montajes a175.000, lecturas, trabajos ni datos históricos. Mantener intactas las funciones de mantenimiento de4.1 y filtros de gastos. Fuente base local6957c1a, respaldo previo en .tools/pre-v6. La evidencia final de esta entrega estará en docs/TEST_REPORT.md; las verificaciones4.1 archivadas son antecedentes.

## Antecedentes históricos, aplicables solo donde no contradigan REQUEST_V6

# Corrección vigente4.1 / build5

REQUEST_V5.md sustituye el selector4.0: Mantenimiento muestra directamente las revisiones proyectadas completas. Recorrido: Repository.tasks → Engine.evaluate (vencimientos reales) → Forecast.project (apariciones independientes) → projectedMaintenanceGroups (ID por grupo/fecha/km) → ProjectedPlanPage → ProjectedRevisionDetail. Contador y «Qué incluye» consumen occurrences del mismo grupo, sin resolver el detalle desde una Task representativa.

Desde175.000 sin historial:195.00029,215.00033,235.00031; cifras de pruebas derivadas del catálogo, no constantes productivas. firstKm/firstMonths solo fijan la primera aparición; siguientes usan km/months recurrentes. Proyección condicionada a cumplir revisiones anteriores; no genera trabajos ni avisos. Home, Vencimientos reales, TaskDetail, NotificationEvaluator y WorkManager siguen utilizando únicamente Task reales. Registrar desde una revisión ofrece solo sus apariciones sequence0 que coinciden con el próximo vencimiento real; las siguientes son consulta.

La lista muestra los primeros cuatro grupos globales completos; operaciones propias aisladas con próximo vencimiento real permanecen visibles también fuera de ese horizonte. No se añaden grupos colectivos lejanos con una proyección truncada. Las operaciones reales completas siguen accesibles en Vencimientos reales. Trabajo parcial195.200 produce215.200 exacto para aceite; las restantes permanecen195.000. Fechas se recalculan por operación desde el trabajo real. Abrir una previsión no escribe Visit/Event/Reading.

Sin cambio de Room3, catálogo, applicationId ni firma. Conservar edición, filtros de gastos y copias. Pruebas de pantalla nativa y detalle completo, parcial, actualizaciones reales de APK y evidencias nuevas en docs/TEST_REPORT.md; resultados4.0 archivados como antecedentes.

## Antecedentes conservados donde sean compatibles

# Pruebas de aceptación v4

`REQUEST_V4.md` prevalece sobre los requisitos anteriores. Esta lista define comprobaciones de aceptación; los resultados ejecutados, comandos, entorno y bloqueos se documentan en el informe de verificación. No interpretar un caso listado aquí como una prueba ya pasada.

## Calendario real y previsión

- Con catálogo y base 175.000 km, distinguir los próximos vencimientos reales de la previsión condicionada a realizar las revisiones previas en sus límites. A 215.000 km la previsión incluye operaciones comunes de 20.000 km y fase C; fase B vuelve a 235.000 km. Contrastar las operaciones con la matriz del catálogo, sin copiar indiscriminadamente la primera revisión.
- B empieza a +20.000 km/12 meses y repite +40.000 km/24 meses; C empieza a +40.000 km/24 meses y repite +40.000 km/24 meses. Mantener las excepciones y plazos individuales. Proyectar no crea visitas, eventos, lecturas ni notificaciones futuras.
- Registro real a 195.200 km: operación común siguiente a 215.200 km y fase B de 40.000 km a 235.200 km. Una revisión parcial deja pendientes las operaciones no seleccionadas. Edición/anulación recupera por operación el último trabajo válido.
- Probar fecha antes que kilómetros y viceversa, ambas causas simultáneas, precisión parcial, fin de mes y zona horaria. Conservar base y fecha configuradas; no crear mantenimiento inicial ni servicios ficticios.

## Operaciones y registro

- Crear y editar una operación propia por km, tiempo o ambos sin URL/fuente obligatoria. Conservar el mismo id, notas, historial y copia. Desactivar recordatorios mantiene límites consultables y trabajos, y evita emisión automática.
- Crear una tarea única con fecha y/o km absolutos; cada dimensión puede vencer. Completarla no programa una repetición; anular ese trabajo recupera sus límites originales. Comprobar también una regla del fabricante personalizada explícitamente como tarea única.
- Personalizar una fase a 10.000 km/6 meses como fixture: eliminar el desfase inicial de 20.000 km/12 meses. Con base 175.000, próximo 185.000; este intervalo de prueba no se atribuye al fabricante. Restaurar recupera la definición original y conserva eventos/revisiones.
- Cambiar solo nombre/notas y restaurar una personalización solo de metadatos conserva la generación y el ledger emitido. Cambiar frecuencia cancela el aviso obsoleto y recalcula; el formulario debe ofrecer edición por operación dentro de grupos.
- Visita nueva inicia sin todas las operaciones marcadas. Probar «Seleccionar todas», selección parcial y confirmación de fecha/km/selección antes de guardar. Editar una visita carga sus operaciones existentes; una reparación libre no crea una regla periódica.
- Buscar en Historial por título, operaciones, taller y notas. «Aceite» encuentra una revisión de título genérico que incluya esa operación. Comprobar navegación, textos españoles, euros, legibilidad móvil y detalle de fases sin presentar una frecuencia única para reglas distintas.

## Filtros y pagos

- Combinar mes/año/tipo, limpiar filtros y conservar la selección al navegar/recrear pantalla. «Todos los años» con un mes filtra ese mes de todos los años; usar meses españoles y años disponibles. Fechas ISO de gasto no se desplazan por zona horaria.
- Lista y «Total de estos gastos» responden a altas, edición de fecha/tipo y anulaciones. «Total acumulado» incluye todos los gastos activos. Vacío muestra 0 €; cálculos exactos en céntimos. Los filtros son consulta, sin informes, gráficos, consumo ni contabilidad.
- Anular pago vinculado y vincular otro id funciona; solo existe un gasto activo vinculado. Editar visita conserva los metadatos personalizados del pago. Anular visita anula/desvincula su pago y una edición posterior no lo revive.
- Reparto histórico: líneas 150+150 €, pago corregido a 310 €. Guardar nuevas notas/km conserva líneas y pago; cambiar reparto a 160+140 se rechaza sin cambios parciales, y 160+150 se acepta. No redistribuir automáticamente la diferencia.
- Normalizar datos existentes y copias 2/3 con gastos inactivos que aún enlazan una visita. Preservar su fila y auditoría, liberar el vínculo y permitir un pago nuevo. Restaurar repetidamente sobre datos locales no duplica importes ni borra pagos distintos. Gasolina no cambia kilómetros ni mantenimiento.

## Avisos, copias y entrega

- Probar aviso Android real al cruzar límite de km y al alcanzar fecha con interfaz cerrada. Editar una regla cambia inmediatamente su calendario/avisos; nombre/notas no repiten emisión. Desactivar recordatorios cancela el aviso de esa operación. La previsión futura nunca genera avisos.
- Actualizar una instalación v3 con un vencimiento ya emitido: el alias de generación conserva el estado del ciclo/ventana y no emite otro aviso por cambiar la versión. Comprobar también estado leído, permiso denegado, canales, reintentos y el recordatorio existente de introducir kilómetros.
- Actualizar APK con misma identidad/firma sin desinstalar; conservar referencia/fecha, lectura, historial, reglas propias/personalizadas, pagos, adjuntos y preferencias. Copias 2/3 y JSON antiguo sin campos nuevos deben cargar con defaults compatibles; Room sigue en esquema 3.
- Ejecutar `testDebugUnitTest`, `lintDebug`, `assembleDebug`, build release firmado y pruebas instrumentadas cuando haya emulador/dispositivo. Verificar pantallas y recorridos principales, actualización real, restauración y notificaciones; distinguir pruebas con transporte simulado de emisión Android real. Entregar APK con versión incrementada, SHA-256 y guía breve. Registrar limitaciones concretas y no publicar ni subir cambios a GitHub.

## Aceptación v3 que sigue siendo aplicable

## Calendario

- Base operativa 175.000, cero trabajos: el plan funciona y no hay mantenimiento inicial por falta de historial. No se crea servicio realizado ni una fecha ficticia de compra.
- Fixture regla 20.000 km: próximo 195.000, no 180.000/200.000 por redondeo. A 184.500 faltan 10.500; a 194.999 aún no vence; a 195.000 vence; a 197.000 permanece pendiente.
- Trabajo válido a 195.200 → siguiente 215.200 para esa recurrencia. Otra operación y simple comprobación no reinician el cambio. Registrar gasto no altera reglas.
- Editar/anular trabajo recomputa referencia correcta. Históricos previos a la base no la desplazan; lectura actual no mueve baseline.
- Plazo de 12 meses con fecha operativa fixture 15/10/2026 → 15/10/2027, salvo ejecución posterior válida. Tiempo o km pueden vencer por separado; ambas causas una tarea/aviso. Fin de mes/bisiesto/zona horaria correctos.
- Hitos únicos, fases B/C y reglas de antigüedad/condición revisados; no aplicar suma genérica a todos. Catálogo completo, fabricante gasolina/automático y condiciones por versión.
- Controles de taller agrupados en su servicio; diarios/semanales sin tarjetas ni alertas por defecto.

## Interfaz

Inicio corto con lectura, próximo límite y faltante. Cuando nada toca muestra «Todo al día». No tarjetas «Mantenimiento inicial»/«no consta una referencia precisa». Detalle: título, intervalo, próximo límite, faltante, último registro y botón. Fuentes en despliegue secundario; ningún id/código inglés/razonamiento largo en bloques principales. Navegación Inicio/Mantenimiento/Historial/Gastos y fuentes accesibles. 320–430 dp, fuentes ampliadas, TalkBack, tema oscuro, teclado/Back y rotación correctos.

Registrar realizado desde tarea en pocos toques con operación/km precargados. Revisión parcial solo cierra operaciones seleccionadas. Fecha pasada no reduce lectura actual. Formularios secundarios opcionales.

## Notificaciones

- Guardar lectura 194.999 → 195.000 produce aviso Android real y bandeja, no solo cambio de estado en pantalla. Verificar en emulador/dispositivo, separado de test de transporte simulado.
- Guardar otra vez mismo km, reabrir y reevaluar worker no repiten vencimiento. Lectura saltando varios límites no pierde tareas; resumen coherente.
- Completar trabajo cancela aviso anterior; nuevo ciclo genera aviso. Reintento/muerte de proceso recupera sin multiplicar ids.
- Tiempo se evalúa con interfaz cerrada mediante WorkManager; sin Internet y tras reinicio. Permiso denegado/canal deshabilitado muestran estado fiel y bandeja disponible.
- Botón «Probar aviso» visible sin alterar mantenimiento; tocar notificación abre detalle/grupo correcto. Documentar límites del SO/forzar detención, sin prometer minuto exacto.

## Gastos y datos

Fixtures: Taller 100 + Extras 25 + Gasolina 60 = 185 €. Editar gasolina a 55 → 180; anular extras → 155. Sin factura/km/relación obligatoria. Un pago de taller 300 con dos líneas no suma 600; pagos distintos no se fusionan por coincidencia. Dinero exacto y doble toque sin duplicación. Reinicio/restauración conserva total; sin gráficos/informes/consumo.

Migración desde app actual con lectura 182.000 conserva 182.000, base175.000, historial/gastos/adjuntos y tareas reales, pero retira pendientes iniciales derivados. Seed/migración reejecutados no duplican ni recrean iniciales. Actualizar APK sin desinstalar con firma/identidad actuales; DB sin borrado. Exportación/restauración completa comprobada por checksums. Datos debug separados.

## Entrega

Build unit tests/lint/debug/release firmado y pruebas Android donde el entorno permita. Reportar comandos/resultados reales, entorno/API y bloqueos específicos. Entregar APK actualizado y 5–8 pasos de prueba. No afirmar app corregida si solo cambiaron etiquetas y el motor todavía genera revisión inicial o no emite aviso al guardar km.

## Cierre de esta entrega

Ver docs/TEST_REPORT.md, docs/v6-tests-verification.json, docs/v6-lifecycle-verification.json y docs/v6-release-install-verification.json: resultados definitivos5.0.0/build6, origen de APK y límites reales. Los informes anteriores son antecedentes.
