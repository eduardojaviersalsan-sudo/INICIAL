from flask import Flask, render_template

app = Flask(__name__)

PLATOS = [
    {
        "nombre": "Ceviche",
        "region": "Costa",
        "descripcion": "Mezcla de mariscos o pescado marinados en limón, acompañado de cebolla, tomate y chifles.",
        "imagen": "ceviche.jpg",
    },
    {
        "nombre": "Encebollado",
        "region": "Costa",
        "descripcion": "Sopa de pescado (generalmente albacora) con yuca, cebolla curtida y limón, típica para el desayuno.",
        "imagen": "encebollado.jpg",
    },
    {
        "nombre": "Hornado",
        "region": "Sierra",
        "descripcion": "Cerdo entero horneado lentamente, servido con mote, llapingachos y curtido de cebolla.",
        "imagen": "hornado.jpg",
    },
    {
        "nombre": "Fritada",
        "region": "Sierra",
        "descripcion": "Carne de cerdo frita en su propia grasa, acompañada de mote, maduro y curtido.",
        "imagen": "fritada.jpg",
    },
    {
        "nombre": "Locro de Papa",
        "region": "Sierra",
        "descripcion": "Sopa cremosa de papas con queso, aguacate y a veces aguacate y ají.",
        "imagen": "locro.jpg",
    },
    {
        "nombre": "Maito de Tilapia",
        "region": "Amazonía",
        "descripcion": "Pescado envuelto en hoja de bijao y cocinado a la brasa, plato tradicional amazónico.",
        "imagen": "maito.jpg",
    },
]


@app.route("/")
def inicio():
    return render_template("index.html", platos=PLATOS)


@app.route("/plato/<nombre>")
def detalle_plato(nombre):
    plato = next((p for p in PLATOS if p["nombre"].lower() == nombre.lower()), None)
    return render_template("detalle.html", plato=plato)


if __name__ == "__main__":
    app.run(debug=True)
