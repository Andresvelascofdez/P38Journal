# Pruebas de aceptación v3

## Calendario

- Base operativa 175.000, cero trabajos: el plan funciona y no hay mantenimiento inicial por falta de historial. No se crea servicio realizado ni una fecha ficticia de compra.
- Fixture regla 20.000 km: próximo 195.000, no 180.000/200.000 por redondeo. A 184.500 faltan 10.500; a 194.999 aún no vence; a 195.000 vence; a 197.000 permanece pendiente.
- Trabajo válido a 195.200 → siguiente 215.200 para esa recurrencia. Otra operación y simple comprobación no reinician el cambio. Registrar gasto no altera reglas.
- Editar/anular trabajo recomputa referencia correcta. Históricos previos a la base no la desplazan; lectura actual no mueve baseline.
- Plazo de 12 meses con fecha operativa fixture 15/10/2026 → 15/10/2027, salvo ejecución posterior válida. Tiempo o km pueden vencer por separado; ambas causas una tarea/aviso. Fin de mes/bisiesto/zona horaria correctos.
- Hitos únicos, fases B/C y reglas de antigüedad/condición revisados; no aplicar suma genérica a todos. Catálogo completo, fabricante gasolina/automático y condiciones por versión.
- Controles de taller agrupados en su servicio; diarios/semanales sin tarjetas ni alertas por defecto.

## Interfaz

Inicio corto con lectura, próximo límite y faltante. Cuando nada toca muestra «Todo al día». No tarjetas «Mantenimiento inicial»/«no consta una referencia precisa». Detalle: título, intervalo, próximo límite, faltante, último registro y botón. Fuentes en despliegue secundario; ningún id/código inglés/razonamiento largo en bloques principales. Navegación Inicio/Mantenimiento/Historial/Gastos y fuentes accesibles. 320–430 dp, fuentes ampliadas, TalkBack, tema oscuro, teclado/Back y rotación correctos.

Registrar realizado desde tarea en pocos toques con operación/km precargados. Revisión parcial solo cierra operaciones seleccionadas. Fecha pasada no reduce lectura actual. Formularios secundarios opcionales.

## Notificaciones

- Guardar lectura 194.999 → 195.000 produce aviso Android real y bandeja, no solo cambio de estado en pantalla. Verificar en emulador/dispositivo, separado de test de transporte simulado.
- Guardar otra vez mismo km, reabrir y reevaluar worker no repiten vencimiento. Lectura saltando varios límites no pierde tareas; resumen coherente.
- Completar trabajo cancela aviso anterior; nuevo ciclo genera aviso. Reintento/muerte de proceso recupera sin multiplicar ids.
- Tiempo se evalúa con interfaz cerrada mediante WorkManager; sin Internet y tras reinicio. Permiso denegado/canal deshabilitado muestran estado fiel y bandeja disponible.
- Botón «Probar aviso» visible sin alterar mantenimiento; tocar notificación abre detalle/grupo correcto. Documentar límites del SO/forzar detención, sin prometer minuto exacto.

## Gastos y datos

Fixtures: Taller 100 + Extras 25 + Gasolina 60 = 185 €. Editar gasolina a 55 → 180; anular extras → 155. Sin factura/km/relación obligatoria. Un pago de taller 300 con dos líneas no suma 600; pagos distintos no se fusionan por coincidencia. Dinero exacto y doble toque sin duplicación. Reinicio/restauración conserva total; sin gráficos/informes/consumo.

Migración desde app actual con lectura 182.000 conserva 182.000, base175.000, historial/gastos/adjuntos y tareas reales, pero retira pendientes iniciales derivados. Seed/migración reejecutados no duplican ni recrean iniciales. Actualizar APK sin desinstalar con firma/identidad actuales; DB sin borrado. Exportación/restauración completa comprobada por checksums. Datos debug separados.

## Entrega

Build unit tests/lint/debug/release firmado y pruebas Android donde el entorno permita. Reportar comandos/resultados reales, entorno/API y bloqueos específicos. Entregar APK actualizado y 5–8 pasos de prueba. No afirmar app corregida si solo cambiaron etiquetas y el motor todavía genera revisión inicial o no emite aviso al guardar km.
