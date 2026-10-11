# Entrega de la corrección v4

El encargo vigente está conservado íntegro en [REQUEST_V4.md](REQUEST_V4.md). Se corrigió el proyecto existente con agentes de dominio/datos, interfaz y revisión independiente. El coordinador integró previsión, filtros puros, plataforma, pruebas de actualización, firma y artefactos. No se publica ni se contratan servicios en este encargo.

APKs 4.0.0/code 4 en `dist/`: release `dev.p38journal`, firmado con el certificado original; debug `dev.p38journal.debug`, separado. Instalar release encima del anterior sin desinstalar. [Guía de ocho pasos](docs/USER_TEST.md), [informe de verificaciones](docs/TEST_REPORT.md), [progreso](docs/PROGRESS.md), [revisión independiente](docs/INDEPENDENT_REVIEW_V4.md) y [hashes](dist/SHA256SUMS.txt).

La base 175.000 y su fecha, el odómetro y los trabajos siguen separados. Vencimientos reales por operación y previsión hipotética tienen recorridos diferentes. La previsión inicial de 215.000 contiene 33 operaciones; B reaparece a 235.000. Trabajo real parcial recalcula solo lo realizado sin redondear. Catálogo canónico sin cambios, fuentes secundarias y seis grupos de próximos límites iniciales.

Cada operación permite consulta, personalización, notas, intervalos y recordatorios. Propias recurrentes/únicas sin URL; restauración de fabricante. Recurrentes existentes conservan su modelo al editar; otra cita única se crea como tarea nueva, evitando que trabajo anterior complete una nueva cita. Visitas sin selección automática y confirmadas, búsqueda por operaciones/taller/notas. Gastos con filtros combinables, importes exactos y pago vinculado único; enlaces inactivos auditados y metadatos personalizados preservados. Si se corrige el pago, no se fabrica otro reparto de costes históricos.

Room permanece en esquema 3; JSON de reglas ampliado y Snapshot 2/3 compatible, sin migración destructiva. Alias de generación anterior conserva ciclos emitidos y bandeja leída cuando el límite no cambia. Avisos reales al guardar km y por fecha con WorkManager; recordatorio de actualizar km opcional. La previsión no crea trabajo ni notificación.

Respaldo previo v3 en `.tools/pre-v4` ignorado con fuente/APKs y hashes; fixtures solamente en debug del emulador. La clave privada permanece externa y no se incluye en código, logs, APK ni copia. Los antecedentes v3 archivados no certifican esta versión; los resultados y límites efectivamente observados figuran en el informe v4.

Continuidad: conservar commits locales y evidencia real, no resembrar ni borrar datos del propietario. Reproducir con `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`; los scripts de ciclo/visual/runtime actúan exclusivamente sobre debug de `emulator-5554`. El usuario solo instala y prueba.
