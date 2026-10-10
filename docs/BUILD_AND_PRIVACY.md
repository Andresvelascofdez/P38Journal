# Compilación, privacidad y firma

Proyecto Kotlin y Compose nativo, mínimo Android 8.0 (API 26), compilación/objetivo Android 16 (API 36). Gradle Wrapper 8.13; AGP 8.10.1; Kotlin/Compose compiler 2.0.21; Compose BOM 2024.12.01; Room 2.6.1; WorkManager 2.10.1; DataStore 1.1.2; JDK ejecutado 21.0.8, bytecode Java 17. Versiones estables fijadas en Gradle y catálogo de versiones. Se usa una combinación compatible explícita, no versiones preview. Resolución desde Google Maven/Maven Central/Gradle oficiales, sin repositorios privados.

Referencias oficiales consultadas: [compatibilidad AGP 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes), [SDK Android 16](https://developer.android.com/about/versions/16/setup-sdk), [Compose compiler Kotlin 2](https://developer.android.com/jetpack/androidx/releases/compose-kotlin), [Room](https://developer.android.com/jetpack/androidx/releases/room), [WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [trabajo periódico](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [permiso de notificaciones](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [firma](https://developer.android.com/studio/publish/app-signing).

## Reproducir como desarrollador

El propietario recibe APKs ya compilados: no necesita estos pasos. Para continuar desarrollo, ejecutar scripts/bootstrap.ps1 y scripts/build.ps1 en Windows, o los equivalentes .sh. Se detecta el SDK indicado por ANDROID_HOME y se genera local.properties privado. Wrapper descarga Gradle oficial. JDK 17/21 y plataforma SDK 36 son requisitos explícitos; bootstrap no modifica rutas globales ni instala servicios. El SDK de esta sesión está en C:/Users/Admin/AppData/Local/Android/Sdk. Se añadió plataforma 36 desde archivo oficial platform-36_r02.zip; aceleración WHPX disponible.

Tests instrumentados: ./gradlew connectedDebugAndroidTest con emulador o teléfono autorizado. La compilación release exige firma privada y falla si falta, evitando entregar un release sin firmar como si estuviera listo. Aumentar versionCode para futuras versiones reales.

## Firma estable

Identidad release dev.p38journal, debug dev.p38journal.debug, versión 2.0.0/versionCode 2. Clave RSA 3072 estable en C:/Users/Admin/.p38journal/release.jks; configuración privada signing.properties en la misma carpeta. Directorio fuera del repositorio con ACL restringida a Admin y SYSTEM. Gradle puede leer otra ruta mediante P38_SIGNING_PROPERTIES. No se incluyen contraseñas ni claves en código, APK, respaldos, logs o git.

El propietario debe conservar una copia cifrada y privada de esa carpeta completa en un medio seguro. No enviar la clave por chat/correo ni publicarla. La misma firma y applicationId permiten actualizar sin desinstalar; perder la clave impide garantizar esa actualización. Los APKs incluyen el certificado público necesario para Android, nunca la clave privada. APK debug usa la clave de depuración de Android y una identidad diferente.

## Datos y archivos

Room guarda catálogo/revisiones, vehículo, visitas/líneas/eventos, gastos, lecturas, notas estructuradas, documentos, bandeja/ledger y auditoría. Snapshot lógico versionado exporta todas esas tablas salvo ledger de emisión, que se reconstruye. Preferencias de producto van en DataStore. Copia ZIP añade ajustes, marcadores de páginas y adjuntos privados con SHA-256; rechaza rutas arbitrarias, entradas duplicadas, formatos/versiones o hashes incorrectos. Límites: documento 32 MB, copia descomprimida 256 MB, 2.000 archivos, índice 8 MB. No hay cuentas, backend, telemetría, GPS ni acceso general al almacenamiento. Android Auto Backup desactivado; se usa respaldo explícito completo.

Restore valida y prepara todos los datos antes de escribir. Integra nuevos IDs y conserva existentes (incluidas ediciones/anulaciones): sobre instalación vacía reconstruye la copia; sobre instalación usada no deshace cambios locales. Excepción de reglas: una personalización de la copia sustituye el seed estándar; los overrides locales prevalecen; las reglas estándar sólo suben de revisión. Bytes nuevos se copian a archivos privados antes de la transacción Room, con retirada si ésta falla. Preferencias se revierten si falla DB. No se sobrescribe un adjunto de igual ruta con hash distinto. Avisos desactivados al restaurar para activar conscientemente en el teléfono de destino. No se restauran permisos SAF de otro móvil.

Tres copias internas rotadas, como máximo una al día, dependen de WorkManager y no sobreviven a desinstalar/perder el teléfono. Copia automática externa diaria sólo tras elegir carpeta SAF con permiso persistente; el estado informa errores/revocaciones. Exportar un ZIP a otro dispositivo/medio sí protege frente a pérdida del teléfono. Las copias contienen información privada del propietario; almacenarlas en el destino elegido bajo su control.

## Documentos y licencias

Visor nativo android.graphics.pdf.PdfRenderer, con páginas PDF separadas de numeración impresa, zoom, navegación y múltiples marcadores. Extracción/búsqueda textual con [PDFBox-Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android), Apache License 2.0; usa PDFBox/FontBox (Apache 2.0) y Bouncy Castle (licencia permisiva Bouncy Castle). No se aplica OCR. PDFs sin texto, cifrados o no compatibles indican búsqueda no disponible. Extracción limitada a 1.000 páginas y 100.000 caracteres por página para proteger memoria; el visor puede abrir documentos dentro del límite de tamaño. PDFBox-Android es una biblioteca Android pública, no un servidor ni WebView.

AndroidX/Compose/Room/WorkManager/DataStore son Apache 2.0; Kotlin Apache 2.0; Gson Apache 2.0; JUnit EPL 1.0 para pruebas. [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0), [Bouncy Castle](https://www.bouncycastle.org/licensing/). Las licencias de componentes no transfieren derechos sobre manuales del fabricante. PDFs de investigación privados quedan en private-reference/ y no se distribuyen en git, APK ni dist. Sólo referencias y paráfrasis/reglas breves acompañan el catálogo.

## Límites del sistema

WorkManager evalúa sin requisito de red cada hora de forma flexible y después de cambios; la ventana elegida (09:00 inicialmente) no garantiza el minuto de entrega. Permiso y canales controlados por Android. Bandeja independiente; emitted significa llamada al sistema, no recibido/leído. Resúmenes agrupados y ledger persistente; fecha y km no crean dos avisos de una tarea. Forzar detención desde Ajustes impide prometer trabajo hasta abrir de nuevo; quitar de recientes es distinto. Avisos por km usan sólo lecturas introducidas, nunca estimaciones de conducción.
