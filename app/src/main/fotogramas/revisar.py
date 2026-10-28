import pathlib

raiz = pathlib.Path(".")
extensiones = {".jpg", ".jpeg", ".png"}

for carpeta in sorted(p for p in raiz.iterdir() if p.is_dir()):
    imagenes = sorted(a for a in carpeta.rglob("*") if a.suffix.lower() in extensiones)
    print(f"{carpeta.name}: {len(imagenes)} imágenes")
    for a in imagenes[:3]:
        print("    ", a)