# Instalar y probar4.1.0 / build5

1. Guarda una copia ZIP si tienes datos. Abre [release actualizado](../dist/p38-journal-4.1.0-release.apk) y acepta **Actualizar, sin desinstalar**. Comprueba «v4.1.0 · 5» en la cabecera y que siguen tus datos. Android8 o posterior; autoriza instalar desde la aplicación que abre el APK si Android lo pide.
2. Para pruebas ficticias usa [debug](../dist/p38-journal-4.1.0-debug.apk), «P38 Journal · Prueba», con datos separados. Con referencia175.000 y sin trabajos/personalizaciones, Mantenimiento abre directamente195.000→29,215.000→33,235.000→31. En cada tarjeta abre «Qué incluye»: contiene exactamente las operaciones contadas.
3. Las revisiones futuras suponen realizar las anteriores cuando toca. «Vencimientos reales y pendientes» muestra los límites operativos que alimentan avisos. Abrir una previsión no registra trabajos ni cambia intervalos.
4. En debug abre195.000, «Registrar realizado», selecciona solo aceite, pon195.200 y confirma fecha/km/trabajo. Aceite pasa a215.200, las otras28 continúan195.000. Las visitas empiezan sin operaciones marcadas. Consulta y edita trabajos desde Historial.
5. Consulta y edita operaciones, personaliza/restaura intervalos o crea mantenimientos propios recurrentes/únicos. Conserva los filtros de gastos por mes/año/tipo y comprueba subtotal y total acumulado.
6. Activa avisos y concede el permiso Android desde Más opciones→Ajustes. «Probar aviso» comprueba el canal. En debug sin trabajos guarda194.999 y después195.000: debe aparecer el aviso real y abrir su detalle. La app solo conoce los km introducidos a mano.

No necesitas compilar ni configurar servicios. Instalar release encima conserva firma y datos. [Pruebas y límites realmente comprobados](TEST_REPORT.md), [hashes](../dist/SHA256SUMS.txt). Los avisos por fecha pueden ser retrasados por Android; tras forzar detención hay que abrir la app de nuevo.
