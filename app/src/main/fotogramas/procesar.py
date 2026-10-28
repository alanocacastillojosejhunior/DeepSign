import json
import pathlib
import re
from collections import defaultdict

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

RAIZ = pathlib.Path(".")
MODELO = (RAIZ / ".." / "assets" / "hand_landmarker.task").resolve()
SALIDA = RAIZ / "capturas_json"
PATRON = re.compile(
    r"^(?P<palabra>.+)_frame_(?P<num>\d+)\.\d+s\.jpg$", re.IGNORECASE
)

SALIDA.mkdir(exist_ok=True)

# Agrupa las imágenes por (carpeta, palabra)
grupos = defaultdict(list)
for archivo in RAIZ.rglob("*.jpg"):
    m = PATRON.match(archivo.name)
    if m:
        grupos[(archivo.parent.name, m["palabra"])].append(
            (int(m["num"]), archivo)
        )

opciones = vision.HandLandmarkerOptions(
    base_options=python.BaseOptions(model_asset_path=str(MODELO)),
    running_mode=vision.RunningMode.IMAGE,
    num_hands=2,
)

total_archivos = 0

with vision.HandLandmarker.create_from_options(opciones) as detector:
    for (categoria, palabra), items in sorted(grupos.items()):
        items.sort()

        # Separa tramos continuos (si el número de frame salta, es otro tramo)
        tramos = []
        actual = [items[0]]
        for anterior, siguiente in zip(items, items[1:]):
            if siguiente[0] - anterior[0] > 1:
                tramos.append(actual)
                actual = [siguiente]
            else:
                actual.append(siguiente)
        tramos.append(actual)

        etiqueta = palabra.replace("_", " ").upper()

        for i, tramo in enumerate(tramos, 1):
            frames = []
            for num, ruta in tramo:
                # Lectura con OpenCV para evitar problemas con caracteres como la ñ
                datos = np.fromfile(str(ruta), dtype=np.uint8)
                bgr = cv2.imdecode(datos, cv2.IMREAD_COLOR)
                if bgr is None:
                    print(f"  No se pudo leer: {ruta.name}")
                    continue

                alto, ancho = bgr.shape[:2]
                rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                imagen = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                resultado = detector.detect(imagen)

                manos = []
                lateralidad = []
                for lms, cats in zip(resultado.hand_landmarks, resultado.handedness):
                    manos.append([{"x": p.x, "y": p.y} for p in lms])
                    lateralidad.append(cats[0].category_name)

                frames.append({
                    "timestampMs": num * 100,
                    "width": ancho,
                    "height": alto,
                    "hands": manos,
                    "handedness": lateralidad,
                })

            con_mano = sum(1 for f in frames if f["hands"])
            if con_mano == 0:
                print(f"{etiqueta} (tramo {i}): sin manos detectadas, omitido")
                continue

            captura = {
                "formatVersion": 2,
                "label": etiqueta,
                "description": f"Generada desde {categoria}/{palabra} tramo {i}",
                "camaraFrontal": False,
                "frames": frames,
            }
            nombre = f"{categoria}_{palabra}_{i}.json"
            (SALIDA / nombre).write_text(
                json.dumps(captura, ensure_ascii=False), encoding="utf-8"
            )
            total_archivos += 1
            print(f"{etiqueta} (tramo {i}): {len(frames)} frames, {con_mano} con mano")

print(f"\nListo: {total_archivos} capturas en la carpeta capturas_json")