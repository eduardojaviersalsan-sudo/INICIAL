import json
import os
import uuid
from datetime import datetime

from flask import Flask, flash, redirect, render_template, request, url_for
from werkzeug.utils import secure_filename

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "static", "uploads")
METADATA_FILE = os.path.join(UPLOAD_FOLDER, "metadata.json")

# Extensión -> categoría del sitio (stickers de WhatsApp vs. contenido de TikTok)
CATEGORIES = {
    "webp": {"slug": "stickers", "label": "Stickers WhatsApp"},
    "gif": {"slug": "tiktok", "label": "GIFs TikTok"},
    "png": {"slug": "tiktok", "label": "PNG TikTok"},
}

# Firmas binarias reales de cada formato, para no confiar solo en la extensión.
FILE_SIGNATURES = {
    "png": [b"\x89PNG\r\n\x1a\n"],
    "gif": [b"GIF87a", b"GIF89a"],
    "webp": [b"RIFF"],  # el bloque "WEBP" se valida aparte (bytes 8-12)
}

MAX_CONTENT_LENGTH = 15 * 1024 * 1024  # 15 MB por petición

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "clave-de-desarrollo-cambiar-en-produccion")
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH

os.makedirs(UPLOAD_FOLDER, exist_ok=True)


def allowed_extension(filename):
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    return ext if ext in CATEGORIES else None


def is_valid_signature(file_storage, ext):
    """Revisa los primeros bytes del archivo para confirmar que el contenido
    realmente corresponde al formato declarado por la extensión."""
    head = file_storage.stream.read(16)
    file_storage.stream.seek(0)

    signatures = FILE_SIGNATURES.get(ext, [])
    if not any(head.startswith(sig) for sig in signatures):
        return False

    if ext == "webp":
        return head[8:12] == b"WEBP"

    return True


def load_metadata():
    if not os.path.exists(METADATA_FILE):
        return []
    try:
        with open(METADATA_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return []


def save_metadata(items):
    with open(METADATA_FILE, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=2)


@app.route("/")
def inicio():
    categoria = request.args.get("categoria", "todos")
    imagenes = load_metadata()

    if categoria != "todos":
        imagenes = [img for img in imagenes if img["categoria_slug"] == categoria]

    imagenes = sorted(imagenes, key=lambda x: x["subido_en"], reverse=True)

    return render_template(
        "index.html",
        imagenes=imagenes,
        categoria_activa=categoria,
        total=len(load_metadata()),
    )


@app.route("/subir", methods=["POST"])
def subir():
    archivos = request.files.getlist("imagenes")
    archivos = [f for f in archivos if f and f.filename]

    if not archivos:
        flash("No se seleccionó ningún archivo.", "error")
        return redirect(url_for("inicio"))

    metadata = load_metadata()
    subidos, rechazados = 0, []

    for archivo in archivos:
        nombre_original = secure_filename(archivo.filename)
        ext = allowed_extension(nombre_original)

        if not ext:
            rechazados.append(f"{archivo.filename} (formato no permitido)")
            continue

        if not is_valid_signature(archivo, ext):
            rechazados.append(f"{archivo.filename} (el contenido no coincide con .{ext})")
            continue

        nombre_guardado = f"{uuid.uuid4().hex}.{ext}"
        archivo.save(os.path.join(UPLOAD_FOLDER, nombre_guardado))

        metadata.append(
            {
                "id": uuid.uuid4().hex,
                "archivo": nombre_guardado,
                "nombre_original": nombre_original,
                "extension": ext,
                "categoria_slug": CATEGORIES[ext]["slug"],
                "categoria_label": CATEGORIES[ext]["label"],
                "subido_en": datetime.utcnow().isoformat(),
            }
        )
        subidos += 1

    if subidos:
        save_metadata(metadata)
        flash(f"¡Listo! Se subieron {subidos} imagen(es) correctamente.", "success")

    if rechazados:
        flash(
            "No se pudieron subir: " + ", ".join(rechazados)
            + ". Solo se aceptan .webp, .gif y .png.",
            "error",
        )

    return redirect(url_for("inicio"))


@app.errorhandler(413)
def archivo_muy_grande(_error):
    flash("El archivo (o el conjunto de archivos) supera el límite de 15 MB.", "error")
    return redirect(url_for("inicio"))


if __name__ == "__main__":
    app.run(debug=True)
