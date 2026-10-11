# P38 Journal 4.1

App Android nativa para el Range Rover P38 Autobiography 4.6 V8 gasolina automático de 2000. Kotlin, Compose, Room y WorkManager; datos y notificaciones locales, sin servidor ni permiso INTERNET.

Instala [el APK release actualizado](dist/p38-journal-4.1.0-release.apk) sobre tu instalación anterior, **sin desinstalar**. Conserva `dev.p38journal`, la firma original y tus datos. Android 8 o posterior. El [APK debug](dist/p38-journal-4.1.0-debug.apk) permite probar con datos separados.

[Instalación y pasos de prueba](docs/USER_TEST.md) · [Verificaciones reales y límites](docs/TEST_REPORT.md) · [Revisión independiente v5](docs/INDEPENDENT_REVIEW_V5.md) · [Hashes](dist/SHA256SUMS.txt).

La referencia operativa de 175.000 km y su fecha permanecen separadas del odómetro y los trabajos. Actualizas km a mano y registras las operaciones realmente realizadas; cada una conserva su próximo límite por tiempo o km, lo que llegue antes. No se inventa mantenimiento inicial.

Mantenimiento abre directamente las siguientes revisiones completas: inicialmente 195.000→29, 215.000→33 y 235.000→31 operaciones. El contador y «Qué incluye» usan la misma lista proyectada desde las reglas. Los ciclos futuros presuponen realizar los anteriores en sus límites; no registran trabajos ni reinician intervalos. «Vencimientos reales y pendientes» conserva el calendario operativo para avisos y registro. Un trabajo parcial de aceite a 195.200 mueve solo su vencimiento real a 215.200 y deja las demás pendientes. La cabecera identifica v4.1.0 · 5.

Puedes consultar y personalizar cada operación, restaurar el fabricante y crear operaciones propias recurrentes o únicas sin fuente obligatoria. Las visitas empiezan sin operaciones marcadas y requieren confirmar selección, fecha y km. Historial busca títulos, operaciones, taller y notas. Gastos ofrece filtros combinables de mes/año/tipo, subtotal filtrado y total acumulado exactos; conserva pagos vinculados y su auditoría.

El encargo vigente es [REQUEST_V5.md](REQUEST_V5.md). [ENGINE](ENGINE.md), [MIGRATION](MIGRATION.md), [QA](QA.md), [CONTRACTS](docs/CONTRACTS.md) y [CATALOGUE](docs/CATALOGUE.md) documentan el comportamiento. El catálogo canónico de 62 reglas/revisión 3 conserva los intervalos verificados; la previsión no modifica las reglas ni genera avisos o trabajos. Los informes de gastos, consumo, backend y publicación están fuera del encargo.

Para desarrollo: `scripts/bootstrap.ps1`, `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`. [Compilación, privacidad y firma](docs/BUILD_AND_PRIVACY.md). El propietario recibe los APK ya compilados. Los antecedentes v3 están archivados en `docs/*_V3.md`; no certifican la entrega4.1.
