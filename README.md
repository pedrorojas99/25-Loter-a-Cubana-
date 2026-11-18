# 25 Lotería Cubana

Aplicación Android nativa desarrollada con Kotlin y Jetpack Compose.

## Alcance funcional
- Resultados de Florida Lottery: Pick 2 y Pick 3, turnos Midday y Evening.
- Guardar cada resultado como texto para preservar ceros iniciales.
- Historial de sorteos, estadísticas descriptivas y Charada Cubana del 1 al 100.
- Separación de la aplicación pública y la aplicación privada de administración.

## Estado
- [x] Estructura Android inicial.
- [x] Pantalla principal con estilo claro y colores suaves.
- [x] Sección inicial de billetera para usuarios con opciones de depósito y retiro (interfaz solamente).
- [ ] Integración verificada con fuente oficial de resultados.
- [ ] Integración de pagos, verificación de identidad, controles antifraude y cumplimiento legal; no activar movimientos reales hasta completar estas verificaciones.
- [ ] Persistencia local y sincronización automática.
- [ ] Historial, métricas y Charada.
- [ ] Aplicación de administración y control de acceso.
- [ ] Compilación y pruebas de APK.

## Construcción
Requiere Android SDK, JDK 17 y Android Gradle Plugin 8.7.3.

Este repositorio es independiente de NBA Analytics. No reutiliza ni modifica su código o datos.
