from datetime import date, datetime, timedelta

from flask import Flask, flash, redirect, render_template, request, url_for

from models import (
    ESTADOS_ACTIVO,
    FRECUENCIAS,
    PROXIMO_DIAS,
    TIPOS_MANTENIMIENTO,
    Activo,
    Mantenimiento,
    db,
)

app = Flask(__name__)
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///inventario.db"
app.config["SECRET_KEY"] = "cambia-esta-clave-en-produccion"

db.init_app(app)


def parse_fecha(valor):
    return datetime.strptime(valor, "%Y-%m-%d").date() if valor else None


def seed_si_vacio():
    if Activo.query.count() > 0:
        return
    hoy = date.today()
    ejemplos = [
        Activo(
            codigo="EQ-001",
            nombre="Laptop Dell Latitude",
            categoria="Equipo de cómputo",
            ubicacion="Oficina principal",
            responsable="Eduardo Salsán",
            estado="Operativo",
            fecha_adquisicion=hoy - timedelta(days=400),
            valor=850.0,
        ),
        Activo(
            codigo="VEH-001",
            nombre="Camioneta Toyota Hilux",
            categoria="Vehículo",
            ubicacion="Bodega central",
            responsable="Logística",
            estado="Operativo",
            fecha_adquisicion=hoy - timedelta(days=900),
            valor=28000.0,
        ),
        Activo(
            codigo="MAQ-001",
            nombre="Compresor de aire",
            categoria="Maquinaria",
            ubicacion="Taller",
            responsable="Mantenimiento",
            estado="En mantenimiento",
            fecha_adquisicion=hoy - timedelta(days=1200),
            valor=1200.0,
        ),
    ]
    db.session.add_all(ejemplos)
    db.session.flush()

    db.session.add_all(
        [
            Mantenimiento(
                activo_id=ejemplos[0].id,
                tipo="Preventivo",
                descripcion="Limpieza interna y actualización de software",
                frecuencia_dias=90,
                ultima_fecha=hoy - timedelta(days=85),
                proxima_fecha=hoy + timedelta(days=5),
                responsable="Soporte TI",
            ),
            Mantenimiento(
                activo_id=ejemplos[1].id,
                tipo="Preventivo",
                descripcion="Cambio de aceite y revisión de frenos",
                frecuencia_dias=180,
                ultima_fecha=hoy - timedelta(days=190),
                proxima_fecha=hoy - timedelta(days=10),
                responsable="Taller externo",
            ),
            Mantenimiento(
                activo_id=ejemplos[2].id,
                tipo="Inspección",
                descripcion="Revisión de presión y válvulas de seguridad",
                frecuencia_dias=30,
                ultima_fecha=hoy - timedelta(days=20),
                proxima_fecha=hoy + timedelta(days=10),
                responsable="Mantenimiento",
            ),
        ]
    )
    db.session.commit()


@app.route("/")
def dashboard():
    hoy = date.today()
    limite = hoy + timedelta(days=PROXIMO_DIAS)

    total_activos = Activo.query.count()
    activos_operativos = Activo.query.filter_by(estado="Operativo").count()

    mantenimientos = Mantenimiento.query.all()
    atrasados = sorted(
        [m for m in mantenimientos if m.proxima_fecha < hoy], key=lambda m: m.proxima_fecha
    )
    proximos = sorted(
        [m for m in mantenimientos if hoy <= m.proxima_fecha <= limite],
        key=lambda m: m.proxima_fecha,
    )

    return render_template(
        "dashboard.html",
        total_activos=total_activos,
        activos_operativos=activos_operativos,
        total_mantenimientos=len(mantenimientos),
        atrasados=atrasados,
        proximos=proximos,
    )


# ---------- Activos ----------


@app.route("/activos")
def listar_activos():
    query = Activo.query
    busqueda = request.args.get("q", "").strip()
    if busqueda:
        like = f"%{busqueda}%"
        query = query.filter(
            db.or_(Activo.nombre.ilike(like), Activo.codigo.ilike(like), Activo.categoria.ilike(like))
        )
    activos = query.order_by(Activo.nombre).all()
    return render_template("activos_list.html", activos=activos, busqueda=busqueda)


@app.route("/activos/nuevo", methods=["GET", "POST"])
def nuevo_activo():
    if request.method == "POST":
        activo = Activo(
            codigo=request.form["codigo"].strip(),
            nombre=request.form["nombre"].strip(),
            categoria=request.form.get("categoria", "").strip(),
            ubicacion=request.form.get("ubicacion", "").strip(),
            responsable=request.form.get("responsable", "").strip(),
            estado=request.form.get("estado", "Operativo"),
            fecha_adquisicion=parse_fecha(request.form.get("fecha_adquisicion")),
            valor=float(request.form["valor"]) if request.form.get("valor") else None,
            notas=request.form.get("notas", "").strip(),
        )
        db.session.add(activo)
        db.session.commit()
        flash(f"Activo '{activo.nombre}' creado correctamente.", "success")
        return redirect(url_for("listar_activos"))
    return render_template("activo_form.html", activo=None, estados=ESTADOS_ACTIVO)


@app.route("/activos/<int:activo_id>")
def detalle_activo(activo_id):
    activo = Activo.query.get_or_404(activo_id)
    return render_template("activo_detalle.html", activo=activo)


@app.route("/activos/<int:activo_id>/editar", methods=["GET", "POST"])
def editar_activo(activo_id):
    activo = Activo.query.get_or_404(activo_id)
    if request.method == "POST":
        activo.codigo = request.form["codigo"].strip()
        activo.nombre = request.form["nombre"].strip()
        activo.categoria = request.form.get("categoria", "").strip()
        activo.ubicacion = request.form.get("ubicacion", "").strip()
        activo.responsable = request.form.get("responsable", "").strip()
        activo.estado = request.form.get("estado", "Operativo")
        activo.fecha_adquisicion = parse_fecha(request.form.get("fecha_adquisicion"))
        activo.valor = float(request.form["valor"]) if request.form.get("valor") else None
        activo.notas = request.form.get("notas", "").strip()
        db.session.commit()
        flash(f"Activo '{activo.nombre}' actualizado.", "success")
        return redirect(url_for("detalle_activo", activo_id=activo.id))
    return render_template("activo_form.html", activo=activo, estados=ESTADOS_ACTIVO)


@app.route("/activos/<int:activo_id>/eliminar", methods=["POST"])
def eliminar_activo(activo_id):
    activo = Activo.query.get_or_404(activo_id)
    db.session.delete(activo)
    db.session.commit()
    flash(f"Activo '{activo.nombre}' eliminado.", "info")
    return redirect(url_for("listar_activos"))


# ---------- Mantenimientos ----------


@app.route("/mantenimientos")
def listar_mantenimientos():
    filtro = request.args.get("estado", "")
    mantenimientos = Mantenimiento.query.join(Activo).order_by(Mantenimiento.proxima_fecha).all()
    if filtro:
        mantenimientos = [m for m in mantenimientos if m.estado == filtro]
    return render_template("mantenimientos_list.html", mantenimientos=mantenimientos, filtro=filtro)


@app.route("/mantenimientos/nuevo", methods=["GET", "POST"])
def nuevo_mantenimiento():
    activos = Activo.query.order_by(Activo.nombre).all()
    activo_id_preseleccionado = request.args.get("activo_id", type=int)

    if request.method == "POST":
        frecuencia = int(request.form["frecuencia_dias"])
        ultima_fecha = parse_fecha(request.form.get("ultima_fecha")) or date.today()
        mantenimiento = Mantenimiento(
            activo_id=int(request.form["activo_id"]),
            tipo=request.form["tipo"],
            descripcion=request.form.get("descripcion", "").strip(),
            frecuencia_dias=frecuencia,
            ultima_fecha=parse_fecha(request.form.get("ultima_fecha")),
            proxima_fecha=ultima_fecha + timedelta(days=frecuencia),
            responsable=request.form.get("responsable", "").strip(),
            notas=request.form.get("notas", "").strip(),
        )
        db.session.add(mantenimiento)
        db.session.commit()
        flash("Mantenimiento programado creado.", "success")
        return redirect(url_for("detalle_activo", activo_id=mantenimiento.activo_id))

    return render_template(
        "mantenimiento_form.html",
        mantenimiento=None,
        activos=activos,
        tipos=TIPOS_MANTENIMIENTO,
        frecuencias=FRECUENCIAS,
        activo_id_preseleccionado=activo_id_preseleccionado,
    )


@app.route("/mantenimientos/<int:mantenimiento_id>/editar", methods=["GET", "POST"])
def editar_mantenimiento(mantenimiento_id):
    mantenimiento = Mantenimiento.query.get_or_404(mantenimiento_id)
    activos = Activo.query.order_by(Activo.nombre).all()

    if request.method == "POST":
        mantenimiento.activo_id = int(request.form["activo_id"])
        mantenimiento.tipo = request.form["tipo"]
        mantenimiento.descripcion = request.form.get("descripcion", "").strip()
        mantenimiento.frecuencia_dias = int(request.form["frecuencia_dias"])
        mantenimiento.ultima_fecha = parse_fecha(request.form.get("ultima_fecha"))
        mantenimiento.proxima_fecha = parse_fecha(request.form["proxima_fecha"])
        mantenimiento.responsable = request.form.get("responsable", "").strip()
        mantenimiento.notas = request.form.get("notas", "").strip()
        db.session.commit()
        flash("Mantenimiento actualizado.", "success")
        return redirect(url_for("detalle_activo", activo_id=mantenimiento.activo_id))

    return render_template(
        "mantenimiento_form.html",
        mantenimiento=mantenimiento,
        activos=activos,
        tipos=TIPOS_MANTENIMIENTO,
        frecuencias=FRECUENCIAS,
        activo_id_preseleccionado=None,
    )


@app.route("/mantenimientos/<int:mantenimiento_id>/completar", methods=["POST"])
def completar_mantenimiento(mantenimiento_id):
    mantenimiento = Mantenimiento.query.get_or_404(mantenimiento_id)
    notas = request.form.get("notas", "").strip()
    mantenimiento.completar(notas=notas)
    db.session.commit()
    flash("Mantenimiento registrado como realizado. Próxima fecha calculada automáticamente.", "success")
    return redirect(url_for("detalle_activo", activo_id=mantenimiento.activo_id))


@app.route("/mantenimientos/<int:mantenimiento_id>/eliminar", methods=["POST"])
def eliminar_mantenimiento(mantenimiento_id):
    mantenimiento = Mantenimiento.query.get_or_404(mantenimiento_id)
    activo_id = mantenimiento.activo_id
    db.session.delete(mantenimiento)
    db.session.commit()
    flash("Mantenimiento eliminado.", "info")
    return redirect(url_for("detalle_activo", activo_id=activo_id))


with app.app_context():
    db.create_all()
    seed_si_vacio()


if __name__ == "__main__":
    app.run(debug=True)
