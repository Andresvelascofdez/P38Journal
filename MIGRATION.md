# Aplicar v3 a la app existente

No es un proyecto nuevo. Inspeccionar código, Room/schema, catálogo, seed, generador de pendientes, interfaz, notificaciones, applicationId y firma actuales. No borrar datos ni resolver migración desinstalando. Crear respaldo verificable antes de cambios.

1. Agregar referencia operativa 175.000 km, con procedencia user_selected_planning_baseline, separada de lectura actual y eventos. Mantener lectura más reciente del propietario. Si no hay ninguna, valor inicial 175.000 identificado sin inventar una lectura hoy. Recuperar fecha de creación del plan si existe; si no, fecha de migración como referencia temporal editable, no como fecha de servicio.
2. Desactivar por completo el generador de mantenimiento inicial por ausencia de historial, incluido después de reinstalar seed/restaurar. Cambiar política de reglas sin ejecución a base operativa. Auditar intervalos/hitos, reutilizando catálogo existente validado.
3. Retirar tareas derivadas automáticas de revisión inicial y cancelar avisos de esos ids/generaciones. Determinar origen con relaciones/metadata/migración; no borrar por coincidencia de título. Conservar los registros de trabajos realmente realizados, gastos, adjuntos, lecturas, notas y tareas manuales del propietario. Si no hay origen fiable, desactivar la instancia derivada sin destruir evidencia ni registro asociado; documentar resolución.
4. Reubicar controles diarios/semanales en referencia del manual sin alertas por defecto. Agrupar inspecciones de taller dentro del servicio al que pertenecen. Catálogo mantiene hechos oficiales; UI no repite cada punto como pendiente desde 175.000.
5. Cambiar navegación/listas/detalle; limpiar códigos ingleses, textos técnicos y mensajes de falta de referencia de pantallas normales. Guardar notas de cobertura en docs y fuente desplegable.
6. Recalcular pendientes desde base y trabajos activos. Reconstruir ledger/WorkManager sin avisos duplicados ni pendientes iniciales. Guardar lectura debe generar notificación por cruce inmediatamente.
7. Ejecutar migración dos veces en fixture: segundo pase no duplica ni cambia referencias. Probar actualización del APK con misma firma y versionCode superior, preservando registros/costes/adjuntos.

No cambiar el valor real del odómetro a 175.000 si el propietario ya registró más kilómetros. No inventar historial «todo realizado a 175.000» para hacer funcionar cálculo. No marcar como hecho trabajo alguno por quitar una tarea automática. El informe final muestra datos preservados y comportamiento corregido, sin volcar datos privados en logs.
