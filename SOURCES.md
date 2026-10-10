# Fuentes para implementación y verificación

Consultadas el 10/10/2026. Los manuales son publicaciones del fabricante alojadas en una web de terceros. No son una reproducción del manual completa ni un catálogo final: los agentes deben verificar y completar la extracción antes de la entrega.

## Fabricante

### M01 — Hoja de mantenimiento 04/00, LRL0354SPA

https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Maintenance_Sheet_2000_ES.pdf

Dos páginas. Revisar página PDF 1 (matriz) y 2 (glosario/notas). Servicio principal: 12 meses/20.000 km. B/C alternan; la lubricación intermedia posterior al primer servicio es de diésel. Filtrar por gasolina, automático, VIN y equipamiento. La extracción textual pierde puntos de columnas: leer el original visual.

### M02 — Manual 2000/2001 británico, LRL0314ENG, 3.ª edición

https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_2000_UK.pdf

211 páginas PDF. Páginas impresas 135–136 (PDF 138–139) para controles y mantenimiento; impresa 146 (PDF 149) para frenos. Controles diarios; semanales o cada 400 km; mensuales. Las operaciones/intervalos concretos se preservan en el JSON inicial con referencia. Distinguir niveles de sustituciones. La inspección del eje intermedio es 24 meses/40.000 km; líquido de frenos, 36 meses/60.000 km. Componentes hidráulicos, 6 años/120.000 km. Reglas sujetas a lectura completa y compatibilidad.

### M03 — Manual 1999 en español, VDH100730

https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_1999_ES.pdf

Ayuda de idioma; no sustituye la edición correspondiente cuando haya diferencias de año.

### M04 — Suplemento 2000 en español

https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_Supplement_2000_ES.pdf

Leer qué modifica; no tratar como manual principal completo.

## Catálogo: trabajo requerido

Inventariar operaciones de M01 con decisión incluida/excluida/condicional y nota correspondiente. Contrastar con M02/M04, documentar divergencias y evitar elegir silenciosamente. El starter no cubre toda la hoja. No inventar intervalos por desgaste (neumáticos, pastillas, balonas) ni características desconocidas. Preservar condiciones y distinguir revisiones, sustituciones e intervenciones profesionales. Revisar también notas de antigüedad y seguridad, sin convertirlas en instrucciones DIY.

Extraer hechos y referencias, con paráfrasis breves; no copiar publicaciones completas en la documentación del repositorio ni distribuir PDFs en público. Puede dejarse una biblioteca privada con los documentos aportados legítimamente por el propietario.

## Codex

AGENTS.md: https://learn.chatgpt.com/docs/agent-configuration/agents-md

Subagentes: https://learn.chatgpt.com/docs/agent-configuration/subagents

Las instrucciones de proyecto y el prompt solicitan expresamente delegación. Los roles son división del trabajo, no una afirmación de que todos los entornos dispongan de las mismas herramientas. Evitar escrituras concurrentes en los mismos archivos.

## Android — referencias oficiales

Verificadas para preparar el encargo; el implementador debe revisar nuevamente compatibilidad/versiones al compilar.

- Compose: https://developer.android.com/compose
- Room: https://developer.android.com/training/data-storage/room
- Trabajo persistente: https://developer.android.com/develop/background-work/background-tasks/persistent
- Periodicidad y flexibilidad: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Permiso de notificaciones: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- SAF/archivos elegidos por usuario: https://developer.android.com/training/data-storage/shared/documents-files
- Gradle/compilación por línea de comandos: https://developer.android.com/build/building-cmdline
- Firma y actualización: https://developer.android.com/studio/publish/app-signing

WorkManager permite trabajo persistente diferible; no garantiza hora exacta. La implementación debe cumplir límites del sistema y no confundir cierre normal con detención forzada. Room almacena datos estructurados localmente. Android 13+ introduce permiso de notificaciones en runtime. SAF usa selección de documentos del usuario; la arquitectura evita acceso general al almacenamiento. La entrega requiere compilar un APK real y mantener su firma para futuras actualizaciones.
