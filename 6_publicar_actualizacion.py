"""
Paso 6: publica una versión nueva de Contapuntos para que la app se actualice sola.

Uso:
    python 6_publicar_actualizacion.py "Qué cambió en esta versión"

Sube el número de versión, arma la app (como el paso 5), la publica en GitHub
(github.com/flitzfa/contapuntos-app, sección Releases) y guarda la versión en Git.
La próxima vez que se abra la app en el celu, ofrece actualizarse.

Requiere:
- gh (GitHub CLI) con la sesión iniciada (gh auth login).
- Armarla en esta PC: la firma sale de la clave de Android de esta PC
  (%USERPROFILE%\\.android\\debug.keystore). Con otra clave el celu rechaza la actualización.
- Todos los cambios guardados en Git (git commit), así cada versión publicada tiene su código.
"""
import importlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import config

crear_apk = importlib.import_module("5_crear_apk")

REPO = "flitzfa/contapuntos-app"  # si se cambia, cambiar también URL_VERSION en Actualizador.kt
GRADLE = config.RAIZ / "app_android" / "app" / "build.gradle.kts"


def git(*args):
    return subprocess.run(["git", *args], cwd=config.RAIZ, capture_output=True, text=True, check=True).stdout


def main():
    if len(sys.argv) < 2 or not sys.argv[1].strip():
        sys.exit(__doc__)
    notas = sys.argv[1].strip()
    if subprocess.run(["gh", "auth", "status"], capture_output=True).returncode != 0:
        sys.exit("gh no tiene la sesión iniciada. Corré: gh auth login")
    if git("status", "--porcelain").strip():
        sys.exit('Hay cambios sin guardar en Git. Guardalos primero (git add -A y git commit -m "...").')

    # la app solo ofrece la actualización si el versionCode es mayor al instalado
    original = GRADLE.read_text(encoding="utf-8")
    codigo = int(re.search(r"versionCode = (\d+)", original).group(1)) + 1
    nombre = f"0.{codigo}"
    nuevo = re.sub(r"versionCode = \d+", f"versionCode = {codigo}", original)
    GRADLE.write_text(re.sub(r'versionName = "[^"]*"', f'versionName = "{nombre}"', nuevo), encoding="utf-8")

    try:
        crear_apk.main()
        tag = f"v{nombre}"
        carpeta = Path(tempfile.mkdtemp(prefix="contapuntos-"))
        apk = carpeta / "contapuntos.apk"
        shutil.copy2(crear_apk.APK, apk)
        version = carpeta / "version.json"
        version.write_text(json.dumps({
            "versionCode": codigo,
            "versionName": nombre,
            "notes": notas,
            "apkUrl": f"https://github.com/{REPO}/releases/download/{tag}/contapuntos.apk",
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        r = subprocess.run(["gh", "release", "create", tag, str(apk), str(version), "--repo", REPO,
                            "--title", f"Versión {nombre}", "--notes", notas, "--latest"])
        if r.returncode != 0:
            sys.exit("La app se armó pero falló la subida a GitHub. No se publicó nada.")
    except BaseException:
        GRADLE.write_text(original, encoding="utf-8")  # el número de versión vuelve a como estaba
        raise

    git("commit", "-m", f"Versión {nombre}: {notas}", "--", str(GRADLE.relative_to(config.RAIZ)))
    git("tag", tag)
    print(f"\nListo: versión {nombre} publicada. La app la va a ofrecer la próxima vez que se abra.")


if __name__ == "__main__":
    main()
