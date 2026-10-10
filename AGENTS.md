# Instrucciones para todos los agentes — Android v2

## Encargo autorizado

Implementar y entregar una app Android nativa con APK instalable. Leer PROMPT_CODEX.md, SPEC.md, ANDROID.md, ENGINE.md, QA.md, HANDOFF.md, SOURCES.md y data/. Proceder con decisiones reversibles sin pedir permiso reiterado. Si existen instrucciones del entorno, integrarlas sin sobrescribirlas a ciegas. No reconstruir la antigua PWA.

## Reglas de producto

- P38 4.6 V8 gasolina, automático, 2000; 175.000 km son de compra.
- Kotlin/Compose/Room/WorkManager; datos locales, sin backend necesario.
- Español, tareas precargadas y avisos según fuentes verificadas, con procedencia.
- Inicio/Plan sin «Por aclarar» ni «Punto de partida»; acciones desde ahora.
- Seed no completa trabajos ni reinicia intervalos. Históricos sin precisión no son referencias exactas.
- Gastos Taller/Extras/Gasolina/Otros: lista y total, sin informes/consumo ni duplicación de taller.
- Datos reales separados de fixtures; conservar datos al actualizar/migrar.
- Notificaciones locales con estado honesto de permisos y pruebas; el SO controla el trabajo diferible.
- No contratar, publicar, enviar mensajes ni alterar configuraciones globales del usuario.

## Delegación solicitada

El coordinador usa subagentes disponibles y reparte escrituras por áreas:

1. Documentación: fuentes, catálogo completo, cobertura y condiciones. Dueño de catálogo y documentación de mantenimiento.
2. Dominio/persistencia: motor puro Kotlin, repositorios Room, migraciones y contratos.
3. Interfaz: pantallas Compose, formularios, navegación y accesibilidad.
4. Operación Android: WorkManager, canales/permisos, archivos/copias, SDK/Gradle/firma y artefactos.
5. Revisión independiente: catálogo y flujos completos; devuelve hallazgos concretos y pruebas.

Definir contratos antes de dividir trabajo y coordinar archivos compartidos. Ningún agente modifica fuera de su ámbito sin acuerdo. Todos reportan archivos, decisiones, evidencias y bloqueos. El coordinador integra, espera la revisión y corrige. Si no existe herramienta de subagentes, informar y continuar secuencialmente; no fingir agentes ni imponer modelos.

## Trabajo verificable

Crear plan y docs/PROGRESS.md. Consultar versiones y documentación oficiales. Reloj y transporte de avisos inyectables. Pruebas significativas de cálculo, transacciones, migraciones, respaldo, notificaciones y UI. No usar migraciones destructivas, datos solo en memoria ni mocks de producción. No afirmar prueba física si solo hubo emulador. Crear código/artefactos ejecutables, no cerrar con documentación únicamente. Guardar continuidad y commits locales cuando sea posible.
