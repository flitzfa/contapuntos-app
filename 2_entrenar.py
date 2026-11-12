"""
Paso 2: entrena el detector de puntos con el dataset unificado.

Uso:
    python 2_entrenar.py              (usa lo que está en config.py)
    python 2_entrenar.py --epocas 20  (para una prueba rápida)

Si tenés placa NVIDIA con CUDA la usa sola; si no, entrena con el procesador
(tarda más, pero anda). Al terminar deja el modelo en modelos/detector_puntos.pt
"""
import argparse
import shutil

import config


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--epocas", type=int, default=config.EPOCAS)
    p.add_argument("--imgsz", type=int, default=config.TAMANIO_IMAGEN)
    p.add_argument("--batch", type=int, default=8, help="bajalo a 4 si se queda sin memoria")
    args = p.parse_args()

    data = config.CARPETA_DATASET / "data.yaml"
    if not data.exists():
        raise SystemExit("No encuentro el dataset. Corré primero 1_descargar_datasets.py")

    from ultralytics import YOLO

    modelo = YOLO(config.MODELO_BASE)
    resultado = modelo.train(
        data=str(data),
        epochs=args.epocas,
        imgsz=args.imgsz,
        batch=args.batch,
        patience=config.PACIENCIA,
        project=str(config.CARPETA_MODELOS),
        name="entrenamiento",
        exist_ok=True,
        max_det=config.MAX_DETECCIONES,
        # aumentos de datos pensados para fotos de celu de tejidos
        degrees=10, fliplr=0.5, flipud=0.0, hsv_h=0.05, hsv_s=0.6, hsv_v=0.4,
        workers=0,  # evita problemas en Windows
    )

    mejor = config.CARPETA_MODELOS / "entrenamiento" / "weights" / "best.pt"
    if mejor.exists():
        shutil.copy2(mejor, config.MODELO_FINAL)
        print(f"\nModelo listo en: {config.MODELO_FINAL}")
        print("Ahora probalo con: python 3_probar.py")
    else:
        print("No se generó best.pt, revisá los mensajes de arriba.")
    return resultado


if __name__ == "__main__":
    main()
