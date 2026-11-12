package com.contapuntos.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.nio.FloatBuffer

/** Una detección, en píxeles de la foto original. */
class Candidato(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val conf: Float) {
    val cx get() = (x0 + x1) / 2
    val cy get() = (y0 + y1) / 2
    val area get() = (x1 - x0) * (y1 - y0)
}

/** Un punto a dibujar. indice >= 0: lo detectó el modelo; < 0: lo agregó la usuaria a mano. */
data class Punto(val x: Float, val y: Float, val indice: Int)

/**
 * Lo mismo que detector.py en la PC: achica la foto a un cuadrado (rellenando con gris),
 * corre el modelo y después se descartan los duplicados con filtrar().
 */
class Detector(contexto: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val sesion: OrtSession = env.createSession(
        contexto.assets.open("detector_puntos.onnx").use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) },
    )
    private val entrada = sesion.inputNames.first()

    // tamaño con el que se exportó el modelo (960 hoy); se lee del modelo para no tener que tocar la app
    private val lado = (sesion.inputInfo[entrada]!!.info as TensorInfo).shape[2].toInt()

    /** Corre el modelo una sola vez. Devuelve todo lo que supera CONF_MIN, de mayor a menor confianza;
     *  la barra de sensibilidad después filtra esta lista sin volver a correr el modelo. */
    fun candidatos(foto: Bitmap): List<Candidato> {
        val w = foto.width
        val h = foto.height
        val r = minOf(lado.toFloat() / w, lado.toFloat() / h)
        val nw = Math.round(w * r)
        val nh = Math.round(h * r)
        val px = (lado - nw) / 2
        val py = (lado - nh) / 2

        val lienzo = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        Canvas(lienzo).apply {
            drawColor(android.graphics.Color.rgb(114, 114, 114))
            drawBitmap(Bitmap.createScaledBitmap(foto, nw, nh, true), px.toFloat(), py.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val area = lado * lado
        val pix = IntArray(area)
        lienzo.getPixels(pix, 0, lado, 0, 0, lado, lado)
        val buf = FloatBuffer.allocate(3 * area)
        for (i in 0 until area) {
            val p = pix[i]
            buf.put(i, ((p shr 16) and 255) / 255f)
            buf.put(area + i, ((p shr 8) and 255) / 255f)
            buf.put(2 * area + i, (p and 255) / 255f)
        }

        val salida = OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, lado.toLong(), lado.toLong())).use { t ->
            sesion.run(mapOf(entrada to t)).use { res ->
                val fb = (res[0] as OnnxTensor).floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }

        // salida [1, 5, n]: centro x, centro y, ancho, alto, confianza (en el cuadrado de entrada)
        val n = salida.size / 5
        val lista = ArrayList<Candidato>()
        for (i in 0 until n) {
            val s = salida[4 * n + i]
            if (s < CONF_MIN) continue
            val cx = (salida[i] - px) / r
            val cy = (salida[n + i] - py) / r
            val bw = salida[2 * n + i] / r
            val bh = salida[3 * n + i] / r
            lista += Candidato(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, s)
        }
        lista.sortByDescending { it.conf }
        return lista
    }

    companion object {
        const val CONF_MIN = 0.1f
    }
}

/** Se queda con los candidatos que superan [conf] y descarta los que se pisan más que [iou] con uno mejor.
 *  Igual que SOLAPAMIENTO en config.py. [c] tiene que venir ordenada de mayor a menor confianza. */
fun filtrar(c: List<Candidato>, conf: Float, iou: Float = 0.4f, max: Int = 1000): List<Int> {
    val quedan = ArrayList<Int>()
    for (i in c.indices) {
        val a = c[i]
        if (a.conf < conf) break
        var ok = true
        for (j in quedan) {
            val b = c[j]
            val iw = minOf(a.x1, b.x1) - maxOf(a.x0, b.x0)
            if (iw <= 0) continue
            val ih = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
            if (ih <= 0) continue
            val inter = iw * ih
            if (inter / (a.area + b.area - inter) > iou) {
                ok = false
                break
            }
        }
        if (ok) {
            quedan += i
            if (quedan.size >= max) break
        }
    }
    return quedan
}

/** Numera siguiendo la aguja: de izquierda a derecha, o de arriba a abajo si la aguja está parada. */
fun ordenar(p: List<Punto>): List<Punto> {
    if (p.size < 2) return p
    val ancho = p.maxOf { it.x } - p.minOf { it.x }
    val alto = p.maxOf { it.y } - p.minOf { it.y }
    return if (alto > ancho) p.sortedBy { it.y } else p.sortedBy { it.x }
}
