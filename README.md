# P38 Journal 4.0

App Android nativa para el Range Rover P38 Autobiography 4.6 V8 gasolina automático de 2000. Kotlin, Compose, Room y WorkManager; datos y notificaciones locales, sin servidor ni permiso INTERNET.

Instala [el APK release actualizado](dist/p38-journal-4.0.0-release.apk) sobre tu instalación anterior, **sin desinstalar**. Conserva `dev.p38journal`, la firma original y tus datos. Android 8 o posterior. El [APK debug](dist/p38-journal-4.0.0-debug.apk) permite probar con datos separados.

[Instalación y ocho pasos de prueba](docs/USER_TEST.md) · [Verificaciones reales y límites](docs/TEST_REPORT.md) · [Revisión independiente v4](docs/INDEPENDENT_REVIEW_V4.md) · [Hashes](dist/SHA256SUMS.txt).

La referencia operativa de 175.000 km y su fecha permanecen separadas del odómetro y los trabajos. Actualizas km a mano y registras las operaciones realmente realizadas; cada una conserva su próximo límite por tiempo o km, lo que llegue antes. No se inventa mantenimiento inicial.

Mantenimiento distingue **próximos vencimientos reales** de una **previsión de los primeros cuatro hitos**, que supone realizar las revisiones anteriores en sus límites. Inicialmente hay 29 operaciones reales a 195.000 km y seis a 215.000; la previsión reúne 33 a 215.000, con las comunes y fase C. La fase B reaparece a 235.000. Registrar aceite a 195.200 mueve solo su límite a 215.200.

Puedes consultar y personalizar cada operación, restaurar el fabricante y crear operaciones propias recurrentes o únicas sin fuente obligatoria. Las visitas empiezan sin operaciones marcadas y requieren confirmar selección, fecha y km. Historial busca títulos, operaciones, taller y notas. Gastos ofrece filtros combinables de mes/año/tipo, subtotal filtrado y total acumulado exactos; conserva pagos vinculados y su auditoría.

El encargo vigente es [REQUEST_V4.md](REQUEST_V4.md). [ENGINE](ENGINE.md), [MIGRATION](MIGRATION.md), [QA](QA.md), [CONTRACTS](docs/CONTRACTS.md) y [CATALOGUE](docs/CATALOGUE.md) documentan el comportamiento. El catálogo canónico de 62 reglas/revisión 3 conserva los intervalos verificados; la previsión no modifica las reglas ni genera avisos o trabajos. Los informes de gastos, consumo, backend y publicación están fuera del encargo.

Para desarrollo: `scripts/bootstrap.ps1`, `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`. [Compilación, privacidad y firma](docs/BUILD_AND_PRIVACY.md). El propietario recibe los APK ya compilados. Los antecedentes v3 están archivados en `docs/*_V3.md`; no certifican la entrega v4.
