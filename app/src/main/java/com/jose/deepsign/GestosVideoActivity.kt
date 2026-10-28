package com.jose.deepsign

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class GestosVideoActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsListo = false
    private lateinit var video: VideoView
    private lateinit var titulo: TextView
    private val botones = mutableListOf<Pair<Button, GradientDrawable>>()

    private val verde = Color.rgb(31, 61, 46)
    private val coral = Color.rgb(232, 130, 92)

    // archivo en res/raw  ->  texto que se muestra
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 20))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        raiz.addView(TextView(this).apply {
            text = "DeepSign · Ver gestos"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        // Zona del video
        val zonaVideo = FrameLayout(this).apply {
            background = redondo(Color.BLACK)
        }
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
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)
            ).apply { topMargin = dp(14) }
        )

        titulo = TextView(this).apply {
            text = "Toca una seña"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        raiz.addView(titulo)

        // Botones
        val grid = GridLayout(this).apply { columnCount = 2 }
        gestos.forEach { (archivo, etiqueta) ->
            val id = resources.getIdentifier(archivo, "raw", packageName)
            if (id == 0) return@forEach   // si falta el video, se omite

            val fondo = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(verde)
            }
            val boton = Button(this).apply {
                text = etiqueta
                textSize = 16f
                isAllCaps = false
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = fondo
                minHeight = dp(70)
                setOnClickListener { reproducir(id, etiqueta, fondo) }
            }
            botones.add(boton to fondo)
            grid.addView(
                boton,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            )
        }

        raiz.addView(
            ScrollView(this).apply { addView(grid) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(raiz)
    }

    private fun reproducir(id: Int, etiqueta: String, fondoElegido: GradientDrawable) {
        titulo.text = etiqueta

        // Resalta en coral el botón elegido
        botones.forEach { (_, fondo) -> fondo.setColor(verde) }
        fondoElegido.setColor(coral)

        video.stopPlayback()
        video.setVideoURI(Uri.parse("android.resource://$packageName/$id"))
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)   // el audio lo da la voz de DeepSign
            video.start()
        }
        video.setOnErrorListener { _, _, _ -> true }  // evita ventanas de error

        if (ttsListo) tts.speak(etiqueta, TextToSpeech.QUEUE_FLUSH, null, "gesto")
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