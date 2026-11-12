package com.contapuntos.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizador (el mismo de la app de Luana): lee version.json del último Release de GitHub,
 * baja el APK y lo instala encima de la versión actual con PackageInstaller, sin desinstalar.
 * Anda mientras cada APK se firme con la misma clave y suba el versionCode
 * (de las dos cosas se encarga 6_publicar_actualizacion.py).
 */
object Actualizador {
    // "latest/download" siempre apunta al Release más nuevo
    private const val URL_VERSION = "https://github.com/flitzfa/contapuntos-app/releases/latest/download/version.json"
    private const val ACCION_ESTADO = "com.contapuntos.app.ESTADO_ACTUALIZACION"

    data class Version(val codigo: Long, val nombre: String, val notas: String, val urlApk: String)

    private fun abrir(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true // GitHub redirige la descarga a su servidor de archivos
    }

    /** La versión nueva, o null si no hay una más nueva (o no hay internet). */
    suspend fun buscar(contexto: Context): Version? = withContext(Dispatchers.IO) {
        runCatching {
            val c = abrir(URL_VERSION)
            try {
                if (c.responseCode != 200) return@runCatching null
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val instalada = contexto.packageManager.getPackageInfo(contexto.packageName, 0).longVersionCode
                Version(
                    codigo = json.getLong("versionCode"),
                    nombre = json.getString("versionName"),
                    notas = json.optString("notes"),
                    urlApk = json.getString("apkUrl"),
                ).takeIf { it.codigo > instalada }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun bajar(contexto: Context, v: Version, progreso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val archivo = File(contexto.cacheDir, "actualizacion.apk")
        val c = abrir(v.urlApk)
        try {
            if (c.responseCode != 200) error("error de descarga (HTTP ${c.responseCode})")
            val total = c.contentLengthLong
            c.inputStream.use { entrada ->
                archivo.outputStream().use { salida ->
                    val buf = ByteArray(64 * 1024)
                    var leidos = 0L
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        salida.write(buf, 0, n)
                        leidos += n
                        if (total > 0) progreso(leidos.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        archivo
    }

    /** Le pasa el APK al instalador del sistema; Android muestra "¿Querés actualizar esta app?". */
    fun instalar(contexto: Context, apk: File, alFallar: (String) -> Unit) {
        val app = contexto.applicationContext
        val instalador = app.packageManager.packageInstaller
        val sesionId = instalador.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))

        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION ->
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                            ?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    PackageInstaller.STATUS_SUCCESS -> app.unregisterReceiver(this)
                    else -> {
                        app.unregisterReceiver(this)
                        alFallar(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "la instalación falló")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(app, receptor, IntentFilter(ACCION_ESTADO), ContextCompat.RECEIVER_NOT_EXPORTED)

        instalador.openSession(sesionId).use { sesion ->
            sesion.openWrite("actualizacion.apk", 0, apk.length()).use { salida ->
                apk.inputStream().use { it.copyTo(salida) }
                sesion.fsync(salida)
            }
            // el instalador tiene que poder completar los datos del aviso: MUTABLE en Android 12+
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val aviso = PendingIntent.getBroadcast(
                app, sesionId, Intent(ACCION_ESTADO).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            sesion.commit(aviso.intentSender)
        }
    }
}
