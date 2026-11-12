"""
Paso 4 (para más adelante): cuando el detector ya ande bien en la PC,
esto lo convierte a TensorFlow Lite, que es lo que usa la app de Android.

Uso:
    python 4_exportar_android.py

La primera vez instala solo lo que necesita (puede tardar un rato).
"""
import config


def main():
    from ultralytics import YOLO

    if not config.MODELO_FINAL.exists():
        raise SystemExit("Todavía no hay modelo entrenado.")
    from detector import tamanio_entrenado

    modelo = YOLO(str(config.MODELO_FINAL))
    ruta = modelo.export(format="tflite", imgsz=tamanio_entrenado(modelo), half=True)
    print(f"\nModelo para Android listo en: {ruta}")


if __name__ == "__main__":
    main()
