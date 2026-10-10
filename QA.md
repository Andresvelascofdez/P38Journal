# Pruebas y definición de terminado — Android

## Motor y catálogo

1. Fixture: regla 12 meses/20.000 km, trabajo el 15/10/2026 a 176.000 km. Vence el 15/10/2027 o al alcanzar 196.000 km. No insertar ese trabajo ficticio en la app real.
2. Tiempo o km pueden vencer solos; ambas causas no duplican tarea/aviso. Sin lectura actual no inventar kilómetros; tiempo sigue evaluándose.
3. Meses 28/29/30/31, año bisiesto, fin de mes, cambios de horario Asia/Nicosia y cambio de zona. Reloj controlable.
4. Sin referencia crear acción inicial; compra/seed no son ejecución. Fechas/km aproximados no producen vencimientos falsamente exactos. Históricos previos conservados.
5. Comprobar aceite no satisface cambio; reparación de una pieza no cierra revisión completa. Programar cita no completa nada.
6. Evento antiguo introducido después no desplaza el posterior. Editar/anular recupera referencia anterior válida y cancela avisos obsoletos.
7. Corrección de odómetro explícita, lecturas retroactivas y precisión parcial. Overrides preservados tras actualización de catálogo.
8. Gasolina/automático, VIN/equipamiento condicionales y fases/hitos según fuentes. No aplicar reglas diésel/manual.
9. Umbrales proporcionales para controles frecuentes, sin preaviso permanente.

Revisor independiente comprueba matriz visual, glosario y todas las filas aplicables de M01, más controles de los manuales. Informe incluidos/excluidos/condicionales con motivo, edición y páginas. Verificar primera revisión, alternancia, hitos y notas. No cerrar con solo el starter. Fuentes bloqueadas no se califican como verificadas ni se reemplazan por intervalos inventados.

## Datos, gastos y copias

- Instalación limpia: migración, seed una vez y datos reales sin fixtures. Reiniciar/reabrir conserva registros y adjuntos. Migraciones desde esquema previo sin borrado.
- Visita con varias líneas atómica; fallo intermedio/doble toque no duplica ni deja estado parcial.
- Gastos fixture Taller 100 €, Extras 25 €, Gasolina 60 € = 185 €. Editar gasolina a 55 € = 180 €; anular extras = 155 €. No requieren factura, km ni mantenimiento; total persiste tras reinicio/restauración.
- Una factura pagada de 300 € con dos líneas 100/200 suma 300 €, no 600 €. Vincular un gasto existente evita contar el mismo pago dos veces; pagos diferentes con igual fecha/importe no se fusionan solos.
- Añadir gasolina no completa tareas, cambia odómetro ni reinicia intervalos. Sin litros/consumo; lista y total, sin informes/gráficos. Importe exacto 12,50/12.50, cero explícito, validación y doble toque.
- Exportar todo a archivo externo, restaurar en app vacía y comparar registros/adjuntos por checksum. Reimportación no duplica. Vista previa, validación de versiones/espacio, protección contra rutas maliciosas y rollback coherente DB/archivos.
- Desinstalar/instalar/restaurar en entorno de prueba demuestra respaldo externo; nunca realizar esa prueba destructiva en la instalación personal. Copias internas no se anuncian como protección ante desinstalar/perder móvil.
- Revocar permiso de origen no rompe documentos ya importados. Permiso externo revocado se refleja y no anuncia copia automática exitosa. CSV opcional seguro frente a fórmulas inyectadas.

## Avisos Android

- Pruebas WorkManager/Room reales con reloj/transportes controlados y pruebas separadas de emisión visible con NotificationManager.
- Evaluaciones concurrentes/reintentos generan una entrada por ventana; reinicio recupera estado sin multiplicar notificaciones.
- Completar tarea durante evaluación invalida generación anterior; ids estables y cancelación correcta. Restaurar/reinstalar reconstruye planificación.
- Activación concedida/denegada, API antes y después de 33, canal deshabilitado y ajuste de permiso desde sistema. Bandeja disponible siempre.
- Notificación de prueba visible abre destino correcto, sin alterar trabajos. Deep links y Back correctos.
- Cierre normal, quitar de recientes/muerte de proceso, reinicio y modo sin Internet. Android puede diferir: comprobar ejecución real dentro de condiciones de prueba, sin exigir minuto exacto.
- Forzar detención desde Ajustes tiene límite documentado; reabrir recupera trabajo. No afirmar entrega garantizada con detención forzada ni confundir emitted con leído.
- Primer uso limitado y resúmenes agrupados; no ráfagas por 20 tareas ni por tiempo+km.

## Interfaz y recorrido

Compose tests/instrumentación y revisión visual: 360/390/430 dp, mínimo 320 dp, fuentes ampliadas, tema claro/oscuro. Insets, teclado, Back, rotación y muerte de proceso sin pérdida de datos confirmados ni controles solapados. Accesibilidad TalkBack/semantics y objetivos táctiles adecuados.

Inicio, filtros, registro/edición de visitas, odómetro, averías, piezas, gastos, manual PDF/búsqueda, adjuntos, bandeja, ajustes y exportación/restauración funcionan de principio a fin. PDF escaneado muestra búsqueda no disponible. No aparecen «Por aclarar» ni «Punto de partida» como bloques de producto. Sin VIN/matrícula/costes ficticios.

## Build, actualización y entrega

Ejecutar Gradle unit tests, lint, compilación debug y release firmado. Pruebas instrumentadas en emulador/dispositivo cuando estén disponibles, indicando API y entorno. Verificar firma, applicationId, versionCode y checksum. Actualizar APK previo con misma firma sin desinstalar, conserva historial/gastos/adjuntos y aplica migración. APK debug tiene datos separados.

Entregar APKs reales en dist/, código completo, catálogo revisado, scripts reproducibles y docs/TEST_REPORT.md con comandos, resultados reales y límites. docs/USER_TEST.md con 5–8 pasos para el propietario. Un build exitoso no demuestra recepción física de avisos. Si no hay dispositivo/emulador/SDK disponible, registrar qué pruebas faltan y su motivo; no llamar completa la parte sin comprobar.

No terminado si hay botones principales vacíos, datos solo en memoria, migraciones destructivas, reglas inventadas, solo 11 reglas starter o APK sin generar. El paquete de especificación no debe confundirse con el producto construido.
