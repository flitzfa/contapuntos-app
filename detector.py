"""
El "corazón": carga el modelo, detecta los puntos en una imagen y dibuja
un puntito numerado sobre cada uno. Esto es lo que después se pasa a Android.
"""
import cv2

import config

_modelo = None


def cargar_modelo(ruta=None):
    global _modelo
    if _modelo is None:
        from ultralytics import YOLO
        ruta = ruta or config.MODELO_FINAL
        if not ruta.exists():
            raise FileNotFoundError(f"No existe {ruta}. Entrená primero con 2_entrenar.py")
        _modelo = YOLO(str(ruta))
    return _modelo


def tamanio_entrenado(modelo):
    """Usa el mismo tamaño de imagen con el que se entrenó (si no, cuenta mal)."""
    try:
        return int(modelo.ckpt["train_args"]["imgsz"])
    except Exception:
        return config.TAMANIO_IMAGEN


def detectar(imagen_bgr, confianza=config.CONFIANZA_DEFECTO, imgsz=None):
    """Devuelve una lista de puntos: [(x, y, confianza), ...] ordenados de izquierda a derecha."""
    modelo = cargar_modelo()
    r = modelo.predict(imagen_bgr, conf=confianza, imgsz=imgsz or tamanio_entrenado(modelo),
                       iou=config.SOLAPAMIENTO, max_det=config.MAX_DETECCIONES, verbose=False)[0]
    puntos = []
    for caja, conf in zip(r.boxes.xyxy.cpu().numpy(), r.boxes.conf.cpu().numpy()):
        x0, y0, x1, y1 = caja
        puntos.append(((x0 + x1) / 2, (y0 + y1) / 2, float(conf)))
    # numerados de izquierda a derecha, como se leen los puntos sobre la aguja
    puntos.sort(key=lambda p: (p[0], p[1]))
    return puntos


def dibujar(imagen_bgr, puntos, numerar=True):
    """Dibuja un círculo por punto y, si entra, su número. Devuelve una copia."""
    img = imagen_bgr.copy()
    h, w = img.shape[:2]
    radio = max(3, int(min(h, w) / 160))
    fuente = max(0.35, min(h, w) / 1600)
    for i, (x, y, conf) in enumerate(puntos, start=1):
        # color de verde (seguro) a naranja (dudoso) según la confianza
        color = (0, int(140 + 115 * conf), int(255 * (1 - conf)))
        cv2.circle(img, (int(x), int(y)), radio, color, -1)
        cv2.circle(img, (int(x), int(y)), radio, (0, 0, 0), 1)
        if numerar:
            cv2.putText(img, str(i), (int(x) + radio, int(y) - radio),
                        cv2.FONT_HERSHEY_SIMPLEX, fuente, (255, 255, 255), 3, cv2.LINE_AA)
            cv2.putText(img, str(i), (int(x) + radio, int(y) - radio),
                        cv2.FONT_HERSHEY_SIMPLEX, fuente, (40, 40, 40), 1, cv2.LINE_AA)
    # cartel con el total
    texto = f"{len(puntos)} puntos"
    escala = max(0.8, min(h, w) / 600)
    (tw, th), _ = cv2.getTextSize(texto, cv2.FONT_HERSHEY_SIMPLEX, escala, 2)
    cv2.rectangle(img, (10, 10), (30 + tw, 30 + th), (255, 255, 255), -1)
    cv2.putText(img, texto, (20, 20 + th), cv2.FONT_HERSHEY_SIMPLEX, escala, (30, 30, 30), 2, cv2.LINE_AA)
    return img
