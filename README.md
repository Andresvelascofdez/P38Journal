# P38 Journal 5.0 · build 6

App Android nativa para el Range Rover P38 Autobiography 4.6 V8 gasolina automático de 2000. Kotlin, Compose, Room y WorkManager; datos y notificaciones locales, sin servidor ni permiso INTERNET.

Instala [el APK release 5.0.0](dist/p38-journal-5.0.0-release.apk) sobre tu instalación anterior, **sin desinstalar**. La entrega conserva `dev.p38journal`, la clave de firma original y tus datos. Android 8 o posterior. El [APK debug](dist/p38-journal-5.0.0-debug.apk) permite probar con datos separados; la cabecera identifica v5.0.0 · 6.

[Instalación y pasos de prueba](docs/USER_TEST.md) · [Informe de verificaciones](docs/TEST_REPORT.md) · [Hashes](dist/SHA256SUMS.txt).

La referencia operativa de 175.000 km y su fecha permanecen separadas del odómetro y los trabajos. Actualizas km a mano y registras las operaciones realmente realizadas; cada una conserva su próximo límite por tiempo o km, lo que llegue antes. No se inventa mantenimiento inicial.

Mantenimiento conserva las revisiones completas: inicialmente 195.000→29, 215.000→33 y 235.000→31 operaciones. El contador y «Qué incluye» usan la misma lista proyectada desde las reglas. Los ciclos futuros presuponen realizar los anteriores en sus límites; no registran trabajos ni reinician intervalos. «Vencimientos reales y pendientes» conserva el calendario operativo para avisos y registro. Un trabajo parcial de aceite a 195.200 mueve solo su vencimiento real a 215.200 y deja las demás pendientes.

Puedes consultar y personalizar cada operación, restaurar el fabricante y crear operaciones propias recurrentes o únicas sin fuente obligatoria. Las visitas empiezan sin operaciones marcadas y requieren confirmar selección, fecha y km. Historial busca títulos, operaciones, taller y notas. Gastos ofrece filtros combinables de mes/año/tipo, subtotal filtrado y total acumulado exactos; conserva pagos vinculados y su auditoría.

La ampliación añade Trabajos y mejoras con estados Previsto/Realizado, documentación de nombre libre y renovación manual, repostajes con gasto gasolina único y consumo sencillo entre llenados completos, y seguimiento de ruedas delanteras/traseras. Fecha/km de trabajos históricos y litros/km de repostajes pueden ser desconocidos; no se rellenan inventándolos. Un coste previsto no genera pago, y convertir un trabajo conserva su identidad. Revisar ruedas mueve su aviso sin reiniciar el uso desde montaje; cambiar ambas comparte trabajo y gasto.

Inicio incorpora acciones rápidas y consumo medio. Documentación y ruedas tienen acceso secundario; se mantienen las cuatro pestañas y los filtros de gastos. La app conoce únicamente las lecturas introducidas manualmente.

El encargo vigente es [REQUEST_V6.md](REQUEST_V6.md). [Contrato v6](docs/CONTRACT_V6.md), [ENGINE](ENGINE.md), [MIGRATION](MIGRATION.md), [QA](QA.md) y [CATALOGUE](docs/CATALOGUE.md) documentan el comportamiento. Room y snapshot pasan a esquema 4; se admiten copias 2/3/4 con validación de relaciones y respaldo SQLite+WAL previo a la migración. El catálogo del fabricante se conserva. Consumo sencillo está autorizado; informes, gráficos, OCR, OBD, backend y seguimiento avanzado de ruedas permanecen fuera del alcance. No se publica esta entrega.

Para desarrollo: `scripts/bootstrap.ps1`, `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`. [Compilación, privacidad y firma](docs/BUILD_AND_PRIVACY.md). El propietario recibe los APK compilados. El informe debe identificar las comprobaciones efectivamente ejecutadas para build 6; los resultados de versiones anteriores son antecedentes.
