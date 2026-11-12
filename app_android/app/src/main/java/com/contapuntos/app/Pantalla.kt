package com.contapuntos.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// Colores sacados de los ovillos
private val Ciruela = Color(0xFF24122E)
private val Rosa = Color(0xFFF06C9B)
private val RosaOscuro = Color(0xFFD9507F)
private val RosaSombra = Color(0xFFA03A62)
private val Mostaza = Color(0xFFF5B841)
private val Salvia = Color(0xFF9CCB9F)
private val Crema = Color(0xFFFFF3E6)

@OptIn(ExperimentalTextApi::class)
private val Fredoka = FontFamily(
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

// Sensibilidad 0..1 -> confianza 0.60..0.15. Arranca en 0.35, el valor de CONFIANZA_DEFECTO en config.py.
private fun confianza(sens: Float) = 0.6f - 0.45f * sens
private const val SENS_INICIAL = (0.6f - 0.35f) / 0.45f

/** Panel de vidrio: blanco translúcido con un borde que brilla arriba. */
private fun Modifier.vidrio(forma: Shape, opacidad: Float = 0.14f) = this
    .clip(forma)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = opacidad + 0.06f), Color.White.copy(alpha = opacidad - 0.06f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))), forma)

@Composable
fun ContapuntosApp(detector: Lazy<Detector>) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Rosa, onPrimary = Ciruela, background = Ciruela, surface = Ciruela)) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var foto by remember { mutableStateOf<Bitmap?>(null) }
        var candidatos by remember { mutableStateOf<List<Candidato>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var tanda by remember { mutableIntStateOf(0) } // sube con cada foto nueva

        // carga el modelo mientras la usuaria saca la foto
        LaunchedEffect(Unit) { withContext(Dispatchers.Default) { runCatching { detector.value } } }

        fun procesar(uri: Uri) {
            error = null
            candidatos = null
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { runCatching { cargarFoto(ctx, uri) }.getOrNull() }
                if (bmp == null) {
                    foto = null
                    error = "No pude abrir esa foto. Probá con otra."
                    return@launch
                }
                foto = bmp
                withContext(Dispatchers.Default) { runCatching { detector.value.candidatos(bmp) } }
                    .onSuccess { candidatos = it; tanda++ }
                    .onFailure { error = "No pude contar en esta foto: ${it.message}" }
            }
        }

        val archivoCamara = remember { File(ctx.cacheDir, "fotos").apply { mkdirs() }.resolve("camara.jpg") }
        val uriCamara = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fotos", archivoCamara) }
        val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) procesar(uriCamara) }
        val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { procesar(it) } }
        val sacarFoto: () -> Unit = {
            try {
                camara.launch(uriCamara)
            } catch (e: ActivityNotFoundException) {
                error = "No encontré la cámara. Elegí una foto de la galería."
            }
        }
        val elegirFoto: () -> Unit = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

        BackHandler(enabled = foto != null) {
            foto = null
            candidatos = null
            error = null
        }

        Box(Modifier.fillMaxSize().background(Ciruela)) {
            Fondo(foto)
            val f = foto
            if (f == null) {
                Inicio(error, sacarFoto, elegirFoto)
            } else {
                Resultado(f, candidatos, tanda, error, sacarFoto, elegirFoto)
            }
        }
    }
}

/** Abre la foto derecha (respeta la rotación del celu) y la achica a 2048 px como máximo. */
private fun cargarFoto(ctx: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val lado = maxOf(info.size.width, info.size.height)
        if (lado > 2048) {
            val f = 2048f / lado
            dec.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
        }
    }

@Composable
private fun Fondo(foto: Bitmap?) {
    if (foto == null) {
        // manchas de lana desenfocadas detrás del vidrio
        Canvas(Modifier.fillMaxSize()) {
            fun mancha(c: Color, x: Float, y: Float, r: Float) {
                val centro = Offset(size.width * x, size.height * y)
                val radio = size.minDimension * r
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), centro, radio), radio, centro)
            }
            mancha(Rosa, 0.1f, 0.18f, 0.95f)
            mancha(Mostaza, 1.0f, 0.5f, 0.8f)
            mancha(Salvia, 0.25f, 1.0f, 0.9f)
        }
    } else {
        val img = remember(foto) { foto.asImageBitmap() }
        Image(img, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Ciruela.copy(alpha = 0.6f)))
    }
}

@Composable
private fun Inicio(error: String?, sacarFoto: () -> Unit, elegirFoto: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        AgujaConPuntos(Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(36.dp))
        Text(
            "¿Cuántos puntos hay en la aguja?",
            fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
            fontSize = 42.sp, lineHeight = 46.sp, color = Crema,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Sacá una foto de la aguja entera, desde arriba y sobre un fondo liso. Yo los cuento.",
            fontSize = 17.sp, lineHeight = 25.sp, color = Crema.copy(alpha = 0.78f),
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Aviso(error)
        }
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.fillMaxWidth().vidrio(RoundedCornerShape(32.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonPrincipal("Sacar foto", sacarFoto, Modifier.fillMaxWidth())
            BotonVidrio("Elegir de la galería", elegirFoto, Modifier.fillMaxWidth())
        }
    }
}

/** Dibujo de portada: la aguja con siete puntos montados y su puntito de conteo. */
@Composable
private fun AgujaConPuntos(modifier: Modifier) {
    Canvas(modifier) {
        val y = size.height * 0.42f
        val x0 = 14.dp.toPx()
        val x1 = size.width - 6.dp.toPx()
        val n = 7
        val inicio = x0 + 34.dp.toPx()
        val paso = (x1 - inicio - 30.dp.toPx()) / n
        val grosor = 8.dp.toPx()
        val alto = 40.dp.toPx()
        fun lazo(i: Int, adelante: Boolean, color: Color) {
            val cx = inicio + paso * (i + 0.5f)
            val ancho = paso * 0.78f
            drawArc(
                color, if (adelante) 0f else 180f, 180f, false,
                topLeft = Offset(cx - ancho / 2, y - alto / 2), size = Size(ancho, alto),
                style = Stroke(grosor, cap = StrokeCap.Round),
            )
        }
        for (i in 0 until n) lazo(i, adelante = false, color = RosaSombra)
        drawLine(Mostaza, Offset(x0, y), Offset(x1, y), 9.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Mostaza, 13.dp.toPx(), Offset(x0, y))
        for (i in 0 until n) lazo(i, adelante = true, color = Rosa)
        for (i in 0 until n) {
            val c = Offset(inicio + paso * (i + 0.5f), y + alto / 2 + 22.dp.toPx())
            drawCircle(Crema, 5.dp.toPx(), c)
        }
    }
}

@Composable
private fun Resultado(
    foto: Bitmap,
    candidatos: List<Candidato>?,
    tanda: Int,
    error: String?,
    sacarFoto: () -> Unit,
    elegirFoto: () -> Unit,
) {
    var sens by remember { mutableFloatStateOf(SENS_INICIAL) }
    var numeros by remember { mutableStateOf(true) }
    val sacados = remember(tanda) { mutableStateListOf<Int>() }      // detecciones que la usuaria borró
    val agregados = remember(tanda) { mutableStateListOf<Offset>() } // puntos que agregó a mano

    val conf = confianza(sens)
    val elegidos = remember(candidatos, conf) { candidatos?.let { filtrar(it, conf) } ?: emptyList() }
    val puntos = if (candidatos == null) emptyList() else ordenar(
        elegidos.filter { it !in sacados }.map { Punto(candidatos[it].cx, candidatos[it].cy, it) } +
            agregados.mapIndexed { k, o -> Punto(o.x, o.y, -1 - k) },
    )
    // tamaño del puntito: un tercio del punto de tejido típico de esta foto
    val radioFoto = remember(candidatos, conf) {
        val lados = elegidos.map { minOf(candidatos!![it].x1 - candidatos[it].x0, candidatos[it].y1 - candidatos[it].y0) }.sorted()
        if (lados.isEmpty()) minOf(foto.width, foto.height) / 60f else lados[lados.size / 2] * 0.3f
    }

    val enfoque = remember(tanda, candidatos) {
        if (candidatos == null || puntos.isEmpty()) null
        else Rect(puntos.minOf { it.x }, puntos.minOf { it.y }, puntos.maxOf { it.x }, puntos.maxOf { it.y }).inflate(radioFoto * 4)
    }

    // los puntos aparecen uno por uno, siguiendo la aguja, y el número sube con ellos
    val revelados = remember(tanda) { Animatable(0f) }
    var terminado by remember(tanda) { mutableStateOf(false) }
    LaunchedEffect(tanda, candidatos) {
        if (candidatos != null && puntos.isNotEmpty()) {
            revelados.animateTo(
                puntos.size.toFloat(),
                tween((puntos.size * 35).coerceIn(600, 2200), easing = LinearEasing),
            )
        }
        if (candidatos != null) terminado = true
    }
    val visibles = if (terminado) puntos.size.toFloat() else revelados.value
    val total = if (terminado) puntos.size else revelados.value.toInt()

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FotoConPuntos(
            foto, puntos, visibles, numeros, radioFoto, pasoTipico(puntos), tanda, enfoque,
            onToque = { p, radioToque ->
                val cerca = puntos.minByOrNull { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) }
                val d2 = cerca?.let { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) }
                if (cerca != null && d2!! <= radioToque * radioToque) {
                    if (cerca.indice >= 0) sacados.add(cerca.indice) else agregados.removeAt(-1 - cerca.indice)
                } else if (p.x in 0f..foto.width.toFloat() && p.y in 0f..foto.height.toFloat()) {
                    agregados.add(p)
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Column(
            Modifier.fillMaxWidth().vidrio(RoundedCornerShape(32.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (candidatos == null && error == null) {
                Text("Contando puntos…", fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = Crema)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rosa, trackColor = Crema.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
            } else if (error != null) {
                Aviso(error)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$total",
                        fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
                        fontSize = 84.sp, lineHeight = 84.sp, color = Crema,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (total == 1) "punto" else "puntos",
                        fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 24.sp,
                        color = Crema.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 14.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Pastilla(if (numeros) "Números sí" else "Números no", numeros) { numeros = !numeros }
                }
                val corregidos = sacados.size + agregados.size
                Text(
                    when {
                        puntos.isEmpty() -> "No encontré puntos. Subí la sensibilidad, o sacá la foto con la aguja entera y más de cerca."
                        corregidos > 0 -> "Corregiste $corregidos a mano. Tocá un punto para sacarlo o donde falta uno para sumarlo."
                        else -> "Tocá un punto para sacarlo o donde falta uno para sumarlo. Pellizcá la foto para acercarla."
                    },
                    fontSize = 14.sp, lineHeight = 20.sp, color = Crema.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sensibilidad", fontSize = 15.sp, color = Crema)
                    Spacer(Modifier.width(14.dp))
                    Slider(
                        value = sens, onValueChange = { sens = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Crema, activeTrackColor = Rosa,
                            inactiveTrackColor = Crema.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPrincipal("Otra foto", sacarFoto, Modifier.weight(1f))
                BotonVidrio("Galería", elegirFoto, Modifier.weight(1f))
            }
        }
    }
}

/** Cómo se ve la foto en pantalla, con el zoom y el arrastre de la usuaria. */
private class Vista(tam: Size, iw: Int, ih: Int, val zoom: Float, val d: Offset) {
    val s0 = minOf(tam.width / iw, tam.height / ih)
    val o0 = Offset((tam.width - iw * s0) / 2, (tam.height - ih * s0) / 2)
    val c = Offset(tam.width / 2, tam.height / 2)
    val escala get() = s0 * zoom
    fun aPantalla(x: Float, y: Float) = c + (o0 + Offset(x * s0, y * s0) - c) * zoom + d
    fun aFoto(p: Offset): Offset {
        val b = c + (p - c - d) / zoom
        return Offset((b.x - o0.x) / s0, (b.y - o0.y) / s0)
    }
}

@Composable
private fun FotoConPuntos(
    foto: Bitmap,
    puntos: List<Punto>,
    visibles: Float,
    numeros: Boolean,
    radioFoto: Float,
    pasoFoto: Float,
    tanda: Int,
    enfoque: Rect?,
    onToque: (Offset, Float) -> Unit,
    modifier: Modifier,
) {
    val img = remember(foto) { foto.asImageBitmap() }
    var tam by remember { mutableStateOf(Size.Zero) }
    var zoom by remember(foto) { mutableFloatStateOf(1f) }
    var desplaz by remember(foto) { mutableStateOf(Offset.Zero) }
    val medidor = rememberTextMeasurer()
    val estiloNumero = TextStyle(
        color = Crema, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        shadow = Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 4f),
    )
    // al terminar de contar, acerca la foto suavemente a los puntos (las fotos suelen tener mucho fondo).
    // El destino se recalcula en cada cuadro porque el marco cambia de tamaño cuando aparece el panel.
    LaunchedEffect(tanda) {
        val r = enfoque ?: return@LaunchedEffect
        val z0 = zoom
        val d0 = desplaz
        animate(0f, 1f, animationSpec = tween(700, easing = FastOutSlowInEasing)) { t, _ ->
            if (tam == Size.Zero) return@animate
            val v = Vista(tam, foto.width, foto.height, 1f, Offset.Zero)
            val zf = minOf(tam.width / (r.width * v.s0), tam.height / (r.height * v.s0)).coerceIn(1f, 4f)
            val d = (v.c - (v.o0 + Offset(r.center.x * v.s0, r.center.y * v.s0))) * zf
            val mx = (zf - 1) * tam.width / 2
            val my = (zf - 1) * tam.height / 2
            val df = Offset(d.x.coerceIn(-mx, mx), d.y.coerceIn(-my, my))
            zoom = z0 + (zf - z0) * t
            desplaz = d0 + (df - d0) * t
        }
    }
    val toque by rememberUpdatedState(onToque)
    val radioActual by rememberUpdatedState(radioFoto)

    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .onSizeChanged { tam = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(foto) {
                detectTransformGestures { centro, pan, factor, _ ->
                    val nuevo = (zoom * factor).coerceIn(1f, 6f)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    // acerca alrededor de los dedos
                    var d = centro - c - (centro - c - desplaz) * (nuevo / zoom) + pan
                    val mx = (nuevo - 1) * size.width / 2
                    val my = (nuevo - 1) * size.height / 2
                    d = Offset(d.x.coerceIn(-mx, mx), d.y.coerceIn(-my, my))
                    zoom = nuevo
                    desplaz = d
                }
            }
            .pointerInput(foto) {
                detectTapGestures(
                    onDoubleTap = { zoom = 1f; desplaz = Offset.Zero },
                    onTap = { p ->
                        val v = Vista(tam, foto.width, foto.height, zoom, desplaz)
                        // margen de toque: al menos 22 dp en pantalla, para el dedo
                        toque(v.aFoto(p), maxOf(radioActual * 1.6f, 22.dp.toPx() / v.escala))
                    },
                )
            }
            .semantics { contentDescription = "Foto con ${puntos.size} puntos marcados" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val v = Vista(size, foto.width, foto.height, zoom, desplaz)
            val a = v.aPantalla(0f, 0f)
            val b = v.aPantalla(foto.width.toFloat(), foto.height.toFloat())
            drawImage(
                img,
                dstOffset = IntOffset(a.x.roundToInt(), a.y.roundToInt()),
                dstSize = IntSize((b.x - a.x).roundToInt(), (b.y - a.y).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val r = (radioFoto * v.escala).coerceIn(3.dp.toPx(), 14.dp.toPx())
            val borde = 1.5.dp.toPx()
            puntos.forEachIndexed { i, p ->
                val t = (visibles - i).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val k = rebote(t)
                val c = v.aPantalla(p.x, p.y)
                drawCircle(Crema, r * k + borde, c)
                drawCircle(if (p.indice >= 0) Rosa else Salvia, r * k, c)
                // número solo si entra entre un punto y el siguiente
                if (numeros && t >= 1f && pasoFoto * v.escala >= 16.dp.toPx()) {
                    val txt = medidor.measure("${i + 1}", estiloNumero)
                    drawText(txt, topLeft = c + Offset(r * 0.7f, -r - txt.size.height * 0.75f))
                }
            }
        }
    }
}

/** Distancia típica entre un punto y el siguiente (la mediana), para saber si entran los números. */
private fun pasoTipico(p: List<Punto>): Float {
    if (p.size < 2) return Float.MAX_VALUE
    val d = p.zipWithNext { a, b -> kotlin.math.hypot(a.x - b.x, a.y - b.y) }.sorted()
    return d[d.size / 2]
}

/** Crece un poco de más y vuelve: el puntito "salta" al aparecer. */
private fun rebote(t: Float): Float {
    val c1 = 1.70158f
    val u = t - 1
    return 1 + (c1 + 1) * u * u * u + c1 * u * u
}

@Composable
private fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Rosa, RosaOscuro)))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Ciruela)
    }
}

@Composable
private fun BotonVidrio(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(56.dp)
            .vidrio(RoundedCornerShape(22.dp), 0.1f)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Crema)
    }
}

@Composable
private fun Pastilla(texto: String, activa: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(bottom = 16.dp)
            .vidrio(RoundedCornerShape(50), if (activa) 0.2f else 0.08f)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(texto, fontSize = 13.sp, color = Crema)
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        fontSize = 15.sp, lineHeight = 21.sp, color = Ciruela,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mostaza).padding(14.dp),
    )
}
