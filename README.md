# StickerHub

Galería web para subir y compartir stickers de WhatsApp (`.webp`) y contenido
para TikTok (`.gif` / `.png`), tanto imágenes estáticas como animadas.

## Características

- Subida de imágenes `.webp`, `.gif` y `.png` (validando extensión y firma
  binaria real del archivo, no solo el nombre).
- Galería responsive (móvil, tablet y escritorio) con filtros por categoría.
- Diseño con colores vivos y tipografía llamativa, pensado para ser simple.
- Espacios publicitarios ya reservados en el layout (`.ad-slot`) para activar
  monetización más adelante.

## Ejecutar en local

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Luego abre `http://localhost:5000`.

## Cómo activar publicidad más adelante

En `templates/base.html` y `templates/index.html` hay varios `<div class="ad-slot" ...>`
marcados con comentarios `ESPACIO PUBLICITARIO`. Cuando tengas una cuenta
aprobada en una red de anuncios (Google AdSense, Ezoic, etc.):

1. Reemplaza el contenido de cada `ad-slot` por el snippet que te entregue la red.
2. Si usas AdSense, agrega su script de verificación en `templates/base.html`
   (dentro del `<head>`) y crea el archivo `ads.txt` que te indiquen en
   `static/ads.txt`.

## Notas de producción

- `MAX_CONTENT_LENGTH` limita las subidas a 15 MB por envío (ajustable en `app.py`).
- Cambia `SECRET_KEY` por una variable de entorno segura en producción.
- Los archivos subidos se guardan en `static/uploads/` y no se versionan en git.
