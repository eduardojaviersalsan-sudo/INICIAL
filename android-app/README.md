# TikTok Sticker Saver (Android)

App de Android para capturar los stickers que dejan otros usuarios en los
comentarios de TikTok **dentro de la propia app de TikTok**, y agregarlos
como un paquete de stickers de WhatsApp que puedes enviar en cualquier chat.

## Dos cosas importantes antes de empezar

**1. No existe un "copiar/pegar" literal hacia WhatsApp.** WhatsApp no
acepta pegar una imagen cualquiera del portapapeles como sticker. La única
vía oficial (documentada por Meta) es que una app registre un **paquete de
stickers** y se lo ofrezca a WhatsApp con su diálogo nativo "Agregar
paquete de stickers"; una vez aceptado, el paquete queda disponible en el
teclado de stickers de WhatsApp para cualquier chat. Es lo que hace esta
app, y es el equivalente funcional más cercano a "copiar y pegar" que
WhatsApp permite.

**2. Para "ver" contenido dentro de la app real de TikTok**, Android exige
un **Servicio de Accesibilidad** (permiso que activas una vez, a mano, en
Ajustes) combinado con una **captura de pantalla** recortada a la zona del
sticker — no hay otra forma de que una app lea el contenido de otra. Esto
implica:
- Los stickers capturados son un recorte de pantalla, no el archivo
  original de TikTok: no tendrán transparencia perfecta si el fondo detrás
  del sticker no es liso.
- Por las políticas de Google, este tipo de apps normalmente **no se puede
  publicar en Play Store**. Está pensada para instalar manualmente
  (sideload) en tu propio teléfono.

## Cómo funciona (flujo de uso)

1. Abres esta app y activas el **Servicio de Accesibilidad** una sola vez
   (botón en la pantalla principal → te lleva a Ajustes de Android).
2. Abres TikTok normalmente, ves un video y entras a sus comentarios.
3. Aparece un **botón flotante** (rosado) en el borde de la pantalla — solo
   se muestra mientras TikTok está abierto. Lo tocas.
4. La app toma una captura de pantalla y analiza qué zonas *parecen*
   stickers (por tamaño y forma). Te muestra esos recortes en una fila para
   que elijas cuál guardar.
5. Tocas el sticker que quieres → se convierte al formato que exige
   WhatsApp y se agrega a tu paquete (guardado localmente en la app, nada
   sale de tu teléfono).
6. Entras a "Ver mis stickers" dentro de la app, revisas el paquete (puedes
   borrar alguno o renombrarlo) y tocas **"Agregar a WhatsApp"** (necesitas
   mínimo 3 stickers). WhatsApp te pregunta si quieres agregarlo — aceptas.
7. Desde ahí, en **cualquier chat de WhatsApp**, abres el teclado de
   stickers y tu paquete ya está disponible para enviar.

## Arquitectura (todo en Kotlin, sin librerías de red)

```
android-app/app/src/main/java/com/eduardosalsan/tiktokstickersaver/
├── accessibility/
│   ├── TikTokAccessibilityService.kt   Detecta TikTok en primer plano,
│   │                                   dibuja el botón flotante y el
│   │                                   selector de stickers (overlays de
│   │                                   accesibilidad, sin permisos extra).
│   └── StickerNodeScanner.kt           Heurística: qué nodos del árbol de
│                                       accesibilidad parecen un sticker
│                                       (tamaño/forma). AJUSTA AQUÍ si la
│                                       detección falla.
├── capture/
│   └── ScreenshotCapture.kt            Envoltorio de
│                                       AccessibilityService.takeScreenshot()
│                                       (API 30+) como función suspend.
├── sticker/
│   ├── StickerPack.kt                  Modelos de datos del paquete.
│   └── StickerPackManager.kt           Recorta/rellena a 512x512, comprime
│                                       a WEBP <100KB, genera el ícono de
│                                       bandeja y guarda todo en
│                                       filesDir/stickerpacks/. AJUSTA AQUÍ
│                                       los tamaños/límites.
├── whatsapp/
│   └── WhatsAppIntegration.kt          Intent "ENABLE_STICKER_PACK" para
│                                       pedirle a WhatsApp que agregue el
│                                       paquete.
├── provider/
│   └── StickerContentProvider.kt       ContentProvider que WhatsApp
│                                       consulta para leer el paquete
│                                       (contrato oficial "Third Party
│                                       Sticker Apps").
└── ui/
    ├── MainActivity.kt                 Estado del permiso + accesos.
    ├── StickerPackActivity.kt          Galería del paquete capturado.
    └── StickerGridAdapter.kt
```

- Kotlin + Views (ViewBinding), sin Compose y sin dependencias de red: todo
  el procesamiento ocurre en el propio teléfono.
- `minSdk 30` (Android 11): es la versión mínima que tiene
  `AccessibilityService.takeScreenshot()`, la API en la que se apoya toda
  la captura. En versiones anteriores no existe forma equivalente sin pedir
  el permiso de captura de pantalla (`MediaProjection`) una y otra vez.
- Sin permiso de Internet, sin `WRITE_EXTERNAL_STORAGE`: todo se guarda en
  el almacenamiento privado de la app (`filesDir/stickerpacks/`).

## Cómo compilarla

Este proyecto se generó en un entorno remoto sin Android SDK instalado
(no hay forma de descargarlo aquí), así que no se generó un `.apk` — el
código no se compiló de punta a punta. Ábrelo en **Android Studio**
(Hedgehog/2023.1 o más reciente):

1. Abre la carpeta `android-app/` como proyecto en Android Studio.
2. Deja que sincronice Gradle (descargará el Android SDK/plugin si no lo
   tienes).
3. Conecta un dispositivo con Android 11+ (o un emulador) y pulsa **Run ▶**.
4. En el teléfono: abre la app → "Activar Servicio de Accesibilidad" → en
   la lista de Ajustes, busca "TikTok Sticker Saver" (aparece como
   "Captura de stickers de TikTok") y actívalo.

También por línea de comandos, si ya tienes `ANDROID_HOME` configurado:

```bash
cd android-app
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Limitaciones conocidas / próximos pasos sugeridos

- **Detección heurística**: `StickerNodeScanner.kt` decide qué es un
  sticker por tamaño (40–150dp) y forma (casi cuadrado). Si en tu versión
  de TikTok detecta de más, de menos, o confunde avatares con stickers,
  ese es el archivo a ajustar — están comentados los umbrales.
- **Sin transparencia real**: al ser un recorte de pantalla, el sticker
  final lleva el fondo que tuviera detrás en TikTok (normalmente el fondo
  de la lista de comentarios). Si quieres mejorar esto, un punto de partida
  sería añadir una eliminación de fondo simple en
  `StickerPackManager.resizeAndPad()` cuando el fondo es de un color casi
  uniforme.
- **Nombre de paquete de TikTok**: `accessibility_service_config.xml` fija
  los paquetes conocidos de TikTok (`com.zhiliaoapp.musically`,
  `com.ss.android.ugc.trill`, `com.zhiliaoapp.musically.go`). Si tu TikTok
  usa otro (algunos mercados/variantes lo cambian), agrégalo ahí.
- **Un solo paquete de stickers**: `StickerPackManager` maneja un único
  paquete de hasta 30 stickers (el máximo que permite WhatsApp). Para
  soportar varios paquetes, `DEFAULT_PACK_ID` es el punto de partida a
  generalizar.
- **Contrato de WhatsApp reconstruido de memoria**: `StickerContentProvider.kt`
  y `WhatsAppIntegration.kt` implementan el contrato público que Meta
  documenta para apps de stickers de terceros (el mismo que sigue su
  repositorio de ejemplo "WhatsApp/stickers"). Si el botón "Agregar a
  WhatsApp" no funciona, lo primero a revisar es que los nombres de acción/
  columnas ahí sigan vigentes.
