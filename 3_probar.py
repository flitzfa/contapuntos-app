"""
Paso 3: probá el detector con cualquier foto.

Modo ventana (recomendado):
    python 3_probar.py
    -> se abre en el navegador; arrastrás una foto y te dice cuántos puntos ve.

Modo consola, para una foto o una carpeta entera:
    python 3_probar.py foto.jpg
    python 3_probar.py carpeta_con_fotos/
    -> guarda las imágenes marcadas en la carpeta "resultados".
"""
import sys
from pathlib import Path

import cv2
import numpy as np

import config
import detector

EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def procesar(imagen_rgb, confianza, numerar):
    if imagen_rgb is None:
        return None, "Subí una foto"
    bgr = cv2.cvtColor(np.asarray(imagen_rgb), cv2.COLOR_RGB2BGR)
    puntos = detector.detectar(bgr, confianza=confianza)
    marcada = detector.dibujar(bgr, puntos, numerar=numerar)
    dudosos = sum(1 for p in puntos if p[2] < 0.5)
    texto = f"## {len(puntos)} puntos"
    if dudosos:
        texto += f"\n{dudosos} de esos son dudosos (en naranja)."
    return cv2.cvtColor(marcada, cv2.COLOR_BGR2RGB), texto


def modo_ventana():
    import gradio as gr

    detector.cargar_modelo()
    with gr.Blocks(title="Contador de puntos") as app:
        gr.Markdown("# Contador de puntos de tejido\nSubí o arrastrá una foto del tejido.")
        with gr.Row():
            with gr.Column():
                entrada = gr.Image(label="Foto", type="numpy")
                conf = gr.Slider(0.05, 0.9, value=config.CONFIANZA_DEFECTO, step=0.05,
                                 label="Sensibilidad (más bajo = detecta más, se equivoca más)")
                numerar = gr.Checkbox(value=True, label="Mostrar números")
            with gr.Column():
                salida = gr.Image(label="Resultado")
                total = gr.Markdown()
        for comp in (entrada, conf, numerar):
            comp.change(procesar, [entrada, conf, numerar], [salida, total])
    app.launch(inbrowser=True)


def modo_consola(ruta):
    ruta = Path(ruta)
    fotos = [ruta] if ruta.is_file() else sorted(p for p in ruta.iterdir() if p.suffix.lower() in EXT)
    carpeta = config.RAIZ / "resultados"
    carpeta.mkdir(exist_ok=True)
    for foto in fotos:
        img = cv2.imread(str(foto))
        if img is None:
            print(f"{foto.name}: no se pudo abrir")
            continue
        puntos = detector.detectar(img)
        cv2.imwrite(str(carpeta / foto.name), detector.dibujar(img, puntos))
        print(f"{foto.name}: {len(puntos)} puntos")
    print(f"\nImágenes marcadas en: {carpeta}")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        modo_consola(sys.argv[1])
    else:
        modo_ventana()
