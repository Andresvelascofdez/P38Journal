# Compatibilidad y actualización v4

`REQUEST_V4.md` es el encargo vigente y prevalece cuando amplía o corrige v3. La actualización conserva applicationId, firma original, datos y fecha configurada del plan. No publicar cambios ni resolver una actualización desinstalando. La referencia de 175.000 km sigue separada de lecturas y trabajos reales; ni la previsión ni la ampliación de reglas crean eventos.

## Room y formato de copia

Room permanece en esquema 3: esta ampliación no añade columnas ni cambia índices. Las reglas ya se almacenan como JSON; se añaden `origin`, `notes`, `enabled`, `dueDate`, `dueKm` y `manufacturerRevision`. El snapshot exportado también conserva `schemaVersion=3` y se admiten copias de esquema 2. Los campos ausentes se normalizan al leer: los recordatorios existentes quedan activados y las reglas del catálogo conservan su política; se distinguen personalizaciones y operaciones propias sin exigirles una fuente inventada.

La regla conserva su id al editar, junto con sus eventos y revisiones anteriores. La fuente del fabricante permanece en el catálogo y en el historial de revisiones. Personalizar una fase como intervalo simple elimina `firstKm`/`firstMonths`; restaurar el fabricante recupera su definición sin eliminar trabajos reales. Una tarea única propia o personalizada usa fecha/km absolutos y no se repite tras una ejecución activa. Desactivar recordatorios mantiene los límites consultables y el historial.

Las copias completas siguen incluyendo registros, reglas, referencia, adjuntos y preferencias con validación de formato/checksums. La restauración combina ids, conserva modificaciones locales y no duplica pagos ni trabajos. El ledger de emisiones no se importa como prueba de una notificación emitida en otra instalación. La política existente de respaldo privado antes de abrir una base 1/2 continúa vigente: copia SQLite y WAL cuando existe, verifica hashes e integridad y detiene la migración si falla. Los adjuntos privados no se modifican en esa migración. Una actualización desde Room 3 utiliza normalización transaccional de datos, sin una migración destructiva ni un cambio artificial de versión de esquema.

## Normalización de vínculos y pagos

Una visita tiene como máximo un gasto activo vinculado. Un gasto anulado se conserva con `visitId=null`; su relación anterior queda en auditoría. La inicialización y las mutaciones/restauraciones normalizan enlaces inactivos antiguos antes de crear otro vínculo. También se respeta la anulación de visitas: sus pagos vinculados se anulan y desvinculan. Repetir la normalización no crea pagos ni duplica auditorías de una relación ya corregida.

El pago activo vinculado es el importe canónico de la visita y se cuenta una sola vez. Editar notas, kilómetros u operaciones de una visita conserva fecha, concepto, categoría y notas personalizados de ese gasto. Crear otro pago tras anular el anterior utiliza un registro nuevo; no resucita silenciosamente la fila anulada. Si una copia aporta otro pago cuyo vínculo está ocupado por un pago local, se conserva el registro financiero independiente y se audita la desvinculación, sin infringir el índice único ni fusionar pagos distintos.

Los costes de líneas pueden conservar un reparto histórico después de corregir el pago desde Gastos. Ejemplo de prueba: dos líneas de 150 € y un pago corregido de 300 € a 310 €. Editar solo notas/km admite las líneas originales si sus importes no cambiaron y el total de la visita coincide con el gasto canónico. No se inventa un reparto de 155 € por línea. Si se cambian los importes de las líneas, el reparto completo debe sumar el pago; también se puede dejar el coste de una línea sin especificar conforme al formulario existente. Las correcciones y valores anteriores permanecen en auditoría.

## Continuidad de avisos

La generación v4 se calcula con campos de calendario, límites y referencias efectivas. Cambiar nombre, notas o revisión de una regla no abre otro ciclo; editar frecuencia, activar/desactivar recordatorios o cambiar una referencia efectiva sí provoca reconciliación. La anulación automática solo afecta a registros con procedencia automática, preservando avisos manuales o `legacy_unknown`.

Para actualizar desde v3, el motor proporciona `Task.legacyGeneration`, calculada con la representación exacta de la regla anterior y sus límites/anclas. El evaluador reconoce la clave previa de ese mismo ciclo y ventana y traslada su estado al identificador actual. Así el cambio de algoritmo hash no vuelve a emitir un vencimiento ya emitido; un intervalo realmente cambiado produce un ciclo distinto. Este alias no crea historial de mantenimiento ni modifica la referencia del plan.

## Verificación de actualización

Comprobar instalación de la APK nueva sobre v3 con la misma firma y versionCode superior, sin borrar datos. La fixture debe incluir referencia/fecha configuradas, lectura real, trabajos, un vínculo inactivo antiguo, pago personalizado, adjunto, preferencias y un aviso v3 emitido. Verificar conservación, sustitución de pago, edición de visita, ausencia de duplicados por alias y restauración repetida de copias 2/3. Los comandos, resultados y limitaciones reales se registran en el informe de verificación; esta sección define el contrato, no acredita una ejecución.

## Antecedente v3 que sigue siendo aplicable

No es un proyecto nuevo. Inspeccionar código, Room/schema, catálogo, seed, generador de pendientes, interfaz, notificaciones, applicationId y firma actuales. No borrar datos ni resolver migración desinstalando. Crear respaldo verificable antes de cambios.

1. Agregar referencia operativa 175.000 km, con procedencia user_selected_planning_baseline, separada de lectura actual y eventos. Mantener lectura más reciente del propietario. Si no hay ninguna, valor inicial 175.000 identificado sin inventar una lectura hoy. Recuperar fecha de creación del plan si existe; si no, fecha de migración como referencia temporal editable, no como fecha de servicio.
2. Desactivar por completo el generador de mantenimiento inicial por ausencia de historial, incluido después de reinstalar seed/restaurar. Cambiar política de reglas sin ejecución a base operativa. Auditar intervalos/hitos, reutilizando catálogo existente validado.
3. Retirar tareas derivadas automáticas de revisión inicial y cancelar avisos de esos ids/generaciones. Determinar origen con relaciones/metadata/migración; no borrar por coincidencia de título. Conservar los registros de trabajos realmente realizados, gastos, adjuntos, lecturas, notas y tareas manuales del propietario. Si no hay origen fiable, desactivar la instancia derivada sin destruir evidencia ni registro asociado; documentar resolución.
4. Reubicar controles diarios/semanales en referencia del manual sin alertas por defecto. Agrupar inspecciones de taller dentro del servicio al que pertenecen. Catálogo mantiene hechos oficiales; UI no repite cada punto como pendiente desde 175.000.
5. Cambiar navegación/listas/detalle; limpiar códigos ingleses, textos técnicos y mensajes de falta de referencia de pantallas normales. Guardar notas de cobertura en docs y fuente desplegable.
6. Recalcular pendientes desde base y trabajos activos. Reconstruir ledger/WorkManager sin avisos duplicados ni pendientes iniciales. Guardar lectura debe generar notificación por cruce inmediatamente.
7. Ejecutar migración dos veces en fixture: segundo pase no duplica ni cambia referencias. Probar actualización del APK con misma firma y versionCode superior, preservando registros/costes/adjuntos.

No cambiar el valor real del odómetro a 175.000 si el propietario ya registró más kilómetros. No inventar historial «todo realizado a 175.000» para hacer funcionar cálculo. No marcar como hecho trabajo alguno por quitar una tarea automática. El informe final muestra datos preservados y comportamiento corregido, sin volcar datos privados en logs.
