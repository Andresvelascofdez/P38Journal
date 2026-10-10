# Ejecución y entrega

## Secuencia

1. Leer paquete e inspeccionar workspace nuevo, entorno y restricciones. Crear proyecto Android en esta raíz, contratos, plan y progreso. Conservar documentación; no crear una segunda app web.
2. Repartir catálogo, motor/persistencia, Compose y operación Android con límites de escritura. Integrar pronto tareas → registro → cálculo → bandeja/notificación local.
3. Completar todas las pantallas y copias; revisar catálogo de forma independiente. Corregir hallazgos antes de entrega.
4. Compilar, ejecutar pruebas y validar en emulador/dispositivo accesible. Probar actualización/restauración y avisos con interfaz cerrada.
5. Entregar APKs e instrucciones. El usuario instala/prueba; los agentes preparan y verifican el código.

## Archivos que debe producir Codex

Proyecto Android completo, Gradle Wrapper/configuración fijada, migraciones, catálogo completo/versionado, cobertura y fuentes, tests, scripts Windows/Unix, documentación de compilación/firma/copias, `docs/PROGRESS.md`, `docs/TEST_REPORT.md`, `docs/USER_TEST.md` y APK debug/release firmado en `dist/` con checksums. Configuración de firma solo de ejemplo sin secretos; material privado fuera de git.

## Informe final

Enlaces/rutas reales a APKs, compatibilidad y pasos breves para instalar. Qué se verificó: dominio, build, lint, DB/migración/copias, UI y recepción de avisos, diferenciando automatización/emulador/teléfono. Declarar cualquier bloqueo concreto y su efecto. No pedir al propietario resolver arquitectura o compilar como entrega normal.

Guía de 5–8 pasos: instalar/abrir, registrar lectura, consultar tareas y fuente, registrar trabajo en debug con fixtures, comprobar vencimientos, probar aviso, añadir gasto y revisar total, exportar/restaurar. Debug separado evita que las pruebas inventen trabajos en el coche real. Las acciones normales de instalación/permisos son del propietario.

## Continuidad

Progreso en archivos y commits locales cuando sea posible. Reanudar leyendo lo realizado sin reiniciar seed ni borrar datos. Si una dependencia externa/cuota/hardware bloquea, registrar estado y siguiente acción reproducible. No simular éxito ni abandonar lo independiente.
