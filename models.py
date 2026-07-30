from datetime import date, timedelta

from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()

FRECUENCIAS = [
    (7, "Semanal"),
    (15, "Quincenal"),
    (30, "Mensual"),
    (90, "Trimestral"),
    (180, "Semestral"),
    (365, "Anual"),
]

ESTADOS_ACTIVO = ["Operativo", "En mantenimiento", "Fuera de servicio", "Dado de baja"]

TIPOS_MANTENIMIENTO = ["Preventivo", "Correctivo", "Calibración", "Inspección", "Limpieza"]

PROXIMO_DIAS = 7


class Activo(db.Model):
    __tablename__ = "activos"

    id = db.Column(db.Integer, primary_key=True)
    codigo = db.Column(db.String(50), unique=True, nullable=False)
    nombre = db.Column(db.String(150), nullable=False)
    categoria = db.Column(db.String(100))
    ubicacion = db.Column(db.String(150))
    responsable = db.Column(db.String(150))
    estado = db.Column(db.String(50), default="Operativo", nullable=False)
    fecha_adquisicion = db.Column(db.Date)
    valor = db.Column(db.Float)
    notas = db.Column(db.Text)

    mantenimientos = db.relationship(
        "Mantenimiento",
        backref="activo",
        cascade="all, delete-orphan",
        order_by="Mantenimiento.proxima_fecha",
    )

    @property
    def proximo_mantenimiento(self):
        if not self.mantenimientos:
            return None
        return min(self.mantenimientos, key=lambda m: m.proxima_fecha)


class Mantenimiento(db.Model):
    __tablename__ = "mantenimientos"

    id = db.Column(db.Integer, primary_key=True)
    activo_id = db.Column(db.Integer, db.ForeignKey("activos.id"), nullable=False)
    tipo = db.Column(db.String(50), nullable=False)
    descripcion = db.Column(db.String(255))
    frecuencia_dias = db.Column(db.Integer, nullable=False)
    ultima_fecha = db.Column(db.Date)
    proxima_fecha = db.Column(db.Date, nullable=False)
    responsable = db.Column(db.String(150))
    notas = db.Column(db.Text)

    historial = db.relationship(
        "HistorialMantenimiento",
        backref="mantenimiento",
        cascade="all, delete-orphan",
        order_by="HistorialMantenimiento.fecha.desc()",
    )

    @property
    def estado(self):
        hoy = date.today()
        if self.proxima_fecha < hoy:
            return "Atrasado"
        if self.proxima_fecha <= hoy + timedelta(days=PROXIMO_DIAS):
            return "Próximo"
        return "Al día"

    @property
    def estado_color(self):
        return {"Atrasado": "danger", "Próximo": "warning", "Al día": "success"}[self.estado]

    def completar(self, fecha=None, notas=""):
        fecha = fecha or date.today()
        db.session.add(
            HistorialMantenimiento(mantenimiento_id=self.id, fecha=fecha, notas=notas)
        )
        self.ultima_fecha = fecha
        self.proxima_fecha = fecha + timedelta(days=self.frecuencia_dias)


class HistorialMantenimiento(db.Model):
    __tablename__ = "historial_mantenimientos"

    id = db.Column(db.Integer, primary_key=True)
    mantenimiento_id = db.Column(db.Integer, db.ForeignKey("mantenimientos.id"), nullable=False)
    fecha = db.Column(db.Date, nullable=False)
    notas = db.Column(db.Text)
