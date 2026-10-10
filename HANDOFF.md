# Ejecución y entrega de la corrección

## Entrega ejecutada — 11/10/2026

APKs definitivos 3.0.0/code 3 en `dist/`: release firmado con el mismo certificado v2, identidad `dev.p38journal`; debug independiente `dev.p38journal.debug`. El release se instaló sobre v2 y arrancó realmente en el emulador sin desinstalar. Los datos poblados se verificaron por actualización debug y restauración repetida con fixtures identificados, conservando lectura, trabajos, gastos, PDF y marcador.

Dominio, catálogo, interfaz y revisión independiente completados con agentes. Referencia 175.000 separada de lecturas y trabajos; sin INITIAL automático; límites recalculados por trabajo real; seis grupos iniciales; lectura manual, avisos inmediatos, registro parcial y gastos simples. Todas las funciones secundarias existentes siguen accesibles.

32 unitarias y 48 Android aprobadas, lint sin errores; cinco comprobaciones visibles offline/proceso/reinicio/reapertura PASS. Fuente, guías y evidencia real en [PROGRESS](docs/PROGRESS.md), [TEST_REPORT](docs/TEST_REPORT.md), [USER_TEST](docs/USER_TEST.md) y [INDEPENDENT_REVIEW](docs/INDEPENDENT_REVIEW.md). Solo se probó Android 16 en emulador; límites Android/OEM/SAF y pruebas físicas pendientes descritos en el informe. La clave privada permanece fuera de la entrega.

## Encargo de ejecución conservado

1. Inspeccionar app, migraciones y catálogo actuales. Respaldo, plan y contratos.
2. Corregir dominio/base y retirar generador inicial; migrar sin borrar registros. En paralelo agentes trabajan UI, catálogo/avisos y revisión.
3. Integrar recorrido lectura manual → cálculo → aviso → registro realizado → nuevo límite; completar gastos e interfaz simplificada.
4. Revisar cobertura del fabricante y pruebas v3; resolver fallos. Validar actualización con identidad/firma actuales.
5. Entregar APK actualizado real, debug, código, docs/PROGRESS.md, docs/TEST_REPORT.md y docs/USER_TEST.md, con 5–8 pasos.

Probar: actualizar lectura, consultar intervalo/fuente y faltante, cruzar límite en debug, recibir/tocar aviso, registrar trabajo, comprobar próximo límite, añadir gasto y total, exportar/restaurar en prueba. Separar fixture de instalación real.

Informe final: artefactos/compatibilidad, comportamiento corregido, pruebas efectivas y límites concretos. No pedir al propietario compilar como entrega normal. Mantener progreso/commits para reanudar y no repetir seed. Esta documentación describe cambios solicitados; el responsable de compilar es Codex en el workspace que contiene la app.
