package com.jose.deepsign

import android.util.Log
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

data class ResultadoPalabra(val palabra: String, val distancia: Float)

/**
 * Reconoce palabras (movimientos completos) comparando la secuencia de la mano
 * con las capturas guardadas, usando DTW (Dynamic Time Warping).
 */
class ReconocedorPalabras(
    private val carpeta: File,
    private val base: ReconocedorLSB
) {

    private class Plantilla(val etiqueta: String, val frames: List<FloatArray>)

    @Volatile private var plantillas: List<Plantilla> = emptyList()
    @Volatile private var cargado = false
    private val lock = Any()

    companion object {
        private const val TAG = "DeepSign"
        const val MIN_FRAMES = 4        // las capturas de fotogramas son cortas
        const val SUBMUESTREO = 1       // sin submuestreo: ya vienen a ~10 fps
        const val UMBRAL_DTW = 0.40f    // distancia máxima aceptada
        const val RAZON_MAX = 0.80f     // la mejor debe ser < 80% de la segunda
    }

    /** Palabras permitidas (en minúsculas). Vacío = todas. */
    @Volatile var palabrasPermitidas: Set<String> = emptySet()
    /** Último resultado del DTW, para mostrarlo en pantalla. */
    @Volatile var diagnostico: String = ""

    fun estaListo(): Boolean = cargado

    /** Carga las capturas JSON. Llamar en un hilo de fondo. */
    fun precargar() {
        synchronized(lock) {
            if (cargado) return

            val lista = mutableListOf<Plantilla>()
            if (carpeta.isDirectory) {
                carpeta.listFiles()
                    ?.filter { it.isFile && it.extension.equals("json", true) }
                    ?.forEach { archivo ->
                        try {
                            val raiz = JSONObject(archivo.readText(Charsets.UTF_8))
                            val etiqueta = raiz.optString("label").trim()
                            if (etiqueta.isBlank()) return@forEach

                            val secuencia = extraerSecuencia(raiz)
                            if (secuencia.size >= MIN_FRAMES) {
                                lista.add(Plantilla(etiqueta, secuencia))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "JSON ignorado: ${archivo.name}", e)
                        }
                    }
            }

            plantillas = lista
            cargado = true
            Log.i(
                TAG,
                "Palabras: ${lista.size} plantillas de " +
                        "${lista.map { it.etiqueta }.distinct().size} palabras"
            )
        }
    }

    /** Vuelve a leer las capturas, por ejemplo después de guardar una nueva. */
    fun recargar() {
        synchronized(lock) {
            cargado = false
            plantillas = emptyList()
        }
        precargar()
    }

    /**
     * Recibe la secuencia de vectores de un movimiento completo.
     * Devuelve la palabra más parecida, o null si no hay coincidencia clara.
     */
    fun reconocer(secuencia: List<FloatArray>): ResultadoPalabra? {
        val lista = plantillas
        if (!cargado || lista.isEmpty()) return null

        val consulta = submuestrear(secuencia)
        if (consulta.size < MIN_FRAMES) return null

        val permitidas = palabrasPermitidas
        val candidatas = if (permitidas.isEmpty()) lista
        else lista.filter { it.etiqueta.lowercase() in permitidas }
        if (candidatas.isEmpty()) return null

        val mejorPorPalabra = HashMap<String, Float>()
        for (p in candidatas) {
            val d = dtw(consulta, p.frames)
            val previo = mejorPorPalabra[p.etiqueta]
            if (previo == null || d < previo) {
                mejorPorPalabra[p.etiqueta] = d
            }
        }

        val orden = mejorPorPalabra.entries.sortedBy { it.value }
        val mejor = orden.firstOrNull() ?: return null
        val segunda = orden.getOrNull(1)?.value

        val textoDtw = "DTW mejor=${mejor.key} %.2f segunda=%s".format(
            mejor.value,
            segunda?.let { "%.2f".format(it) } ?: "-"
        )
        Log.d(TAG, textoDtw)
        diagnostico = textoDtw

        if (mejor.value > UMBRAL_DTW) return null
        if (segunda != null && mejor.value > segunda * RAZON_MAX) return null

        return ResultadoPalabra(mejor.key, mejor.value)
    }

    private fun extraerSecuencia(raiz: JSONObject): List<FloatArray> {
        val espejo = raiz.optBoolean("camaraFrontal", false)
        val frames = raiz.optJSONArray("frames") ?: return emptyList()

        val vectores = ArrayList<FloatArray>()
        for (i in 0 until frames.length()) {
            val frame = frames.optJSONObject(i) ?: continue
            vectorDeFrame(frame, espejo)?.let { vectores.add(it) }
        }
        return vectores
    }

    private fun vectorDeFrame(frame: JSONObject, espejo: Boolean): FloatArray? {
        val manos = frame.optJSONArray("hands") ?: return null
        if (manos.length() == 0) return null

        val puntos = manos.optJSONArray(0) ?: return null
        if (puntos.length() != ReconocedorLSB.PUNTOS) return null

        val xs = FloatArray(ReconocedorLSB.PUNTOS)
        val ys = FloatArray(ReconocedorLSB.PUNTOS)
        for (p in 0 until ReconocedorLSB.PUNTOS) {
            val punto = puntos.optJSONObject(p) ?: return null
            xs[p] = punto.optDouble("x", Double.NaN).toFloat()
            ys[p] = punto.optDouble("y", Double.NaN).toFloat()
        }
        return base.vectorDesde(xs, ys, espejo)
    }

    private fun submuestrear(lista: List<FloatArray>): List<FloatArray> =
        lista.filterIndexed { i, _ -> i % SUBMUESTREO == 0 }

    private fun distanciaFrames(a: FloatArray, b: FloatArray): Float {
        var suma = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            suma += d * d
        }
        return sqrt(suma / a.size)
    }

    /** DTW normalizado: coste medio por paso alineado. */
    private fun dtw(a: List<FloatArray>, b: List<FloatArray>): Float {
        val n = a.size
        val m = b.size
        var previa = FloatArray(m + 1) { Float.MAX_VALUE }
        var actual = FloatArray(m + 1) { Float.MAX_VALUE }
        previa[0] = 0f

        for (i in 1..n) {
            actual[0] = Float.MAX_VALUE
            for (j in 1..m) {
                val costo = distanciaFrames(a[i - 1], b[j - 1])
                actual[j] = costo + minOf(previa[j], actual[j - 1], previa[j - 1])
            }
            val tmp = previa
            previa = actual
            actual = tmp
        }
        return previa[m] / (n + m)
    }
}

/**
 * Agrupa frames en movimientos: un movimiento empieza cuando aparece la mano
 * y termina cuando la mano desaparece durante varios frames, o al pulsar Detener.
 */
class SegmentadorMovimiento(
    private val framesSinManoParaCortar: Int = 20,
    private val maxFrames: Int = 300
) {
    companion object {
        // Desplazamiento mínimo entre frames para considerar que la mano se mueve.
        const val UMBRAL_MOVIMIENTO = 0.03f
    }

    private val buffer = ArrayList<FloatArray>()
    private var framesSinMano = 0

    /** Devuelve el movimiento completo cuando termina; si no, null. */
    fun agregar(vector: FloatArray?): List<FloatArray>? {
        if (vector != null) {
            framesSinMano = 0
            buffer.add(vector)
            return if (buffer.size >= maxFrames) cortar() else null
        }

        if (buffer.isEmpty()) return null
        framesSinMano++
        return if (framesSinMano >= framesSinManoParaCortar) cortar() else null
    }

    /** Cierra el movimiento en curso, si hay alguno (se usa al pulsar Detener). */
    fun vaciar(): List<FloatArray>? {
        if (buffer.isEmpty()) return null
        return cortar()
    }

    fun reiniciar() {
        buffer.clear()
        framesSinMano = 0
    }

    private fun cortar(): List<FloatArray> {
        val copia = recortarReposo(buffer.toList())
        reiniciar()
        return copia
    }

    /** Quita los frames casi quietos al inicio y al final del movimiento. */
    private fun recortarReposo(frames: List<FloatArray>): List<FloatArray> {
        if (frames.size < 3) return frames

        val movimiento = FloatArray(frames.size)
        for (i in 1 until frames.size) {
            movimiento[i] = distancia(frames[i], frames[i - 1])
        }

        val inicio = movimiento.indexOfFirst { it > UMBRAL_MOVIMIENTO }
        if (inicio == -1) return emptyList()
        val fin = movimiento.indexOfLast { it > UMBRAL_MOVIMIENTO }

        val desde = maxOf(0, inicio - 1)
        val hasta = minOf(frames.size - 1, fin + 1)
        return frames.subList(desde, hasta + 1).toList()
    }

    private fun distancia(a: FloatArray, b: FloatArray): Float {
        var suma = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            suma += d * d
        }
        return sqrt(suma / a.size)
    }
}