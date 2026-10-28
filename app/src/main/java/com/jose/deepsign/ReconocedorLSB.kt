package com.jose.deepsign

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

data class Resultado(val etiqueta: String, val distancia: Float)

class ReconocedorLSB(
    private val carpeta: File,
    private val context: Context
) {

    private class Muestra(
        val etiqueta: String,
        val mano: String?,      // "Left", "Right" o null (coincide con cualquiera)
        val vector: FloatArray  // 42 valores: x0,y0,x1,y1,... normalizados
    )

    private class Acumulador {
        val xs = FloatArray(PUNTOS)
        val ys = FloatArray(PUNTOS)
        val visto = BooleanArray(PUNTOS)
        fun completo() = visto.all { it }
    }

    @Volatile private var muestras: List<Muestra> = emptyList()
    @Volatile private var cargado = false
    private val lockCarga = Any()

    companion object {
        const val PUNTOS = 21
        const val DIMENSIONES = PUNTOS * 2
        const val ARCHIVO_CATALOGO = "catalogo_fotogramas_revisado_renombrado.csv"
        const val ARCHIVO_COORDENADAS = "coordenadas_manos_separadas_renombradas-1.csv"
        private const val TAG = "DeepSign"

        // Valores iniciales: calibrar con capturas de prueba reales.
        const val UMBRAL_RMS = 0.15f   // distancia máxima aceptada (RMS por coordenada)
        const val MARGEN_MIN = 0.02f   // diferencia mínima entre la 1.ª y la 2.ª clase
    }

    /** Carga las muestras. Debe llamarse en un hilo de fondo, nunca en el hilo principal. */
    fun precargar() {
        synchronized(lockCarga) {
            if (cargado) return
            val lista = mutableListOf<Muestra>()
            cargarCapturasJson(lista)
            cargarCatalogo(lista)
            muestras = lista
            cargado = true
            Log.i(TAG, "Reconocedor listo: ${lista.size} muestras en total")
        }
    }

    fun estaListo(): Boolean = cargado

    /**
     * Convierte 21 puntos (coordenadas normalizadas de MediaPipe) en un vector invariante
     * a posición y tamaño: resta la muñeca y divide por la distancia muñeca → base del dedo medio.
     * Si espejo es true, invierte x (cámara frontal) para coincidir con las muestras de cámara trasera.
     */
    fun vectorDesde(xs: FloatArray, ys: FloatArray, espejo: Boolean = false): FloatArray? {
        if (xs.size != PUNTOS || ys.size != PUNTOS) return null

        val x = FloatArray(PUNTOS) { i -> if (espejo) 1f - xs[i] else xs[i] }
        val y = ys

        for (i in 0 until PUNTOS) {
            if (!x[i].isFinite() || !y[i].isFinite()) return null
        }

        val mx = x[0]
        val my = y[0]
        val dx = x[9] - mx
        val dy = y[9] - my
        val escala = sqrt(dx * dx + dy * dy)
        if (!escala.isFinite() || escala < 1e-5f) return null

        val salida = FloatArray(DIMENSIONES)
        for (i in 0 until PUNTOS) {
            salida[i * 2] = (x[i] - mx) / escala
            salida[i * 2 + 1] = (y[i] - my) / escala
        }
        return salida
    }

    /**
     * Devuelve la etiqueta de la clase más cercana, o null si no hay coincidencia
     * suficientemente cercana o si no es claramente mejor que la segunda clase.
     */
    fun reconocer(vector: FloatArray, mano: String?): Resultado? {
        val lista = muestras
        if (!cargado || lista.isEmpty() || vector.size != DIMENSIONES) return null

        // Distancia mínima por clase, no por muestra individual.
        val mejorPorClase = HashMap<String, Float>()
        for (m in lista) {
            if (mano != null && m.mano != null && m.mano != mano) continue
            val d = distanciaRms(vector, m.vector)
            val previo = mejorPorClase[m.etiqueta]
            if (previo == null || d < previo) {
                mejorPorClase[m.etiqueta] = d
            }
        }
        if (mejorPorClase.isEmpty()) return null

        var mejorEtiqueta = ""
        var mejor = Float.MAX_VALUE
        var segunda = Float.MAX_VALUE
        for ((etiqueta, d) in mejorPorClase) {
            if (d < mejor) {
                segunda = mejor
                mejor = d
                mejorEtiqueta = etiqueta
            } else if (d < segunda) {
                segunda = d
            }
        }

        if (mejor > UMBRAL_RMS) return null
        if (segunda - mejor < MARGEN_MIN) return null
        return Resultado(mejorEtiqueta, mejor)
    }

    private fun distanciaRms(a: FloatArray, b: FloatArray): Float {
        var suma = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            suma += d * d
        }
        return sqrt(suma / a.size)
    }

    private fun normalizarRuta(ruta: String): String =
        ruta.trim()
            .removePrefix("\uFEFF")
            .replace('\\', '/')
            .lowercase(Locale.ROOT)

    private fun normalizarMano(texto: String?): String? {
        val t = texto?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when {
            t.startsWith("left") || t.startsWith("izq") -> "Left"
            t.startsWith("right") || t.startsWith("der") -> "Right"
            else -> null
        }
    }

    private fun aFloat(texto: String): Float? =
        texto.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    /** Lee las capturas JSON que guarda DeepSign. */
    private fun cargarCapturasJson(destino: MutableList<Muestra>) {
        if (!carpeta.isDirectory) return

        var total = 0
        carpeta.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", true) }
            ?.forEach { archivo ->
                try {
                    val raiz = JSONObject(archivo.readText(Charsets.UTF_8))
                    val etiqueta = raiz.optString("label").trim()
                    if (etiqueta.isBlank()) return@forEach

                    val espejo = raiz.optBoolean("camaraFrontal", false)
                    val frames = raiz.optJSONArray("frames") ?: return@forEach

                    for (i in 0 until frames.length()) {
                        val frame = frames.optJSONObject(i) ?: continue
                        val manos = frame.optJSONArray("hands") ?: continue
                        if (manos.length() == 0) continue

                        val puntos = manos.optJSONArray(0) ?: continue
                        val mano = normalizarMano(
                            frame.optJSONArray("handedness")?.optString(0)
                        )

                        val xs = FloatArray(PUNTOS)
                        val ys = FloatArray(PUNTOS)
                        var valido = puntos.length() == PUNTOS
                        if (valido) {
                            for (p in 0 until PUNTOS) {
                                val punto = puntos.optJSONObject(p)
                                if (punto == null) { valido = false; break }
                                xs[p] = punto.optDouble("x", Double.NaN).toFloat()
                                ys[p] = punto.optDouble("y", Double.NaN).toFloat()
                            }
                        }
                        if (!valido) continue

                        val vector = vectorDesde(xs, ys, espejo) ?: continue
                        destino.add(Muestra(etiqueta, mano, vector))
                        total++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JSON ignorado: ${archivo.name}", e)
                }
            }

        Log.i(TAG, "Muestras desde capturas JSON: $total")
    }

    /**
     * Lee los dos CSV de assets. Se asume que las coordenadas del CSV
     * provienen de cámara trasera (sin espejo).
     */
    private fun cargarCatalogo(destino: MutableList<Muestra>) {
        // 1. Ruta de fotograma → etiqueta.
        val etiquetas = HashMap<String, String>()
        try {
            context.assets.open(ARCHIVO_CATALOGO)
                .bufferedReader(Charsets.UTF_8)
                .useLines { lineas ->
                    lineas.drop(1).forEach { linea ->
                        val c = linea.split(';')
                        if (c.size < 4) return@forEach
                        val ruta = normalizarRuta(c[1])
                        val etiqueta = c[3].trim()
                        if (ruta.isNotBlank() && etiqueta.isNotBlank()) {
                            etiquetas[ruta] = etiqueta
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer el CSV del catálogo", e)
            return
        }

        // 2. Coordenadas agrupadas por fotograma y mano.
        val acumuladores = HashMap<String, Acumulador>()
        var filasDescartadas = 0
        try {
            context.assets.open(ARCHIVO_COORDENADAS)
                .bufferedReader(Charsets.UTF_8)
                .useLines { lineas ->
                    lineas.drop(1).forEach { linea ->
                        val c = linea.split(';')
                        if (c.size < 5) { filasDescartadas++; return@forEach }

                        val ruta = normalizarRuta(c[0])
                        if (!etiquetas.containsKey(ruta)) return@forEach

                        val manoRaw = c[1].trim()
                        val indice = c[2].trim().toIntOrNull()
                        val x = aFloat(c[3])
                        val y = aFloat(c[4])

                        if (indice == null || indice !in 0 until PUNTOS || x == null || y == null) {
                            filasDescartadas++
                            return@forEach
                        }

                        val acc = acumuladores.getOrPut("$ruta|$manoRaw") { Acumulador() }
                        acc.xs[indice] = x
                        acc.ys[indice] = y
                        acc.visto[indice] = true
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer el CSV de coordenadas", e)
            return
        }

        // 3. Construye una muestra por cada fotograma completo.
        var total = 0
        for ((clave, acc) in acumuladores) {
            if (!acc.completo()) { filasDescartadas++; continue }

            val ruta = clave.substringBeforeLast('|')
            val manoRaw = clave.substringAfterLast('|')
            val etiqueta = etiquetas[ruta] ?: continue

            val vector = vectorDesde(acc.xs, acc.ys) ?: continue
            destino.add(Muestra(etiqueta, normalizarMano(manoRaw), vector))
            total++
        }

        Log.i(
            TAG,
            "Muestras desde catálogo CSV: $total; etiquetas: ${etiquetas.size}; " +
                    "filas descartadas: $filasDescartadas"
        )
    }
}

/**
 * Exige que la misma etiqueta aparezca en varios fotogramas seguidos
 * antes de mostrarla, para evitar parpadeos entre señas parecidas.
 */
class EstabilizadorSena(
    private val ventana: Int = 12,
    private val minimo: Int = 8
) {
    private val historial = ArrayDeque<String?>()

    fun agregar(resultado: String?): String? {
        historial.addLast(resultado)
        while (historial.size > ventana) historial.removeFirst()
        if (historial.size < minimo) return null

        val conteo = HashMap<String, Int>()
        for (r in historial) {
            if (r != null) conteo[r] = (conteo[r] ?: 0) + 1
        }

        val mejor = conteo.maxByOrNull { it.value } ?: return null
        return if (mejor.value >= minimo) mejor.key else null
    }

    fun reiniciar() {
        historial.clear()
    }
}