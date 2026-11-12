"""
Paso 1: baja los datasets públicos de Roboflow y los junta en uno solo
con una única clase: "punto".

Uso:
    python 1_descargar_datasets.py TU_API_KEY

La API key la sacás gratis creando una cuenta en https://roboflow.com
(Settings -> API Keys -> Private API Key).
También podés ponerla en la variable de entorno ROBOFLOW_API_KEY, o en un
archivo .env al lado de este script con la línea ROBOFLOW_API_KEY=tu_clave
(y entonces corrés solo: python 1_descargar_datasets.py).

Si ya bajaste los datasets y solo querés volver a unificarlos:
    python 1_descargar_datasets.py --solo-unificar
"""
import os
import random
import shutil
import sys

import yaml

import config

EXT_IMAGEN = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def descargar(api_key):
    from roboflow import Roboflow

    rf = Roboflow(api_key=api_key)
    config.CARPETA_DESCARGAS.mkdir(parents=True, exist_ok=True)

    for workspace, proyecto in config.DATASETS:
        destino = config.CARPETA_DESCARGAS / f"{workspace}__{proyecto}"
        if destino.exists() and any(destino.iterdir()):
            print(f"[ya estaba] {workspace}/{proyecto}")
            continue
        print(f"\n[bajando] {workspace}/{proyecto} ...")
        try:
            proj = rf.workspace(workspace).project(proyecto)
            versiones = proj.versions()
            if not versiones:
                print("   No tiene versiones publicadas, lo salteo.")
                continue
            ultima = max(versiones, key=lambda v: int(str(v.version).split("/")[-1]))
            ultima.download("yolov8", location=str(destino))
        except Exception as e:  # un dataset roto no frena a los demás
            print(f"   No se pudo bajar: {e}")


def clases_de_punto(nombres):
    """Devuelve los índices de las clases que son puntos de tejido."""
    elegidas = []
    for i, nombre in enumerate(nombres):
        n = str(nombre).lower()
        if any(x in n for x in config.PALABRAS_EXCLUIR):
            continue
        if any(x in n for x in config.PALABRAS_PUNTO):
            elegidas.append(i)
    return elegidas


def linea_a_caja(partes):
    """Pasa una etiqueta YOLO (caja o polígono) a 'cx cy w h'."""
    nums = [float(p) for p in partes]
    if len(nums) == 4:
        return nums
    # polígono de segmentación: x1 y1 x2 y2 ... -> caja que lo envuelve
    xs, ys = nums[0::2], nums[1::2]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    return [(x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0]


def agregar_recortes(carpeta_train, por_imagen, semilla=1):
    """Agrega recortes ampliados de cada foto de entrenamiento (como un primer plano de la aguja).
    Solo acepta recortes que no cortan ningún punto por la mitad, así las etiquetas siguen siendo exactas."""
    import cv2

    rnd = random.Random(semilla)
    nuevos = 0
    for img in sorted((carpeta_train / "images").iterdir()):
        bgr = cv2.imread(str(img))
        if bgr is None:
            continue
        h, w = bgr.shape[:2]
        cajas = []
        for linea in (carpeta_train / "labels" / (img.stem + ".txt")).read_text().splitlines():
            cx, cy, bw, bh = (float(x) for x in linea.split()[1:5])
            cajas.append(((cx - bw / 2) * w, (cy - bh / 2) * h, (cx + bw / 2) * w, (cy + bh / 2) * h))
        hechos = 0
        for _ in range(2000):
            if hechos == por_imagen:
                break
            f = rnd.uniform(0.15, 0.45)  # ventana chica = aumento de 2x a 6x
            cw, ch = int(w * f), int(h * f * rnd.uniform(0.75, 1.33))
            if ch >= h or cw >= w:
                continue
            x0, y0 = rnd.randint(0, w - cw), rnd.randint(0, h - ch)
            x1, y1 = x0 + cw, y0 + ch
            dentro = [c for c in cajas if c[0] >= x0 and c[1] >= y0 and c[2] <= x1 and c[3] <= y1]
            fuera = sum(1 for a, b, c, d in cajas if c <= x0 or a >= x1 or d <= y0 or b >= y1)
            if len(dentro) + fuera != len(cajas) or len(dentro) < 2:
                continue
            esc = 640 / max(cw, ch)
            recorte = cv2.resize(bgr[y0:y1, x0:x1], (int(cw * esc), int(ch * esc)), interpolation=cv2.INTER_CUBIC)
            nombre = f"{img.stem}__zoom{hechos}"
            cv2.imwrite(str(carpeta_train / "images" / (nombre + ".jpg")), recorte, [cv2.IMWRITE_JPEG_QUALITY, 92])
            (carpeta_train / "labels" / (nombre + ".txt")).write_text("\n".join(
                f"0 {((a + c) / 2 - x0) / cw:.6f} {((b + d) / 2 - y0) / ch:.6f} {(c - a) / cw:.6f} {(d - b) / ch:.6f}"
                for a, b, c, d in dentro))
            hechos += 1
        nuevos += hechos
    return nuevos


def unificar():
    salida = config.CARPETA_DATASET
    if salida.exists():
        shutil.rmtree(salida)
    for split in ("train", "valid"):
        (salida / split / "images").mkdir(parents=True)
        (salida / split / "labels").mkdir(parents=True)

    if not config.CARPETA_DESCARGAS.exists():
        sys.exit("No hay datasets bajados. Corré primero con tu API key.")

    total = {"train": 0, "valid": 0}
    sin_valid = []

    # solo los que siguen en config.DATASETS (si sacás uno de la lista, deja de usarse aunque esté bajado)
    for workspace, proyecto in config.DATASETS:
        carpeta = config.CARPETA_DESCARGAS / f"{workspace}__{proyecto}"
        data_yaml = carpeta / "data.yaml"
        if not data_yaml.exists():
            continue
        datos = yaml.safe_load(data_yaml.read_text(encoding="utf-8"))
        nombres = datos.get("names", [])
        if isinstance(nombres, dict):
            nombres = [nombres[k] for k in sorted(nombres)]
        elegidas = clases_de_punto(nombres)

        print(f"\n{carpeta.name}")
        print(f"   clases del dataset: {nombres}")
        print(f"   uso como 'punto':   {[nombres[i] for i in elegidas]}")
        if not elegidas:
            print("   Ninguna clase coincide, lo salteo (revisá PALABRAS_PUNTO en config.py).")
            continue

        tuvo_valid = False
        for split_origen in ("train", "valid", "test"):
            dir_img = carpeta / split_origen / "images"
            dir_lbl = carpeta / split_origen / "labels"
            if not dir_img.exists():
                continue
            split_destino = "train" if split_origen == "train" else "valid"
            if split_destino == "valid":
                tuvo_valid = True

            for img in dir_img.iterdir():
                if img.suffix.lower() not in EXT_IMAGEN:
                    continue
                lbl = dir_lbl / (img.stem + ".txt")
                lineas = []
                if lbl.exists():
                    for linea in lbl.read_text().splitlines():
                        partes = linea.split()
                        if len(partes) < 5:
                            continue
                        if int(float(partes[0])) in elegidas:
                            caja = linea_a_caja(partes[1:])
                            lineas.append("0 " + " ".join(f"{v:.6f}" for v in caja))
                if not lineas:
                    continue  # imagen sin puntos: no aporta
                nombre = f"{carpeta.name}__{img.stem}"
                shutil.copy2(img, salida / split_destino / "images" / (nombre + img.suffix))
                (salida / split_destino / "labels" / (nombre + ".txt")).write_text("\n".join(lineas))
                total[split_destino] += 1

        if not tuvo_valid:
            sin_valid.append(carpeta.name)

    # Si quedó sin imágenes de validación, separo un 15% de train
    if total["valid"] == 0 and total["train"] > 0:
        imagenes = sorted((salida / "train" / "images").iterdir())
        random.seed(42)
        random.shuffle(imagenes)
        for img in imagenes[: max(1, len(imagenes) * 15 // 100)]:
            lbl = salida / "train" / "labels" / (img.stem + ".txt")
            shutil.move(str(img), salida / "valid" / "images" / img.name)
            shutil.move(str(lbl), salida / "valid" / "labels" / lbl.name)
            total["train"] -= 1
            total["valid"] += 1

    # recortes ampliados solo en train (después de separar valid, para no mezclar)
    if config.RECORTES_ZOOM_POR_IMAGEN > 0:
        nuevos = agregar_recortes(salida / "train", config.RECORTES_ZOOM_POR_IMAGEN)
        total["train"] += nuevos
        print(f"\nAgregué {nuevos} recortes ampliados (tipo primer plano) al entrenamiento.")

    (salida / "data.yaml").write_text(
        yaml.safe_dump(
            {"path": str(salida), "train": "train/images", "val": "valid/images", "names": {0: "punto"}},
            allow_unicode=True,
        ),
        encoding="utf-8",
    )
    print(f"\nListo: {total['train']} imágenes para entrenar y {total['valid']} para validar.")
    print(f"Dataset unificado en: {salida}")
    if total["train"] < 50:
        print("Ojo: son pocas imágenes. Va a andar, pero conviene sumar más datasets o fotos propias.")


def leer_env():
    """Carga las variables de un archivo .env (CLAVE=valor) si existe, sin pisar las que ya estén."""
    ruta = config.RAIZ / ".env"
    if ruta.exists():
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            if "=" in linea and not linea.lstrip().startswith("#"):
                k, v = linea.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip().strip('"'))


if __name__ == "__main__":
    leer_env()
    if "--solo-unificar" not in sys.argv:
        args = [a for a in sys.argv[1:] if not a.startswith("--")]
        clave = args[0] if args else os.environ.get("ROBOFLOW_API_KEY")
        if not clave:
            sys.exit(__doc__)
        descargar(clave)
    unificar()
