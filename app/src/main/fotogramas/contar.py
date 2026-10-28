import json
import pathlib
from collections import Counter

carpeta = pathlib.Path("capturas_json")
conteo = Counter()
con_mano = Counter()

for ruta in carpeta.glob("*.json"):
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    etiqueta = datos["label"]
    conteo[etiqueta] += 1
    con_mano[etiqueta] += sum(1 for f in datos["frames"] if f["hands"])

print(f"Etiquetas distintas: {len(conteo)}")
for etiqueta, veces in sorted(conteo.items()):
    print(f"{etiqueta:30} capturas: {veces:3}   frames con mano: {con_mano[etiqueta]}")