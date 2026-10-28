package com.jose.deepsign

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class QuizActivity : AppCompatActivity() {

    private class Pregunta(
        val texto: String,
        val opciones: List<String>,
        val correcta: Int,
        val videoId: Int = 0          // 0 = pregunta sin video
    )

    // Mismos videos de res/raw que usa "Ver gestos"
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

    private val verde = Color.rgb(46, 76, 61)
    private val coral = Color.rgb(255, 111, 94)
    private val rojo = Color.rgb(200, 70, 60)

    private var preguntas = listOf<Pregunta>()
    private var indice = 0
    private var puntos = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var barra: ProgressBar
    private lateinit var txtProgreso: TextView
    private lateinit var txtPregunta: TextView
    private lateinit var zonaVideo: FrameLayout
    private lateinit var video: VideoView
    private lateinit var contenedor: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FBF6F2"))
            setPadding(dp(24), dp(40), dp(24), dp(16))
        }

        raiz.addView(TextView(this).apply {
            text = "←"
            textSize = 26f
            setTextColor(verde)
            setOnClickListener { finish() }
        })

        barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(coral)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
            ).apply { topMargin = dp(12) }
        }
        raiz.addView(barra)

        txtProgreso = TextView(this).apply {
            textSize = 13f
            setTextColor(coral)
            setPadding(0, dp(10), 0, dp(6))
        }
        raiz.addView(txtProgreso)

        txtPregunta = TextView(this).apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(verde)
            setPadding(0, 0, 0, dp(14))
        }
        raiz.addView(txtPregunta)

        zonaVideo = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.BLACK)
            }
            visibility = View.GONE
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            ).apply { bottomMargin = dp(16) }
        )

        contenedor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(
            ScrollView(this).apply { addView(contenedor) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(raiz)
        empezar()
    }

    private fun empezar() {
        preguntas = construirPreguntas()
        indice = 0
        puntos = 0
        barra.max = preguntas.size
        mostrarPregunta()
    }

    // ---------- Preguntas ----------

    private fun deTexto(texto: String, correcta: String, vararg otras: String): Pregunta {
        val opciones = (otras.toList() + correcta).shuffled()
        return Pregunta(texto, opciones, opciones.indexOf(correcta))
    }

    private fun construirPreguntas(): List<Pregunta> {
        val texto = listOf(
            deTexto("¿Qué significa LCB?", "Lengua de Señas Boliviana",
                "Lenguaje Corporal Básico", "Lectura Con Braille"),
            deTexto("¿Con qué se comunica principalmente una lengua de señas?", "Manos, rostro y cuerpo",
                "Solo con sonidos", "Solo con escritura"),
            deTexto("¿Qué hace la opción Voz de DeepSign?", "Convierte lo que dices en texto",
                "Graba videos de gestos", "Borra el historial"),
            deTexto("¿Qué hace la opción Escribir de DeepSign?", "Convierte texto en señas LCB",
                "Cambia el tema de la app", "Llama a un contacto"),
            deTexto("Las expresiones del rostro en una lengua de señas...", "Forman parte del mensaje",
                "No importan", "Se usan solo para jugar")
        ).shuffled()

        val disponibles = gestos.mapNotNull { (archivo, etiqueta) ->
            val id = resources.getIdentifier(archivo, "raw", packageName)
            if (id == 0) null else Triple(archivo, etiqueta, id)
        }

        val deVideo = if (disponibles.size >= 3) {
            disponibles.shuffled().take(5).map { g ->
                val distractores = disponibles.filter { it.first != g.first }
                    .shuffled().take(2).map { it.second }
                val opciones = (distractores + g.second).shuffled()
                Pregunta("¿Qué seña es esta?", opciones, opciones.indexOf(g.second), g.third)
            }
        } else emptyList()

        // Alterna: video, texto, video, texto...
        val resultado = mutableListOf<Pregunta>()
        val t = texto.toMutableList()
        val v = deVideo.toMutableList()
        while (t.isNotEmpty() || v.isNotEmpty()) {
            if (v.isNotEmpty()) resultado.add(v.removeAt(0))
            if (t.isNotEmpty()) resultado.add(t.removeAt(0))
        }
        return resultado
    }

    // ---------- Pantalla de pregunta ----------

    private fun mostrarPregunta() {
        val p = preguntas[indice]
        barra.progress = indice
        txtProgreso.text = "Pregunta ${indice + 1} de ${preguntas.size}"
        txtPregunta.text = p.texto
        contenedor.removeAllViews()

        if (p.videoId != 0) {
            zonaVideo.visibility = View.VISIBLE
            video.stopPlayback()
            video.setVideoURI(Uri.parse("android.resource://$packageName/${p.videoId}"))
            video.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                video.start()
            }
            video.setOnErrorListener { _, _, _ -> true }
        } else {
            video.stopPlayback()
            zonaVideo.visibility = View.GONE
        }

        val botones = mutableListOf<Pair<Button, GradientDrawable>>()
        p.opciones.forEachIndexed { i, texto ->
            val fondo = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
            val boton = Button(this).apply {
                text = texto
                isAllCaps = false
                textSize = 15f
                setTextColor(verde)
                background = fondo
                minHeight = dp(56)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            botones.add(boton to fondo)
            boton.setOnClickListener { responder(i, p, botones) }
            contenedor.addView(boton)
        }
    }

    private fun responder(
        elegida: Int,
        p: Pregunta,
        botones: List<Pair<Button, GradientDrawable>>
    ) {
        botones.forEach { it.first.isEnabled = false }

        val acierto = elegida == p.correcta
        if (acierto) puntos++

        // Verde la correcta; roja la elegida si fallaste
        botones[p.correcta].second.setColor(verde)
        botones[p.correcta].first.setTextColor(Color.WHITE)
        if (!acierto) {
            botones[elegida].second.setColor(rojo)
            botones[elegida].first.setTextColor(Color.WHITE)
        }
        txtProgreso.text = if (acierto) "¡Correcto!" else "Incorrecto"

        handler.postDelayed({
            indice++
            if (indice < preguntas.size) mostrarPregunta() else mostrarResultado()
        }, 1100)
    }

    // ---------- Resultado ----------

    private fun mostrarResultado() {
        video.stopPlayback()
        zonaVideo.visibility = View.GONE
        barra.progress = preguntas.size
        txtProgreso.text = "Quiz terminado"
        txtPregunta.text = "Obtuviste $puntos de ${preguntas.size}"
        contenedor.removeAllViews()

        val mensaje = when {
            puntos == preguntas.size -> "¡Perfecto! Dominas estas señas."
            puntos >= preguntas.size * 0.7 -> "¡Muy bien! Sigue practicando."
            else -> "Repasa los gestos en Ver gestos y vuelve a intentarlo."
        }
        contenedor.addView(TextView(this).apply {
            text = mensaje
            textSize = 15f
            setTextColor(Color.parseColor("#5C6F66"))
            setPadding(0, 0, 0, dp(16))
        })

        contenedor.addView(Button(this).apply {
            text = "Intentar de nuevo"
            isAllCaps = false
            setOnClickListener { empezar() }
        })
        contenedor.addView(Button(this).apply {
            text = "Volver"
            isAllCaps = false
            setOnClickListener { finish() }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        video.stopPlayback()
        super.onDestroy()
    }
}