# Isla Dinámica (Android)

Isla dinámica tipo iPhone + bloqueo de WhatsApp con rostro / huella / PIN.

## Cómo obtener el APK

**Opción A – Android Studio**
1. Abre la carpeta `IslaDinamica` con Android Studio (Hedgehog o más nuevo) y espera la sincronización.
2. Menú *Build → Build Bundle(s) / APK(s) → Build APK(s)*.
3. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

**Opción B – GitHub Actions (sin instalar nada)**
1. Crea un repositorio en GitHub y sube todo el contenido de esta carpeta (incluida `.github`).
2. Entra en la pestaña *Actions* → *Build APK* → *Run workflow*.
3. Cuando termine, descarga `IslaDinamica-apk` desde *Artifacts*.

## Reinicio del teléfono
Android vuelve a encender solo los servicios de accesibilidad y de notificaciones que estaban activados,
así que la isla aparece sola después de desbloquear el teléfono por primera vez tras reiniciar.
En algunas marcas (Xiaomi, Huawei, Samsung...) hay que permitir además el "inicio automático" y quitar la
optimización de batería de la app (hay un botón dentro de la app para esto).

Opcional, si tu teléfono apaga el servicio de accesibilidad al reiniciar: conecta el teléfono a una
computadora con la depuración USB activada y ejecuta una sola vez

    adb shell pm grant com.isladinamica.app android.permission.WRITE_SECURE_SETTINGS

Con eso la app reactiva sola el servicio en cada reinicio.

## Primer uso
1. Instala el APK en el teléfono.
2. Abre la app y pulsa **Activar servicio de accesibilidad** y **Dar acceso a las notificaciones**.
3. Si el interruptor sale en gris (Android 13+): Ajustes → Apps → Isla Dinámica → ⋮ → *Permitir configuración restringida*.
4. Verifica que el teléfono tenga rostro, huella o PIN configurado.
