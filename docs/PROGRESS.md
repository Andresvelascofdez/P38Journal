# Corrección v3 completada — 11/10/2026

Leídos AGENTS, PROMPT_CODEX, SPEC, ENGINE, MIGRATION, QA y documentación del paquete v3. Cambios del propietario preservados; antes de implementar se conservaron fuente, APKs v2 y paquete recibido en `.tools/pre-v3` con hashes. [Progreso v2](PROGRESS_V2.md) queda como antecedente histórico.

## Implementación completada

- Agentes de dominio/migración, interfaz, catálogo y revisión independiente trabajaron con contratos compartidos; el coordinador integró plataforma, builds, firma y pruebas reales.
- Referencia operativa persistida de 175.000 km separada de lecturas/trabajos; fecha explícita editable. Intervalos verificados y ciclos posteriores por operación real y precisión de cada dimensión. Sin generador INITIAL ni revisiones por falta de historial.
- Room esquema 3, copia SQLite/WAL previa verificada, migración no destructiva y retiro exclusivo de derivados automáticos conocidos. Datos, documentos, gastos, referencias manuales y personalizaciones preservados. ZIP v2/v3 e importación repetida sin duplicados.
- Inicio/Mantenimiento/Historial/Gastos, seis grupos iniciales, registros parciales, detalle legible, fuentes y controles secundarios. Catálogo 62 reglas/revisión 3, cobertura de 59 filas del fabricante, condiciones y exclusiones documentadas.
- Guardar lectura evalúa límites y emite aviso Android inmediatamente cuando corresponde; fechas con WorkManager, deduplicación persistida, PendingIntent correcto y resumen parcial silencioso. Gastos exactos en céntimos.
- Kotlin/Compose/Room/WorkManager existentes conservados; release `dev.p38journal`, firma original y datos privados. Debug separado. Sin servidor ni permiso INTERNET.

## Verificaciones reales cerradas

- Build serializado final y build de entrega: SUCCESS; **32 unitarias y 48 Android aprobadas**, 0 fallos, 1 test por etapas omitido en la suite y ejecutado aparte. Lint 0 errores/33 advertencias. XML y logs en `docs/evidence/v3` y `docs/v3-*.log`.
- Ciclo real debug v2→v3 sin desinstalar, con fixture controlada 182.000 km, trabajos reales de prueba a 178.000, gasto/pago, nota, PDF y marcador; preservación comprobada. Copia previa esquema 2 íntegra. Reinstalación y dos restauraciones exclusivamente en debug del emulador: PASS.
- Cinco comprobaciones runtime PASS: avisos visibles offline y destinos, cruce manual 194.999→195.000, continuidad tras muerte normal del proceso, reinicio real con reprogramación y force-stop seguido de reapertura. Worker por fecha ejecutado realmente sin actividad abierta; no se garantiza ejecución durante force-stop.
- Capturas Inicio/Mantenimiento a 320/360/390/430 dp y oscuro a 320 dp con fuente 130%; avisos/destinos, proceso/reinicio y release revisados. Diez hallazgos independientes corregidos; revisión cerrada sin defectos funcionales pendientes conocidos.
- Release 3.0.0 firmado, certificado igual al v2; apksigner y hashes comprobados. Actualizado realmente sobre release 2.0.0 del emulador mediante install-r: Success y arranque nativo, sin desinstalar ni borrar datos.

## Entrega y continuidad

APKs definitivos en `dist/p38-journal-3.0.0-release.apk` y `dist/p38-journal-3.0.0-debug.apk`, con checksums e instrucciones. [Guía de ocho pasos](USER_TEST.md), [informe real](TEST_REPORT.md), [revisión independiente](INDEPENDENT_REVIEW.md), [catálogo](CATALOGUE.md) y [compilación/firma/privacidad](BUILD_AND_PRIVACY.md).

Solo se probó Android 16/API 36 en emulador; Android 8/API 26 es el mínimo declarado. No se probó un teléfono físico, otros OEM ni TalkBack con audio; SAF/revocación real no tienen recorrido automatizado completo. Se conservan restricciones Android y detalles estéticos menores documentados. No hay bloqueo de compilación ni APK pendiente. Los datos fixture están aislados en debug del emulador y no se incluyen en APK; no volver a sembrar ni borrar datos personales.

Para reproducir como desarrollador: `scripts/bootstrap.ps1`, `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`. Release usa configuración privada externa existente. El propietario solo instala el APK y sigue la guía. Fuente, informes, capturas y sumas se guardan en git; APKs, claves y PDFs de investigación quedan excluidos.
