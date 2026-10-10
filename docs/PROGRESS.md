# Plan y progreso — 10/10/2026

Documentación original leída. Encargo vigente Android v2 en esta raíz, distinto del proyecto SAP. JDK 21 y SDK presentes; sin teléfono conectado. Se delegan catálogo, dominio/persistencia e interfaz con escrituras separadas y contratos en CONTRACTS.md. Coordinador: entorno/WorkManager/archivos/firma/integración. Revisión independiente tras primeras implementaciones.

## Implementación y revisión completadas

- Proyecto Kotlin/Compose/Material 3 con Room, DataStore, WorkManager, archivos privados/SAF y visor PDF nativo. Inicio/Plan/Historial/Más con visitas, lecturas, gastos, averías, piezas, documentos, avisos, ajustes y copias completas.
- Contratos y escrituras separados entre agentes reales: catálogo, dominio/persistencia, interfaz y revisión independiente; coordinación integra plataforma, SDK, firma, builds y verificaciones.
- Catálogo de 60 reglas, revisión 2, cobertura de las 59 filas de M01, siete exclusiones documentadas y condiciones explícitas. Lectura visual de matriz/notas/glosario y contraste propio del revisor. PDFs de investigación privados excluidos de git/APK.
- Nueve hallazgos de revisión corregidos. Ciclos desde operación real, actualización de revisiones sin perder overrides, perfiles condicionados separados con evidencia, restauración de overrides sobre seed y conservación de personalizaciones locales.
- SDK 36 oficial instalado, JDK 21 disponible, emulador API 36 funcionando. APK release firmado con RSA 3072, clave privada persistente fuera del workspace; APK debug con applicationId independiente. APKs en `dist/`, versionCode 2.

## Evidencia real obtenida

- `scripts/build.ps1`: unit tests, lint, debug y release **BUILD SUCCESSFUL**. 26 pruebas unitarias aprobadas, lint 0 errores/33 advertencias.
- `connectedDebugAndroidTest`: 33 registradas, 32 aprobadas/0 fallos/1 omisión intencional de prueba por etapas. XML conservado en `docs/evidence/`. Cuatro recorridos Compose, 16 Room/migración, 8 archivos/avisos, 2 PDF y 2 Android real.
- Firma de ambos APK verificada con apksigner; metadatos minSdk 26/targetSdk 36/identidades separadas; APKs instalados realmente en el emulador. Hashes en `dist/SHA256SUMS.txt`.
- `verify-lifecycle.py`: tres etapas PASS, actualización candidato real versionCode 1 → APK 2 sin desinstalar, seguida de desinstalación debug aislada/reinstalación/restore completo dos veces. Comparación de gasto/lectura/avería/PDF/hash/marcador sin duplicados. Fixtures retirados después.
- Revisión visual de Inicio/Plan a 320/360/390/430 dp y 320 dp oscuro con letra 130%; colores predeterminados de contenedores Material 3 corregidos. Capturas finales en `docs/screenshots/`.
- Guía breve de ocho pasos en `docs/USER_TEST.md`, instrucciones `dist/LEEME.md`, detalle `docs/TEST_REPORT.md`, firma/privacidad `docs/BUILD_AND_PRIVACY.md`, revisión `docs/INDEPENDENT_REVIEW.md`.

## Continuidad

Entrega completada: APKs definitivos firmados/instalados/verificados y documentación disponible. Prueba nativa adicional `runtime-verification.json`: cinco comprobaciones PASS (avisos visibles offline y PendingIntent; WorkManager real después de Home; proceso; reinicio sin abrir antes de recuperar trabajos; detención forzada seguida de reapertura). Revisión independiente cerrada. No volver a sembrar ni borrar datos personales para repetir pruebas; los scripts destructivos sólo operan en debug de `emulator-5554`. Ningún teléfono físico probado. Restricciones Android/OEM/SAF/TalkBack y estética menor de etiqueta de zona horaria al 130% se documentan, sin inventar verificaciones.

Para reproducir como desarrollador: `scripts/bootstrap.ps1`, `scripts/build.ps1` y `gradlew.bat connectedDebugAndroidTest`. Release necesita la configuración privada externa. Para instalar, sólo APK e instrucciones de `dist/`.
