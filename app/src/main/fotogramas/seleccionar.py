import json
import pathlib
import re

ORIGEN = pathlib.Path("capturas_json")
DESTINO = pathlib.Path("capturas_filtradas")
MIN_FRAMES_CON_MANO = 8  # sube o baja este número para ser más o menos exigente

DESTINO.mkdir(exist_ok=True)

copiadas = 0
etiquetas = set()

for ruta in ORIGEN.glob("*.json"):
    datos = json.loads(ruta.read_text(encoding="utf-8"))

    # Une las variantes: "AYUDAR VERSION 2" pasa a "AYUDAR"
    etiqueta = re.sub(r"\s+VERSION \d+$", "", datos["label"]).strip()

    # Descarta letras sueltas, números e intros
    if len(etiqueta) == 1 or etiqueta.isdigit() or etiqueta.startswith("INTRO"):
        continue

    con_mano = sum(1 for f in datos["frames"] if f["hands"])
    if con_mano < MIN_FRAMES_CON_MANO:
        continue

    datos["label"] = etiqueta
    (DESTINO / ruta.name).write_text(
        json.dumps(datos, ensure_ascii=False), encoding="utf-8"
    )
    etiquetas.add(etiqueta)
    copiadas += 1

print(f"Capturas copiadas: {copiadas}")
print(f"Palabras distintas: {len(etiquetas)}")
for e in sorted(etiquetas):
    print("  ", e)