# TikTok Sticker Saver (Android)

App de Android para guardar los stickers que aparecen en los comentarios de
videos de TikTok, directamente en tu galería (`Pictures/TikTokStickers`).

## Cómo funciona

TikTok **no ofrece una API pública** para leer comentarios ni stickers, y
usar sus endpoints internos (no documentados, firmados, y que cambian con
cada actualización) sería frágil y además roza sus Términos de Servicio.

Por eso esta app usa un enfoque distinto, mucho más robusto y respetuoso:

1. La pantalla principal es un **navegador embebido** (un `WebView`) que
   carga la página de TikTok que tú le indiques (pega el enlace de un video,
   o comparte el video desde la propia app de TikTok con "Compartir → TikTok
   Sticker Saver").
2. Dentro de esa página abres normalmente la sección de comentarios, como lo
   harías en el navegador o en la app.
3. **Mantén presionada** cualquier imagen (sticker) de un comentario para
   guardarla — funciona igual que "guardar imagen" en Chrome, usando
   `WebView.getHitTestResult()`. Este es el mecanismo principal y siempre
   funciona, sin importar cómo cambie el diseño de TikTok.
4. Como ayuda extra, la app inyecta un script que intenta **detectar
   automáticamente** las imágenes pequeñas dentro del área de comentarios
   (excluyendo avatares) y las muestra en una franja inferior para
   guardarlas con un toque, o todas a la vez con "Guardar todos". Esta
   detección es **heurística y best-effort**: si TikTok cambia su
   maquetación puede dejar de encontrar stickers; en ese caso usa siempre la
   opción de mantener presionado.

La app **no inicia sesión en TikTok en tu nombre, no usa tus credenciales ni
llama a APIs privadas**: solo guarda imágenes que TikTok ya cargó para
mostrártelas en pantalla, algo equivalente a lo que cualquier navegador
permite hacer. Aun así, el contenido descargado sigue perteneciendo a sus
creadores originales — úsalo para tu consumo personal y respeta los
derechos de autor.

## Estructura del proyecto

```
android-app/
├── app/
│   └── src/main/
│       ├── java/com/eduardosalsan/tiktokstickersaver/
│       │   ├── ui/       MainActivity (navegador), GalleryActivity, adapters
│       │   ├── web/      Script de detección de stickers + puente JS↔Kotlin
│       │   └── data/     Descarga (MediaStore) y lectura de la galería
│       └── res/          Layouts, strings (es), iconos, tema oscuro estilo TikTok
├── build.gradle.kts / settings.gradle.kts
└── gradlew / gradlew.bat
```

- Kotlin + Views (ViewBinding), sin Compose.
- `minSdk 24`, `targetSdk`/`compileSdk 34`.
- Descarga con OkHttp; guardado con `MediaStore` (Android 10+, sin permisos
  extra) o escritura directa + `MediaScannerConnection` en versiones
  anteriores (requiere `WRITE_EXTERNAL_STORAGE`, ya declarado con
  `maxSdkVersion=28`).
- Miniaturas e imágenes de la galería con Coil.

## Cómo compilarla

Este proyecto se generó en un entorno remoto sin Android SDK instalado, así
que no se generó un `.apk` — necesitas abrirlo en **Android Studio**
(Hedgehog/2023.1 o más reciente):

1. Abre la carpeta `android-app/` como proyecto en Android Studio.
2. Deja que sincronice Gradle (descargará el Android SDK/plugin la primera
   vez si no lo tienes).
3. Conecta un dispositivo o usa un emulador y pulsa **Run ▶**.

También puedes compilar por línea de comandos si ya tienes el Android SDK
instalado y `ANDROID_HOME` configurado:

```bash
cd android-app
./gradlew assembleDebug
# APK generado en app/build/outputs/apk/debug/app-debug.apk
```

## Limitaciones conocidas

- La detección automática de stickers es heurística (basada en tamaño de
  imagen y en atributos `data-e2e`/clases que contengan "comment"); TikTok
  puede cambiar su HTML en cualquier momento y romperla. El guardado manual
  (mantener presionado) no depende de esto y siempre debería funcionar.
- Algunas imágenes de TikTok exigen encabezados `Referer`/`User-Agent`
  específicos para descargarse fuera del navegador; la app ya envía un
  `User-Agent` de Chrome móvil y el `Referer` de la página actual, pero si
  el CDN de TikTok cambia sus reglas alguna descarga podría fallar (verás un
  Toast con el error).
- No hay inicio de sesión: si un video requiere estar logueado para ver
  comentarios, TikTok te lo pedirá dentro del propio WebView, igual que en
  un navegador.
