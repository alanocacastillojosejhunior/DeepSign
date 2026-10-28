package com.jose.deepsign

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject

class TraductorActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlayMano: HandOverlayView
    private lateinit var estadoMano: TextView
    private lateinit var txtPalabra: TextView
    private lateinit var botonIniciar: Button
    private lateinit var botonDetener: Button
    private lateinit var botonCamara: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cargaExecutor: ExecutorService
    private lateinit var palabraExecutor: ExecutorService
    private lateinit var tts: TextToSpeech

    private var handLandmarker: HandLandmarker? = null
    private var cuerpoDetector: CuerpoDetector? = null
    private lateinit var reconocedorLSB: ReconocedorLSB
    private lateinit var textoFrase: TextView
    private lateinit var reconocedorPalabras: ReconocedorPalabras
    private val segmentador = SegmentadorMovimiento()
    private var cameraProvider: ProcessCameraProvider? = null

    private val palabrasDichas = mutableListOf<String>()
    private val traduciendo = AtomicBoolean(false)
    private var fraseFinal = ""
    private lateinit var botonGrabar: Button
    private val grabando = AtomicBoolean(false)
    private val framesGrabados = mutableListOf<FrameGrabado>()
    private val DURACION_GRABACION_MS = 6000L

    @Volatile
    private var ultimaManoMs = 0L

    @Volatile
    private var huboMano = false

    companion object {
        // Si no se ve la mano durante este tiempo, se cierra la frase solo.
        const val TIEMPO_SIN_MANO_MS = 1500L

        // Un movimiento con menos fotogramas que esto se ignora (ruido).
        const val MIN_FRAMES_PALABRA = 4
    }

    // Claves SIN tildes, SIN signos y en minúsculas.
    private val frasesCorregidas = mapOf(
        "hola como estas" to "Hola, ¿cómo estás?",
        "hola buenas tardes" to "Hola, buenas tardes.",
        "hola buenos dias" to "Hola, buenos días.",
        "hola buenas noches" to "Hola, buenas noches.",
        "como te llamas" to "¿Cómo te llamas?",
        "mucho gusto" to "Mucho gusto.",
        "por favor" to "Por favor.",
        "gracias" to "Gracias.",
        "adios" to "Adiós."
    )

    // Preferencia del usuario: true = frontal, false = trasera.
    @Volatile
    private var usarFrontal = false

    // Cámara activa en este momento.
    @Volatile
    private var camaraFrontal = false

    @Volatile
    private var mensajeTemporalHasta = 0L
    private var ultimoTimestampMs = -1L
    private var diagnosticoRegistrado = false

    private val permisoCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            iniciarCamara()
        } else {
            estadoMano.text = "Se necesita permiso para la cámara"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_traductor)

        tts = TextToSpeech(this, this)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cargaExecutor = Executors.newSingleThreadExecutor()
        palabraExecutor = Executors.newSingleThreadExecutor()

        reconocedorLSB = ReconocedorLSB(File(filesDir, "capturas_lsb"), this)
        reconocedorPalabras = ReconocedorPalabras(
            File(filesDir, "capturas_lsb"), reconocedorLSB
        )

        // Palabras habilitadas. emptySet() = todas
        reconocedorPalabras.palabrasPermitidas = emptySet()

        cargaExecutor.execute {
            reconocedorLSB.precargar()
            reconocedorPalabras.precargar()
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        val contenedor = findViewById<FrameLayout>(R.id.contenedorCamara)
        txtPalabra = findViewById(R.id.txtPalabra)

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        overlayMano = HandOverlayView(this)

        estadoMano = TextView(this).apply {
            text = "Preparando detector de manos..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        contenedor.removeAllViews()
        contenedor.addView(previewView)
        contenedor.addView(
            overlayMano, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        contenedor.addView(estadoMano)

        textoFrase = TextView(this).apply {
            text = ""
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                bottomMargin = dp(60)
            }
            // Toque corto: lee la frase completa
            setOnClickListener {
                val frase = if (fraseFinal.isNotBlank()) fraseFinal
                else palabrasDichas.joinToString(" ")
                if (frase.isNotBlank()) {
                    tts.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "frase")
                }
            }
            // Toque largo: borra la frase
            setOnLongClickListener {
                palabrasDichas.clear()
                fraseFinal = ""
                textoFrase.text = ""
                txtPalabra.text = "—"
                true
            }
        }
        contenedor.addView(textoFrase)

        val verdeDeepSign = Color.rgb(31, 61, 46)
        val coralDeepSign = Color.rgb(232, 130, 92)
        val azulDeepSign = Color.rgb(60, 90, 120)

        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        botonIniciar = crearBoton("▶ Iniciar", verdeDeepSign) { iniciarTraduccion() }
        botonDetener = crearBoton("■ Detener", coralDeepSign) { detenerTraduccion() }
        botonCamara = crearBoton("⟲ Cámara", azulDeepSign) { cambiarCamara() }
        botonGrabar = crearBoton("● Grabar", Color.rgb(150, 50, 60)) { pedirNombreYGrabar() }

        botonDetener.isEnabled = false
        botonDetener.alpha = 0.55f

        val margen = dp(4)
        listOf(botonIniciar, botonDetener, botonCamara, botonGrabar).forEach { boton ->
            controles.addView(
                boton,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = margen
                    marginEnd = margen
                })
        }

        // Los botones se colocan dentro de la tarjeta de abajo.
        val tarjeta = findViewById<LinearLayout>(R.id.tarjetaResultado)
        tarjeta.addView(
            controles, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            })

        try {
            val opcionesBase =
                BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()

            val opcionesDetector =
                HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(opcionesBase)
                    .setRunningMode(RunningMode.VIDEO).setNumHands(2).build()

            handLandmarker = HandLandmarker.createFromOptions(this, opcionesDetector)
            estadoMano.text = "Detector listo. Pulsa Iniciar."
        } catch (e: Exception) {
            estadoMano.text = "No se pudo cargar el modelo de manos"
            Log.e("DeepSign", "Error al cargar MediaPipe", e)
            return
        }
        //cuerpoDetector = CuerpoDetector(this)
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            previewView.post { iniciarCamara() }
        } else {
            permisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun crearBoton(texto: String, color: Int, accion: () -> Unit): Button =
        Button(this).apply {
            text = texto
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(6), dp(7), dp(6), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(color)
            }
            elevation = dp(2).toFloat()
            minHeight = dp(52)
            setOnClickListener { accion() }
        }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es")
        }
    }

    private fun iniciarTraduccion() {
        // Empieza una frase nueva
        palabrasDichas.clear()
        fraseFinal = ""
        textoFrase.text = ""
        txtPalabra.text = "…"
        huboMano = false
        ultimaManoMs = System.currentTimeMillis()

        traduciendo.set(true)
        cameraExecutor.execute { segmentador.reiniciar() }

        botonIniciar.isEnabled = false
        botonIniciar.alpha = 0.55f
        botonDetener.isEnabled = true
        botonDetener.alpha = 1f

        mostrarTemporal("Seguimiento de mano activo")
    }

    private fun detenerTraduccion() {
        // Si ya estaba detenido, no hace nada (evita ejecutarse dos veces)
        if (!traduciendo.getAndSet(false)) return

        botonIniciar.isEnabled = true
        botonIniciar.alpha = 1f
        botonDetener.isEnabled = false
        botonDetener.alpha = 0.55f

        cameraExecutor.execute {
            // Cierra el último movimiento y lo manda a reconocer
            segmentador.vaciar()?.let { reconocerMovimiento(it) }

            // Esta tarea se ejecuta DESPUÉS de reconocer la última palabra
            palabraExecutor.execute {
                runOnUiThread { mostrarFraseFinal() }
            }
        }

        mostrarTemporal("Seguimiento detenido")
    }

    private fun cambiarCamara() {
        if (traduciendo.get()) detenerTraduccion()

        usarFrontal = !usarFrontal
        cameraExecutor.execute { segmentador.reiniciar() }

        iniciarCamara()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)

        futuro.addListener({
            try {
                val proveedor = futuro.get()
                cameraProvider = proveedor

                val tieneFrontal = proveedor.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val tieneTrasera = proveedor.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

                val esFrontal = (usarFrontal && tieneFrontal) || !tieneTrasera
                camaraFrontal = esFrontal

                val selector = if (esFrontal) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val rotacion = previewView.display?.rotation ?: Surface.ROTATION_0

                val vistaPrevia = Preview.Builder().setTargetRotation(rotacion).build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotacion)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()

                analisis.setAnalyzer(cameraExecutor) { imagen -> analizarFrame(imagen) }


                val grupo = UseCaseGroup.Builder().addUseCase(vistaPrevia).addUseCase(analisis)
                    .apply { previewView.viewPort?.let { setViewPort(it) } }.build()

                proveedor.unbindAll()
                proveedor.bindToLifecycle(this, selector, grupo)
            } catch (e: Exception) {
                estadoMano.text = "No se pudo iniciar la cámara"
                Log.e("DeepSign", "Error de cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Se ejecuta en el hilo de cámara. */


    private fun analizarFrame(imagen: ImageProxy) {
        try {
            val detector = handLandmarker ?: return
            val bitmapVertical = imagenABitmapVertical(imagen) ?: return

            val anchoFrame = bitmapVertical.width
            val altoFrame = bitmapVertical.height

            val mpImagen = BitmapImageBuilder(bitmapVertical).build()
            val timestampMs = maxOf(
                imagen.imageInfo.timestamp / 1_000_000L, ultimoTimestampMs + 1
            )
            ultimoTimestampMs = timestampMs

            val resultado = try {
                detector.detectForVideo(mpImagen, timestampMs)
            } finally {
                mpImagen.close()
            }

            // Cuerpo: se detecta antes de liberar la imagen.
            val puntosCuerpo = cuerpoDetector?.detectar(bitmapVertical, timestampMs)

            bitmapVertical.recycle()

            val manos: List<List<PointF>> = resultado.landmarks().map { mano ->
                mano.map { PointF(it.x(), it.y()) }
            }

            // Grabación: se guarda aunque no estés en Iniciar.
            if (grabando.get() && (resultado.landmarks().isNotEmpty() || puntosCuerpo != null)) {
                val manosGrabadas = resultado.landmarks().take(2).map { mano ->
                    mano.map { Pair(it.x(), it.y()) }
                }
                val cuerpoGrabado = puntosCuerpo?.map { Pair(it.x, it.y) }
                synchronized(framesGrabados) {
                    framesGrabados.add(FrameGrabado(manosGrabadas, cuerpoGrabado))
                }
            }

            if (traduciendo.get()) {
                val vectorFrame = resultado.landmarks().firstOrNull()?.let { lm ->
                    val xs = FloatArray(ReconocedorLSB.PUNTOS) { lm[it].x() }
                    val ys = FloatArray(ReconocedorLSB.PUNTOS) { lm[it].y() }
                    reconocedorLSB.vectorDesde(xs, ys, camaraFrontal)
                }

                segmentador.agregar(vectorFrame)?.let { reconocerMovimiento(it) }

                val ahora = System.currentTimeMillis()
                if (manos.isNotEmpty()) {
                    huboMano = true
                    ultimaManoMs = ahora
                } else if (huboMano && ahora - ultimaManoMs > TIEMPO_SIN_MANO_MS) {
                    runOnUiThread { detenerTraduccion() }
                }
            }

            runOnUiThread {
                overlayMano.actualizar(manos, anchoFrame, altoFrame, camaraFrontal)
                overlayMano.actualizarCuerpo(puntosCuerpo)

                if (traduciendo.get() && System.currentTimeMillis() >= mensajeTemporalHasta) {
                    estadoMano.text = when {
                        manos.isEmpty() -> "Muestra tu mano frente a la cámara"
                        else -> "Mano detectada: 21 puntos en seguimiento"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DeepSign", "Error analizando imagen", e)
        } finally {
            imagen.close()
        }
    }

    private fun reconocerMovimiento(movimiento: List<FloatArray>) {
        if (!reconocedorPalabras.estaListo()) {
            runOnUiThread { mostrarTemporal("Las plantillas aún están cargando") }
            return
        }
        if (movimiento.size < 4) {
            runOnUiThread { mostrarTemporal("Movimiento muy corto: ${movimiento.size} fotogramas") }
            return
        }

        palabraExecutor.execute {
            val hallazgo = reconocedorPalabras.reconocer(movimiento)

            runOnUiThread {
                if (hallazgo == null) {
                    mostrarTemporal("Sin coincidencia: ${reconocedorPalabras.diagnostico} (${movimiento.size} fot.)")
                } else if (hallazgo.palabra.equals(palabrasDichas.lastOrNull(), ignoreCase = true)) {
                    mostrarTemporal("Repetida, se ignora: ${hallazgo.palabra}")
                } else {
                    palabrasDichas.add(hallazgo.palabra)
                    textoFrase.text = palabrasDichas.joinToString(" ")
                    txtPalabra.text = hallazgo.palabra
                    Almacen.agregarHistorial(this, hallazgo.palabra, "Gesto")
                    mostrarTemporal("Seña reconocida: ${hallazgo.palabra}")
                }
            }
        }
    }


    /** Muestra un mensaje en la barra de estado durante 2 segundos. */
    /** Une las palabras, las corrige, las muestra y las lee en voz alta. */
    private fun mostrarFraseFinal() {
        if (palabrasDichas.isEmpty()) {
            fraseFinal = ""
            txtPalabra.text = "—"
            mostrarTemporal("No se reconoció ninguna seña")
            return
        }

        fraseFinal = corregirFrase(palabrasDichas)
        txtPalabra.text = fraseFinal
        textoFrase.text = fraseFinal
        tts.speak(fraseFinal, TextToSpeech.QUEUE_FLUSH, null, "frase")
    }

    /** Busca la frase en el diccionario; si no está, solo pone la primera letra en mayúscula. */
    private fun corregirFrase(palabras: List<String>): String {
        val clave = normalizar(palabras.joinToString(" "))
        frasesCorregidas[clave]?.let { return it }

        val texto = palabras.joinToString(" ").trim().lowercase(Locale.ROOT)
        return texto.replaceFirstChar { it.uppercase() }
    }

    /** Minúsculas, sin tildes y sin signos: "¿Cómo estás?" -> "como estas". */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun pedirNombreYGrabar() {
        if (traduciendo.get()) detenerTraduccion()

        val campo = EditText(this).apply {
            hint = "Ejemplo: hola"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("¿Qué seña vas a grabar?")
            .setView(campo)
            .setPositiveButton("Grabar") { _, _ ->
                val etiqueta = campo.text.toString().trim().lowercase(Locale.ROOT)
                if (etiqueta.isNotBlank()) iniciarGrabacion(etiqueta)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarGrabacion(etiqueta: String) {
        botonGrabar.isEnabled = false
        botonGrabar.alpha = 0.55f
        val handler = Handler(Looper.getMainLooper())

        fun cuentaAtras(n: Int) {
            if (n > 0) {
                mostrarTemporal("Prepárate… $n")
                handler.postDelayed({ cuentaAtras(n - 1) }, 1000)
            } else {
                synchronized(framesGrabados) { framesGrabados.clear() }
                grabando.set(true)
                mostrarTemporal("¡GRABANDO! Haz la seña")
                handler.postDelayed({
                    grabando.set(false)
                    guardarCaptura(etiqueta)
                    botonGrabar.isEnabled = true
                    botonGrabar.alpha = 1f
                }, DURACION_GRABACION_MS)
            }
        }
        cuentaAtras(3)
    }

    private fun guardarCaptura(etiqueta: String) {
        val copia = synchronized(framesGrabados) {
            val c = framesGrabados.toList()
            framesGrabados.clear()
            c
        }
        if (copia.size < 4) {
            mostrarTemporal("Grabación muy corta (${copia.size} fotogramas). ¿Se veía tu mano?")
            return
        }

        val frames = JSONArray()
        for (f in copia) {
            val arrManos = JSONArray()
            for (mano in f.manos) {
                val arrPuntos = JSONArray()
                for (p in mano) {
                    arrPuntos.put(
                        JSONObject().put("x", p.first.toDouble()).put("y", p.second.toDouble())
                    )
                }
                arrManos.put(arrPuntos)
            }
            val frame = JSONObject().put("hands", arrManos)

            f.cuerpo?.let { cuerpo ->
                val arrCuerpo = JSONArray()
                for (p in cuerpo) {
                    arrCuerpo.put(
                        JSONObject().put("x", p.first.toDouble()).put("y", p.second.toDouble())
                    )
                }
                frame.put("pose", arrCuerpo)
            }
            frames.put(frame)
        }

        val raiz = JSONObject()
            .put("label", etiqueta)
            .put("camaraFrontal", camaraFrontal)
            .put("frames", frames)

        val nombreSeguro = etiqueta.replace(Regex("[^a-z0-9]+"), "_")
        val carpeta = File(filesDir, "capturas_lsb")

        cargaExecutor.execute {
            carpeta.mkdirs()
            File(carpeta, "${nombreSeguro}_${System.currentTimeMillis()}.json")
                .writeText(raiz.toString())
            reconocedorPalabras.recargar()
            runOnUiThread {
                mostrarTemporal("Guardada: $etiqueta (${copia.size} fotogramas)")
            }
        }
    }
    private fun mostrarTemporal(mensaje: String) {
        estadoMano.text = mensaje
        mensajeTemporalHasta = System.currentTimeMillis() + 6000
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density + 0.5f).toInt()

    private fun imagenABitmapVertical(imagen: ImageProxy): Bitmap? {
        val plano = imagen.planes.firstOrNull() ?: return null

        if (plano.pixelStride != 4) {
            Log.w("DeepSign", "Formato inesperado: pixelStride=${plano.pixelStride}")
            return null
        }

        val anchoBuffer = plano.rowStride / plano.pixelStride

        val crudo = Bitmap.createBitmap(anchoBuffer, imagen.height, Bitmap.Config.ARGB_8888)

        val buffer = plano.buffer
        buffer.rewind()
        crudo.copyPixelsFromBuffer(buffer)

        val recorte = Rect(imagen.cropRect)
        if (!recorte.intersect(0, 0, imagen.width, imagen.height) || recorte.isEmpty) {
            recorte.set(0, 0, imagen.width, imagen.height)
        }

        val rotacion = imagen.imageInfo.rotationDegrees
        val matriz = Matrix().apply { postRotate(rotacion.toFloat()) }

        val vertical = Bitmap.createBitmap(
            crudo, recorte.left, recorte.top, recorte.width(), recorte.height(), matriz, true
        )

        if (vertical !== crudo) {
            crudo.recycle()
        }

        if (!diagnosticoRegistrado) {
            diagnosticoRegistrado = true
            Log.d("DeepSign", "Imagen vertical: ${vertical.width}x${vertical.height}")
        }

        return vertical
    }

    override fun onDestroy() {
        traduciendo.set(false)
        cameraProvider?.unbindAll()

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::cargaExecutor.isInitialized) cargaExecutor.shutdown()
        if (::palabraExecutor.isInitialized) palabraExecutor.shutdown()

        handLandmarker?.close()
        cuerpoDetector?.cerrar()
        super.onDestroy()
    }
}

class HandOverlayView(context: Context) : View(context) {

    private var manos: List<List<PointF>> = emptyList()
    private var cuerpo: List<PointF>? = null
    private var anchoFrame = 0
    private var altoFrame = 0
    private var espejo = false

    private val pinturaLinea = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val pinturaPunto = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinturaCuerpo = Paint().apply {
        color = Color.rgb(255, 200, 0)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val pinturaCabeza = Paint().apply {
        color = Color.rgb(255, 200, 0)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pinturaPuntoCuerpo = Paint().apply {
        color = Color.rgb(255, 200, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val conexiones = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        0 to 13, 13 to 14, 14 to 15, 15 to 16,
        0 to 17, 17 to 18, 18 to 19, 19 to 20,
        5 to 9, 9 to 13, 13 to 17
    )

    // Índices de los 9 puntos del cuerpo: 0 nariz, 1-2 orejas, 3-4 hombros,
    // 5-6 codos, 7-8 muñecas.
    private val conexionesCuerpo = listOf(3 to 4, 3 to 5, 5 to 7, 4 to 6, 6 to 8)

    fun actualizar(
        marcadores: List<List<PointF>>,
        anchoFrame: Int,
        altoFrame: Int,
        espejo: Boolean
    ) {
        this.manos = marcadores
        this.anchoFrame = anchoFrame
        this.altoFrame = altoFrame
        this.espejo = espejo
        invalidate()
    }

    fun actualizarCuerpo(puntos: List<PointF>?) {
        cuerpo = puntos
        invalidate()
    }

    private fun aPantalla(punto: PointF, escala: Float, dx: Float, dy: Float): PointF {
        var x = punto.x * anchoFrame * escala + dx
        val y = punto.y * altoFrame * escala + dy
        if (espejo) x = width - x
        return PointF(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (anchoFrame <= 0 || altoFrame <= 0 || width == 0 || height == 0) return

        val escala = maxOf(
            width / anchoFrame.toFloat(),
            height / altoFrame.toFloat()
        )
        val dx = (width - anchoFrame * escala) / 2f
        val dy = (height - altoFrame * escala) / 2f

        // Cuerpo: contorno de la cabeza, brazos y hombros.
        cuerpo?.takeIf { it.size >= 9 }?.let { c ->
            val p = c.map { aPantalla(it, escala, dx, dy) }
            val radio = kotlin.math.hypot(p[1].x - p[2].x, p[1].y - p[2].y) * 0.8f
            canvas.drawCircle(p[0].x, p[0].y, radio, pinturaCabeza)
            for ((a, b) in conexionesCuerpo) {
                canvas.drawLine(p[a].x, p[a].y, p[b].x, p[b].y, pinturaCuerpo)
            }
            for (punto in p) {
                canvas.drawCircle(punto.x, punto.y, 8f, pinturaPuntoCuerpo)
            }
        }

        // Manos.
        for (mano in manos) {
            if (mano.size < 21) continue

            val puntos = mano.map { aPantalla(it, escala, dx, dy) }

            for ((inicio, fin) in conexiones) {
                val a = puntos[inicio]
                val b = puntos[fin]
                canvas.drawLine(a.x, a.y, b.x, b.y, pinturaLinea)
            }

            for (punto in puntos) {
                canvas.drawCircle(punto.x, punto.y, 7f, pinturaPunto)
            }
        }
    }
}