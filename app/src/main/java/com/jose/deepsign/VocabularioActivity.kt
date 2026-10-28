package com.jose.deepsign

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class VocabularioActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsListo = false
    private lateinit var video: VideoView
    private lateinit var marcador: TextView
    private lateinit var mensaje: TextView
    private lateinit var contenedorOpciones: LinearLayout
    private lateinit var botonSiguiente: Button

    private val verde = Color.rgb(31, 61, 46)
    private val coral = Color.rgb(232, 130, 92)
    private val correctoColor = Color.rgb(46, 125, 50)
    private val incorrectoColor = Color.rgb(183, 28, 28)

    // archivo en res/raw -> texto que se muestra
    private val gestos = listOf(
        "hola" to "Hola",
        "buenos_dias" to "Buenos días",
        "buenas_tardes" to "Buenas tardes",
        "buenas_noches" to "Buenas noches",
        "como_estas" to "¿Cómo estás?",
        "gracias" to "Gracias",
        "muchas_gracias" to "Muchas gracias",
        "no_gracias" to "No, gracias",
        "provecho" to "Provecho",
        "satisfecho" to "Satisfecho"
    )

    private class Seña(val archivo: String, val etiqueta: String, val id: Int)

    // solo los videos que realmente existen en res/raw
    private val disponibles: List<Seña> by lazy {
        gestos.mapNotNull { (archivo, etiqueta) ->
            val id = resources.getIdentifier(archivo, "raw", packageName)
            if (id == 0) null else Seña(archivo, etiqueta, id)
        }
    }

    private var orden: List<Seña> = emptyList()
    private var indice = 0
    private var aciertos = 0
    private var respondido = false
    private var terminado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 20))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        raiz.addView(TextView(this).apply {
            text = "DeepSign · Vocabulario"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        marcador = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(200, 200, 200))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        raiz.addView(marcador)

        val zonaVideo = FrameLayout(this).apply { background = redondo(Color.BLACK) }
        video = VideoView(this)
        zonaVideo.addView(
            video,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        raiz.addView(
            zonaVideo,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260))
        )

        mensaje = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(12), dp(8), dp(8))
        }
        raiz.addView(mensaje)

        contenedorOpciones = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        botonSiguiente = Button(this).apply {
            text = "Siguiente"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minHeight = dp(60)
            background = redondo(coral)
            visibility = View.INVISIBLE
            setOnClickListener {
                if (terminado) {
                    empezar()
                } else {
                    indice++
                    mostrarPregunta()
                }
            }
        }

        val contenido = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contenido.addView(contenedorOpciones)
        contenido.addView(
            botonSiguiente,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        raiz.addView(
            ScrollView(this).apply { addView(contenido) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(raiz)
        empezar()
    }

    private fun empezar() {
        if (disponibles.size < 3) {
            marcador.text = ""
            mensaje.text = "Faltan videos para el quiz"
            return
        }
        orden = disponibles.shuffled()
        indice = 0
        aciertos = 0
        terminado = false
        mostrarPregunta()
    }

    private fun mostrarPregunta() {
        if (indice >= orden.size) {
            mostrarFinal()
            return
        }
        respondido = false
        botonSiguiente.visibility = View.INVISIBLE
        botonSiguiente.text = if (indice == orden.size - 1) "Ver resultado" else "Siguiente"

        val correcta = orden[indice]
        marcador.text = "Pregunta ${indice + 1} de ${orden.size}  ·  Aciertos: $aciertos"
        mensaje.text = "¿Qué seña es?"

        reproducir(correcta.id)

        val opciones = (disponibles.filter { it !== correcta }.shuffled().take(2) + correcta).shuffled()

        contenedorOpciones.removeAllViews()
        val botones = mutableListOf<Triple<Button, GradientDrawable, Seña>>()
        opciones.forEach { opcion ->
            val fondo = redondo(verde)
            val boton = Button(this).apply {
                text = opcion.etiqueta
                textSize = 20f
                isAllCaps = false
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                minHeight = dp(64)
                background = fondo
            }
            botones.add(Triple(boton, fondo, opcion))
            contenedorOpciones.addView(
                boton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            )
        }

        botones.forEach { (boton, _, opcion) ->
            boton.setOnClickListener {
                if (respondido) return@setOnClickListener
                respondido = true

                if (opcion === correcta) {
                    aciertos++
                    mensaje.text = "¡Correcto!"
                    hablar("¡Correcto! ${correcta.etiqueta}")
                } else {
                    mensaje.text = "Era: ${correcta.etiqueta}"
                    hablar("Casi. Era ${correcta.etiqueta}")
                }

                // Pinta la correcta en verde y la elegida mal en rojo
                botones.forEach { (_, fondo, o) ->
                    when {
                        o === correcta -> fondo.setColor(correctoColor)
                        o === opcion -> fondo.setColor(incorrectoColor)
                    }
                }
                marcador.text = "Pregunta ${indice + 1} de ${orden.size}  ·  Aciertos: $aciertos"
                botonSiguiente.visibility = View.VISIBLE
            }
        }
    }

    private fun mostrarFinal() {
        terminado = true
        video.stopPlayback()
        contenedorOpciones.removeAllViews()
        marcador.text = "Quiz terminado"
        mensaje.text = "Acertaste $aciertos de ${orden.size}"
        hablar("Terminaste. Acertaste $aciertos de ${orden.size}")
        botonSiguiente.text = "Jugar de nuevo"
        botonSiguiente.visibility = View.VISIBLE
    }

    private fun reproducir(id: Int) {
        video.stopPlayback()
        video.setVideoURI(Uri.parse("android.resource://$packageName/$id"))
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            video.start()
        }
        video.setOnErrorListener { _, _, _ -> true }
    }

    private fun hablar(texto: String) {
        if (ttsListo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "quiz")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val elegido = listOf(Locale("es", "BO"), Locale("es", "MX"), Locale("es", "ES"), Locale("es"))
            .firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: Locale("es")
        tts.language = elegido
        ttsListo = true
    }

    override fun onDestroy() {
        video.stopPlayback()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun redondo(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(color)
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density + 0.5f).toInt()
}