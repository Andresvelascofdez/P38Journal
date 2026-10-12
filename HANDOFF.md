# Entrega P38 Journal 5.0.0 · build 6

El encargo vigente es [REQUEST_V6.md](REQUEST_V6.md). Se amplía la app existente conservando Kotlin/Compose/Room/WorkManager, identidad, clave de firma original y datos. La referencia operativa y su fecha, catálogo, calendario del fabricante, revisiones proyectadas, registro parcial y filtros de gastos se mantienen. No hay publicación ni servicios nuevos.

Las nuevas funciones son trabajos/mejoras previstos o realizados, documentación libre con renovación manual, repostajes y consumo medio entre llenados completos, y ruedas por eje. Previsto→realizado conserva Visit.id y no convierte el coste estimado en gasto. Repostaje↔gasto se sincroniza y cuenta una vez. Renovar requiere fecha confirmada. Revisar ruedas conserva el montaje/uso; cambiar ambas comparte visita/pago. Los datos desconocidos quedan sin fecha/km/litros inventados.

Room 4 añade tablas y campos compatibles mediante migración 3→4; las copias admiten snapshot 2/3/4. El respaldo privado previo incluye SQLite+WAL y validación. La restauración conserva modificaciones locales; el historial de ruedas incorporado que entra en conflicto no sustituye el seguimiento actual ni recibe una anulación ficticia. Corregir/anular ese histórico protege el trabajo y pago local vinculados.

Artefactos: [release 5.0.0](dist/p38-journal-5.0.0-release.apk), [debug separado](dist/p38-journal-5.0.0-debug.apk), [guía breve](docs/USER_TEST.md) y [hashes](dist/SHA256SUMS.txt). Instalar release como actualización sin desinstalar. Release: `dev.p38journal`; debug: `dev.p38journal.debug`. Clave privada externa, no añadida al repositorio.

El cierre de verificación de build 6 corresponde al coordinador y se recoge en [TEST_REPORT](docs/TEST_REPORT.md): comandos y resultados, firma/versionCode, actualización con datos, copias, pruebas Android y limitaciones. Este documento no atribuye a build 6 las pruebas de 4.1 ni afirma un commit o una comprobación todavía pendiente.
