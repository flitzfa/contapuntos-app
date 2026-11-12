"""
Paso 5: arma la app de Android (Contapuntos.apk) con el modelo entrenado.

Uso:
    python 5_crear_apk.py

Necesita Java 17 a 24 y el SDK de Android (los instala Android Studio).
La primera vez baja lo que le falta y tarda unos minutos; después es rápido.
El .apk queda en la carpeta del proyecto: lo pasás al celu y lo abrís para instalarlo.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

import config

CARPETA_APP = config.RAIZ / "app_android"
ONNX = config.MODELO_FINAL.with_suffix(".onnx")
APK = config.RAIZ / "Contapuntos.apk"


def exportar_modelo():
    """Pasa el modelo a ONNX (lo que entiende el celu), solo si cambió desde la última vez."""
    if not config.MODELO_FINAL.exists():
        sys.exit("Todavía no hay modelo entrenado. Corré primero 2_entrenar.py")
    if ONNX.exists() and ONNX.stat().st_mtime >= config.MODELO_FINAL.stat().st_mtime:
        return
    from ultralytics import YOLO

    from detector import tamanio_entrenado

    modelo = YOLO(str(config.MODELO_FINAL))
    modelo.export(format="onnx", imgsz=tamanio_entrenado(modelo), simplify=True, opset=17)


def version_java(carpeta):
    java = Path(carpeta, "bin", "java.exe" if os.name == "nt" else "java")
    if not java.exists():
        return 0
    salida = subprocess.run([str(java), "-version"], capture_output=True, text=True).stderr
    m = re.search(r'version "(\d+)', salida)
    return int(m.group(1)) if m else 0


def buscar_java():
    """Gradle 8.14 necesita Java 17 a 24 (con 25 todavía no anda)."""
    candidatas = [os.environ.get("JAVA_HOME", "")]
    for base in (r"C:\Program Files\Eclipse Adoptium", r"C:\Program Files\Java", r"C:\Program Files\Microsoft"):
        candidatas += sorted(glob.glob(base + r"\jdk-*"), reverse=True)
    candidatas.append(r"C:\Program Files\Android\Android Studio\jbr")
    for c in candidatas:
        if c and 17 <= version_java(c) <= 24:
            return c
    sys.exit("No encontré Java 17 a 24. Instalá Android Studio o Java 21 (adoptium.net).")


def main():
    exportar_modelo()
    entorno = dict(os.environ, JAVA_HOME=buscar_java())
    if not (entorno.get("ANDROID_HOME") or (CARPETA_APP / "local.properties").exists()):
        sys.exit("No encontré el SDK de Android. Instalá Android Studio y abrilo una vez.")
    gradlew = CARPETA_APP / ("gradlew.bat" if os.name == "nt" else "gradlew")
    r = subprocess.run([str(gradlew), "assembleRelease", "--console=plain"], cwd=CARPETA_APP, env=entorno)
    if r.returncode != 0:
        sys.exit("Falló el armado de la app (mirá el error de arriba).")
    shutil.copy2(CARPETA_APP / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk", APK)
    print(f"\nListo: {APK} ({APK.stat().st_size / 1e6:.0f} MB)")
    print("Pasalo al celu (por WhatsApp, Drive o cable), abrilo y aceptá instalar apps de esta fuente.")


if __name__ == "__main__":
    main()
