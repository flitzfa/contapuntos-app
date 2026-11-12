"""
Configuración general del contador de puntos.
Todo lo que quieras tocar está acá, así no hace falta meterse en los otros scripts.
"""
from pathlib import Path

RAIZ = Path(__file__).resolve().parent

# --- Datasets públicos de Roboflow Universe -------------------------------
# Formato: (workspace, proyecto). Salen de la URL:
#   https://universe.roboflow.com/<workspace>/<proyecto>
# Si encontrás otro dataset de tejido que te guste, lo agregás a la lista.
DATASETS = [
    ("knitting", "knittingstitches"),   # clases: aguja y punto (lo que más nos sirve)
    ("purlie", "count-stitches"),       # fotos variadas de internet, puntos en la aguja (11 a 90 por foto)
    ("yarn-07lgf", "yarn-wydjg"),       # primeros planos con manos y agujas (clase "stiches-of-yarn")
    # ("roobot", "knit-stitches"),      # SACADO: etiqueta las "V" de la tela ya tejida (hasta 1796 por foto),
    #                                   # no los puntos en la aguja, y tiene fotos mal etiquetadas. Hace contar de más.
]

# Palabras para reconocer qué clases de cada dataset son "puntos".
# Todo lo que NO coincida (por ejemplo la aguja) se descarta.
PALABRAS_PUNTO = ["stitch", "stiches", "punto", "loop"]  # "stiches" así, mal escrito, viene en yarn-07lgf
PALABRAS_EXCLUIR = ["needle", "aguja", "defect", "machine"]

# Recortes ampliados por foto que se suman al entrenamiento (simulan primeros planos de la aguja).
# Con 1, el error contando bajó de 0.43 a 0.19 puntos por foto. 0 = no agregar.
RECORTES_ZOOM_POR_IMAGEN = 1

# --- Carpetas -------------------------------------------------------------
CARPETA_DESCARGAS = RAIZ / "datos" / "descargas"   # datasets crudos tal cual vienen
CARPETA_DATASET = RAIZ / "datos" / "unificado"     # dataset final, una sola clase "punto"
CARPETA_MODELOS = RAIZ / "modelos"                 # entrenamientos y modelo final
MODELO_FINAL = CARPETA_MODELOS / "detector_puntos.pt"

# --- Entrenamiento --------------------------------------------------------
MODELO_BASE = "yolo11n.pt"   # el más chico y rápido, ideal para después pasarlo al celu
EPOCAS = 80
TAMANIO_IMAGEN = 960         # medido: 960 erra menos que 640 en fotos completas (0.18 vs 0.50 puntos/foto)
PACIENCIA = 50               # corta solo si deja de mejorar durante tantas épocas
                             # (con 20 cortaba antes de la fase final: con fotos variadas la validación salta mucho)

# --- Detección ------------------------------------------------------------
CONFIANZA_DEFECTO = 0.35     # más bajo = detecta más (y se equivoca más)
MAX_DETECCIONES = 1000       # tejidos con muchos puntos
SOLAPAMIENTO = 0.4           # si dos detecciones se pisan más que esto, cuenta una sola (evita duplicados)
# CONFIANZA 0.35 y SOLAPAMIENTO 0.4 salieron de probar 0.2-0.5 y 0.3-0.5 midiendo el error de conteo
# en fotos de los 3 datasets (cada dataset pesa igual).
