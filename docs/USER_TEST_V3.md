# Instalar y probar P38 Journal 3.0

Android 8.0 o posterior. No necesita cuenta, conexión ni servidor. Release actualiza P38 Journal; debug aparece como **P38 Journal · Prueba**, con datos separados.

1. Instala [el APK release](../dist/p38-journal-3.0.0-release.apk) **encima del anterior, sin desinstalar**. Si Android lo pide, permite instalar desde la aplicación que abre el APK. Firma y applicationId conservados.
2. Abre Inicio: comprueba tu lectura existente. Si nunca registraste una, aparece **175.000 km · Valor inicial**. El plan usa esa referencia sin registrar ningún trabajo. En **Más opciones → Ajustes** puedes revisar la fecha inicial del plan: es editable y no representa una compra ni un mantenimiento.
3. En **Mantenimiento**, abre una revisión: verás cuándo toca y cuánto falta. **Qué incluye** permite seleccionar operaciones; **Ver fuente del fabricante** abre la información secundaria. Sin trabajo posterior, aceite/filtro vence a **195.000 km o 12 meses desde la fecha del plan**, lo que ocurra antes.
4. En **Más opciones → Ajustes**, pulsa **Activar avisos**, concede el permiso Android y pulsa **Probar aviso**. Tócalo para abrir la bandeja. Guardar kilómetros evalúa inmediatamente los límites; Android programa las comprobaciones por fecha en segundo plano.
5. Para probar sin alterar datos reales, instala también [el APK debug](../dist/p38-journal-3.0.0-debug.apk). En esa app, guarda **194.999** y luego **195.000 km** desde **Actualizar kilómetros**. Debe aparecer un aviso de mantenimiento pendiente; al tocarlo se abre su revisión. Guardar el mismo valor o reabrir no repite el mismo aviso. Puede haber un aviso previo de proximidad, distinto del vencimiento.
6. En debug, abre **Qué incluye → Desmarcar todas**, selecciona **Cambio de aceite y filtro** y pulsa **Registrar realizado**. Introduce **195.200 km**, guarda y confirma que se ha realizado. Esa operación pasa a **215.200 km**; las demás conservan sus límites. Un registro pasado no reduce el odómetro; actualizarlo desde el trabajo exige confirmación explícita.
7. En **Gastos**, añade Taller **100**, Extras **25** y Gasolina **60**: total **185 €**. Cambia gasolina a **55**: **180 €**. Anula Extras: **155 €**. Los pagos vinculados a un trabajo se cuentan una vez.
8. En **Más opciones → Copias**, exporta un ZIP completo a un destino fuera del teléfono. No desinstales release para probar restauraciones; usa debug. La copia incluye registros, gastos, PDF/fotos, marcadores y ajustes.

Las verificaciones reales se hicieron en Android 16/API 36; no se probó un teléfono físico. Forzar detención desde Ajustes requiere volver a abrir la app; los avisos por fecha dependen de Android y de sus canales/permisos. [Resultados ejecutados](TEST_REPORT.md).
