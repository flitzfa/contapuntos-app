# Contador de puntos de tejido

Detector que mira una foto de un tejido (puntos montados en la aguja) y cuenta cuántos puntos hay.
Primero se prueba en la PC; cuando ande bien, se pasa a Android.

## Instalación (una sola vez)

Necesitás Python 3.10 o más nuevo. En la carpeta del proyecto:

```
python -m venv venv
venv\Scripts\activate          (en Windows)
pip install -r requirements.txt
```

Si la PC tiene placa NVIDIA (fijate con `nvidia-smi`), instalá antes PyTorch con CUDA,
si no `requirements.txt` instala la versión que solo usa el procesador:

```
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu130
```

Comprobás que la vea con `python -c "import torch; print(torch.cuda.is_available())"` (tiene que decir `True`).

## Paso 1: bajar los datasets

1. Creá una cuenta gratis en https://roboflow.com
2. Andá a Settings → API Keys y copiá la **Private API Key**
3. Guardala en un archivo `.env` en la carpeta del proyecto, con esta línea
   (el `.env` está en `.gitignore`, así no se sube a ningún lado):

```
ROBOFLOW_API_KEY=tu_clave
```

4. Corré:

```
python 1_descargar_datasets.py
```

(También anda `python 1_descargar_datasets.py TU_API_KEY` o con la variable de entorno `ROBOFLOW_API_KEY`.)

Baja los datasets que están en `config.py` (lista `DATASETS`), se queda solo con las clases
que son puntos (descarta la aguja) y los junta en `datos/unificado`. Al final te muestra
cuántas imágenes quedaron.

Para sumar otro dataset de Roboflow Universe: copiás de la URL
`universe.roboflow.com/<workspace>/<proyecto>` esas dos partes, las agregás a `DATASETS`
y volvés a correr el script (los que ya bajó no los baja de nuevo).
Para sacar uno, lo borrás (o comentás) de `DATASETS` y corrés
`python 1_descargar_datasets.py --solo-unificar`: deja de usarse aunque ya esté bajado.

Hoy se usan tres, todos con puntos montados en la aguja:
`knitting/knittingstitches` (fotos parejas, unos 20 puntos), `purlie/count-stitches`
(fotos variadas de internet, de 11 a 90 puntos) y `yarn-07lgf/yarn-wydjg` (primeros
planos con manos). Con los recortes quedan 947 fotos para entrenar y 145 para validar.
`roobot/knit-stitches` se sacó porque etiqueta las "V" de la tela ya tejida, no los
puntos en la aguja, y hacía contar de más.

Al unificar, además se agrega a entrenamiento un **recorte ampliado** de cada foto
(un pedazo de aguja con pocos puntos grandes, como un primer plano). Se controla con
`RECORTES_ZOOM_POR_IMAGEN` en `config.py` (0 = no agregar). Con eso el error contando
bajó a la mitad.

## Paso 2: entrenar

```
python 2_entrenar.py
```

- Con placa NVIDIA es rápido. Sin placa entrena con el procesador y puede tardar
  una o varias horas según cuántas imágenes haya.
  Referencia en esta PC (GTX 1660 SUPER, 947 fotos contando los recortes, 80 épocas):
  unas 2 horas a 960 (`TAMANIO_IMAGEN` actual).
- **Cerrá la ventana de prueba (`3_probar.py`) antes de entrenar.** Si las dos usan la
  placa a la vez se queda sin memoria y el entrenamiento se pone cientos de veces más lento.
  Si se corta, se retoma desde donde quedó con
  `yolo train resume model=modelos/entrenamiento/weights/last.pt`
  (al terminar, copiás `modelos/entrenamiento/weights/best.pt` como `modelos/detector_puntos.pt`).
- Para que use la placa, PyTorch tiene que estar instalado con CUDA (ver abajo).
- Para una prueba rápida antes de la de verdad: `python 2_entrenar.py --epocas 10`
- Si tira error de memoria: `python 2_entrenar.py --batch 4`
- Las gráficas del entrenamiento quedan en `modelos/entrenamiento/` (mirá `results.png`).

El modelo final queda en `modelos/detector_puntos.pt`.

## Paso 3: probar

```
python 3_probar.py
```

Se abre una página en el navegador: arrastrás cualquier foto (de internet o la que sea)
y te muestra un puntito numerado sobre cada punto detectado y el total.
Los verdes son detecciones seguras y los naranjas, dudosas. Con la barra de
sensibilidad ves cómo cambia el conteo.

Para probar muchas fotos de una:

```
python 3_probar.py carpeta_con_fotos
```

Deja las fotos marcadas en `resultados/` y te imprime el conteo de cada una.

**Cómo sacar la foto para que cuente bien:** la aguja entera en la foto, desde arriba,
a un brazo de distancia y sobre un fondo liso. Así son las fotos con las que aprendió.
Con primeros planos muy cerca, la aguja parada o tejidos en varias agujas todavía falla bastante.
En `fotos_prueba/` hay fotos libres de internet para probar (fuentes y licencias en
`fotos_prueba/FUENTES.md`; son solo para probar, no para entrenar algo que se publique).

**Consejo para probar en serio:** juntá fotos donde sepas el número real
(por ejemplo, contándolas una vez a mano) y anotá cuánto le erra el detector.
Así sabés si los cambios mejoran o empeoran.

## Paso 4: pasar a Android (más adelante)

```
python 4_exportar_android.py
```

Genera el modelo en formato TensorFlow Lite (`.tflite`). La app del paso 5 no lo necesita
(usa ONNX), queda por si algún día se hace otra app con TensorFlow Lite.

## Paso 5: la app de Android (Contapuntos)

```
python 5_crear_apk.py
```

Deja `Contapuntos.apk` en la carpeta del proyecto. Se pasa al celu (WhatsApp, Drive o cable),
se abre y se acepta "instalar apps de esta fuente". Anda en Android 9 o más nuevo y no
necesita internet: el modelo va adentro de la app y cuenta en el mismo celu.

- Necesita Java 17 a 24 y el SDK de Android (vienen con Android Studio). El script busca solo
  el Java que sirve; con Java 25 Gradle todavía no anda.
- Lo arma con el modelo que esté en `modelos/detector_puntos.pt`: si reentrenás, volvé a
  correr el script y sale la app nueva.
- El código de la app está en `app_android/` (Kotlin + Jetpack Compose). El modelo se pasa a
  `modelos/detector_puntos.onnx` y se copia adentro al armarla.

En la app: **Contar en vivo** abre la cámara y va marcando los puntos y el total mientras se
apunta a la aguja (se actualiza cada vez que termina de contar una imagen; cuánto tarda depende
del celu). **Congelar** se queda con lo que se ve y pasa a la pantalla de revisión. También
están **Sacar foto** y **Galería**. En la revisión muestra el total con un puntito numerado
sobre cada punto. Se puede tocar un puntito para sacarlo, tocar donde falta uno para sumarlo,
pellizcar para acercar (doble toque vuelve) y mover la sensibilidad, igual que en la página
del paso 3. La primera vez pide permiso para usar la cámara.

Para probarla en la PC sin celular: `app_android\gradlew assembleDebug -Pabis=x86_64`
arma una versión para el emulador de Android Studio.

## Paso 6: publicar una actualización

Desde la versión 0.2 la app se actualiza sola: al abrirse busca si hay una versión nueva
y, si hay, muestra "Hay una versión nueva" con **Actualizar** / **Más tarde**. La primera vez
Android pide activar "Permitir de esta fuente" para Contapuntos; después ya no.

Para publicar una versión nueva:

1. Hacé los cambios y guardalos en Git:
   ```
   git add -A
   git commit -m "Qué cambié"
   ```
2. Publicala:
   ```
   python 6_publicar_actualizacion.py "Lo que va a leer en el celu, por ejemplo: cuenta mejor con la aguja parada"
   ```

El script sube el número de versión, arma la app, la publica en
https://github.com/flitzfa/contapuntos-app (Releases) y guarda la versión en Git con su
etiqueta (`v0.3`, `v0.4`...). Ese repositorio es público y solo tiene las versiones
publicadas, no el código.

Importante:
- Hay que armarla **en esta PC**: la app va firmada con la clave de Android de esta PC
  (`%USERPROFILE%\.android\debug.keystore`). Un celu solo acepta una actualización firmada
  con la misma clave que la app instalada. Si cambiás de PC, copiá ese archivo antes.
- Necesita `gh` (GitHub CLI) con la sesión iniciada (`gh auth login`).

## Historial (Git)

El proyecto está en Git (no se suben los datasets, resultados, `venv` ni el `.env`).
`git log --oneline` muestra los cambios, y cada versión publicada tiene su etiqueta:
`git checkout v0.2` vuelve al código exacto de esa versión (`git checkout main` para volver).

## Qué tocar si cuenta mal

| Problema | Qué probar |
|---|---|
| Se come puntos | Bajar la sensibilidad en la página de prueba, o `CONFIANZA_DEFECTO` en `config.py` |
| Cuenta puntos de más | Subir la sensibilidad, o bajar `SOLAPAMIENTO` a 0.4 |
| Puntos muy chiquitos en la foto | Ya está en `TAMANIO_IMAGEN = 960`; sacar la foto más de cerca |
| Cuenta como puntos los rulos de la hebra suelta o un fondo muy estampado | Sacar la foto sobre un fondo liso y con la hebra lejos de la aguja |
| Anda bien con fotos del dataset y mal con fotos reales | Faltan fotos parecidas a las reales: sumar datasets o fotos propias |
