# P38 Journal — encargo Android nativo, versión 2.0

Preparado el 10/10/2026. Este paquete contiene documentación de desarrollo y datos iniciales; no contiene todavía la aplicación ni un APK.

## Uso desde una carpeta nueva

1. Descomprime el paquete y abre `p38-android-kit` en VS Code con Codex. Estos documentos son autónomos: no necesitas la conversación ni la documentación anterior.
2. Pega el contenido de `INICIO_CODEX.txt` en Codex. El encargo completo está en `PROMPT_CODEX.md`.
3. Codex debe preparar el proyecto, implementar todas las funciones, revisar el catálogo con agentes, ejecutar las pruebas y entregar un APK instalable con pasos breves para probarlo.

La plataforma queda decidida: **app Android nativa, Kotlin, Jetpack Compose, Room y WorkManager**. La entrega es un APK; el desarrollo se puede llevar desde VS Code y Gradle. Esta versión sustituye íntegramente la especificación anterior de PWA. No crear frontend web, WebView como app, servidor, PostgreSQL, Docker, Web Push ni alojamiento para cumplir este encargo. No se necesita publicación en Google Play.

## Decisiones de producto

Range Rover P38 4.6 V8 Autobiography, año 2000, gasolina, automático, comprado con 175.000 km. Español, kilómetros y euros. Uso personal en un teléfono, datos privados locales y funcionamiento principal sin conexión.

La app trae las tareas y reglas verificadas del fabricante. El propietario registra kilómetros y trabajos reales, recibe avisos y consulta el historial. Inicio: «Hacer ahora», «Próximamente» y «Programado», sin bloques «Por aclarar» ni «Punto de partida». Mantenimiento inicial accionable, respetando lo ya realizado. Gastos manuales Taller/Extras/Gasolina/Otros: lista y total «Lo que llevo gastado», sin informes ni cálculos de consumo.

## Archivos

- `PROMPT_CODEX.md` y `INICIO_CODEX.txt`: instrucciones para ejecutar el encargo.
- `AGENTS.md`: autonomía, coordinación y revisión independiente.
- `SPEC.md`: funciones y pantallas.
- `ANDROID.md`: arquitectura, permisos, compilación, firma y APK.
- `ENGINE.md`: cálculo de mantenimiento, persistencia, avisos y gastos.
- `QA.md`: pruebas y definición de terminado.
- `HANDOFF.md`: secuencia y entrega verificable.
- `SOURCES.md`: manuales y referencias Android.
- `data/vehicle.json`: coche e intervenciones anteriores con su procedencia.
- `data/maintenance-starter.json`: 11 reglas iniciales, no el catálogo final. Los agentes deben completarlo y revisarlo.
- `MANIFEST.json`: versión, lista de archivos y checksums.

## Acciones normales del propietario

Instalar el APK, abrirlo, permitir los avisos si quiere recibirlos e introducir la lectura actual. Codex debe hacer el trabajo técnico de desarrollo y compilación. El aviso por kilómetros depende de lecturas introducidas, sin conexión automática al coche. Android puede retrasar trabajo en segundo plano; una ventana de mañana no garantiza una hora exacta. El cierre forzado desde Ajustes requiere volver a abrir la app para reactivar su funcionamiento normal.

Actualizar con la misma identidad y firma debe conservar los datos. Desinstalar elimina los datos privados: ofrecer respaldo externo completo y restauración. Una copia dentro del mismo teléfono no cubre su pérdida.


## Producto Android construido

La implementación nativa está en `app/`. APKs instalables en `dist/`: release firmado y debug con datos separados. Guía: [docs/USER_TEST.md](docs/USER_TEST.md); verificaciones reales: [docs/TEST_REPORT.md](docs/TEST_REPORT.md); catálogo completo: [docs/CATALOGUE.md](docs/CATALOGUE.md); revisión independiente: [docs/INDEPENDENT_REVIEW.md](docs/INDEPENDENT_REVIEW.md); compilación/firma/privacidad: [docs/BUILD_AND_PRIVACY.md](docs/BUILD_AND_PRIVACY.md). No requiere cuenta, backend ni red para el mantenimiento local.
