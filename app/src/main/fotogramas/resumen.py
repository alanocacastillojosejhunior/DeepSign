import pathlib
import re
from collections import defaultdict

raiz = pathlib.Path(".")
patron = re.compile(r"^(?P<palabra>.+)_frame_(?P<num>\d+)\.\d+s\.jpg$", re.IGNORECASE)

grupos = defaultdict(list)
sin_patron = []

for archivo in raiz.rglob("*.jpg"):
    m = patron.match(archivo.name)
    if not m:
        sin_patron.append(archivo.name)
        continue
    grupos[(archivo.parent.name, m["palabra"])].append(int(m["num"]))

with open("resumen.txt", "w", encoding="utf-8") as f:
    for (categoria, palabra), nums in sorted(grupos.items()):
        nums.sort()
        tramos = 1 + sum(1 for a, b in zip(nums, nums[1:]) if b - a > 1)
        f.write(
            f"{categoria} | {palabra} | {len(nums)} frames | "
            f"tramos: {tramos} | frames {nums[0]}-{nums[-1]}\n"
        )

con_un_tramo = sum(1 for nums in grupos.values() if 1 + sum(1 for a, b in zip(sorted(nums), sorted(nums)[1:]) if b - a > 1) == 1)

print(f"Palabras distintas: {len(grupos)}")
print(f"Palabras con un solo tramo: {con_un_tramo}")
print(f"Palabras con varios tramos: {len(grupos) - con_un_tramo}")
print(f"Archivos que no encajan en el patrón: {len(sin_patron)}")
for nombre in sin_patron[:5]:
    print("    ", nombre)
print("Detalle guardado en resumen.txt")