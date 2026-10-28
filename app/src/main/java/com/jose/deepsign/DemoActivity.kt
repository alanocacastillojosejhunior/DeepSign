package com.jose.deepsign

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class DemoActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var textoFrase: TextView
    private lateinit var campoTexto: EditText
    private lateinit var grid: GridLayout
    private lateinit var spinnerVoz: Spinner
    private lateinit var txtVelocidad: TextView
    private var ttsListo = false
    private var velocidadVoz = 0.9f
    private var listaVoces: List<Voice> = emptyList()

    private val verde = Color.rgb(31, 61, 46)
    private val coral = Color.rgb(232, 130, 92)
    private val gris = Color.rgb(45, 45, 45)
    private val textoInicial = "Toca una frase"

    private val frasesBase = listOf(
        "Hola, buenos días",
        "Hola, buenas tardes",
        "Hola, buenas noches",
        "Hola, un placer",
        "¿Cómo te llamas?",
        "¿Cómo estás?",
        "Muchas gracias",
        "Bienvenido",
        "Hasta luego"
    )

    // ---------- Frases del usuario (guardadas en el celular) ----------

    private val prefs by lazy { getSharedPreferences("deepsign_frases", Context.MODE_PRIVATE) }

    private fun cargarMisFrases(): MutableList<String> {
        val guardado = prefs.getString("frases", "") ?: ""
        return if (guardado.isEmpty()) mutableListOf()
        else guardado.split("\n").filter { it.isNotBlank() }.toMutableList()
    }

    private fun guardarMisFrases(lista: List<String>) {
        prefs.edit().putString("frases", lista.joinToString("\n")).apply()
    }

    // ---------- Pantalla ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 20))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        raiz.addView(TextView(this).apply {
            text = "DeepSign · Hablar"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        // Tarjeta con la frase grande (fija arriba)
        textoFrase = TextView(this).apply {
            text = textoInicial
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minHeight = dp(130)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = redondo(gris)
        }
        raiz.addView(
            textoFrase,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(8)
            }
        )

        // Todo lo demás va dentro de un scroll
        val contenido = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Campo de texto + Hablar
        val filaEscribir = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        campoTexto = EditText(this).apply {
            hint = "Escribe una frase..."
            setHintTextColor(Color.rgb(150, 150, 150))
            setTextColor(Color.WHITE)
            textSize = 18f
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = redondo(gris)
        }
        filaEscribir.addView(
            campoTexto,
            LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginEnd = dp(8) }
        )
        filaEscribir.addView(
            crearBoton("Hablar", coral, 60) {
                val t = campoTexto.text.toString().trim()
                if (t.isNotEmpty()) {
                    decirFrase(t)
                    ocultarTeclado()
                }
            },
            LinearLayout.LayoutParams(dp(120), dp(60))
        )
        contenido.addView(filaEscribir)

        // ---------- Velocidad ----------
        txtVelocidad = etiqueta("")
        contenido.addView(txtVelocidad, margenSuperior(16))

        // progreso 0..150 -> velocidad 0.5x..2.0x (nunca 0)
        val seek = SeekBar(this).apply {
            max = 150
            progress = 40   // 0.9x
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    velocidadVoz = 0.5f + progress / 100f
                    actualizarEtiquetaVelocidad()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        contenido.addView(seek)
        actualizarEtiquetaVelocidad()

        // ---------- Voces ----------
        contenido.addView(etiqueta("Tipo de voz (toca para cambiar)"), margenSuperior(16))

        spinnerVoz = Spinner(this).apply {
            background = redondo(gris)
            setPopupBackgroundDrawable(redondo(gris))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        contenido.addView(
            spinnerVoz,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply { topMargin = dp(6) }
        )

        contenido.addView(
            crearBoton("Probar todas las voces", verde, 50) { probarVoces() },
            margenSuperior(10)
        )

        // ---------- Frases propias ----------
        contenido.addView(
            crearBoton("+ Agregar frase", coral, 56) { mostrarDialogoNueva() },
            margenSuperior(16)
        )

        contenido.addView(TextView(this).apply {
            text = "Mantén presionada una de tus frases para borrarla"
            textSize = 12f
            setTextColor(Color.rgb(150, 150, 150))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        })

        grid = GridLayout(this).apply { columnCount = 2 }
        contenido.addView(grid)

        val scroll = ScrollView(this).apply { addView(contenido) }
        raiz.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // Controles inferiores (fijos)
        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        controles.addView(
            crearBoton("Borrar", coral, 64) {
                textoFrase.text = textoInicial
                campoTexto.setText("")
                if (ttsListo) tts.stop()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        controles.addView(
            crearBoton("Repetir frase", verde, 64) {
                val actual = textoFrase.text.toString()
                if (actual != textoInicial) hablar(actual)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )
        raiz.addView(controles)

        setContentView(raiz)
        construirGrid()

        // El motor de voz se crea al final, cuando la pantalla ya existe
        tts = TextToSpeech(this, this)
    }

    // ---------- Lista de frases ----------

    private fun construirGrid() {
        grid.removeAllViews()
        cargarMisFrases().forEach { agregarBotonFrase(it, esPropia = true) }
        frasesBase.forEach { agregarBotonFrase(it, esPropia = false) }
    }

    private fun agregarBotonFrase(frase: String, esPropia: Boolean) {
        val boton = Button(this).apply {
            text = frase
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(verde)
                if (esPropia) setStroke(dp(2), coral)
            }
            minHeight = dp(90)
            setOnClickListener { decirFrase(frase) }
            if (esPropia) {
                setOnLongClickListener {
                    confirmarBorrado(frase)
                    true
                }
            }
        }
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

    private fun mostrarDialogoNueva() {
        val input = EditText(this).apply {
            hint = "Ej: Mi nombre es Jose"
            setSingleLine(false)
            maxLines = 4
            textSize = 18f
        }
        val contenedor = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Nueva frase")
            .setView(contenedor)
            .setPositiveButton("Guardar") { _, _ ->
                val nueva = input.text.toString().replace("\n", " ").trim()
                if (nueva.isNotEmpty()) {
                    val lista = cargarMisFrases()
                    if (!lista.contains(nueva)) {
                        lista.add(0, nueva)
                        guardarMisFrases(lista)
                        construirGrid()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarBorrado(frase: String) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar esta frase?")
            .setMessage(frase)
            .setPositiveButton("Borrar") { _, _ ->
                val lista = cargarMisFrases()
                lista.remove(frase)
                guardarMisFrases(lista)
                construirGrid()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Voz ----------

    private fun decirFrase(texto: String) {
        textoFrase.text = texto
        hablar(texto)
    }

    private fun hablar(texto: String) {
        if (!ttsListo) return
        val pos = spinnerVoz.selectedItemPosition
        if (pos >= 0 && pos < listaVoces.size) {
            tts.voice = listaVoces[pos]
        }
        tts.setSpeechRate(velocidadVoz)
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "demo")
    }

    private fun probarVoces() {
        if (!ttsListo) return
        tts.setSpeechRate(velocidadVoz)
        listaVoces.forEachIndexed { index, voz ->
            tts.voice = voz
            tts.speak("Voz número ${index + 1}", TextToSpeech.QUEUE_ADD, null, "prueba_n$index")
            tts.speak(
                "Hola, esta es una prueba de voz para DeepSign",
                TextToSpeech.QUEUE_ADD, null, "prueba_t$index"
            )
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        val candidatos = listOf(
            Locale("es", "BO"),
            Locale("es", "MX"),
            Locale("es", "ES"),
            Locale("es")
        )
        val elegido = candidatos.firstOrNull {
            tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        } ?: Locale("es")
        tts.language = elegido

        val todasLasVoces = tts.voices
            ?.filter { it.locale.language == "es" }
            ?.toList() ?: emptyList()

        // Mismas voces e iconos que tenías en Escribir
        val elegidas = listOf(
            9 to Pair("Opción 1", R.drawable.icono_joven1),
            17 to Pair("Opción 2", R.drawable.icono_joven2),
            1 to Pair("Opción 3", R.drawable.icono_senorita1),
            5 to Pair("Opción 4", R.drawable.icono_senor),
            7 to Pair("Opción 5", R.drawable.icono_senora),
            10 to Pair("Opción 6", R.drawable.icono_senorita2)
        )

        var vocesFiltradas = elegidas.mapNotNull { (indice, datos) ->
            if (indice < todasLasVoces.size)
                Triple(todasLasVoces[indice], datos.first, datos.second)
            else null
        }

        // Respaldo: si este celular no tiene esos índices, usar las primeras voces en español
        if (vocesFiltradas.isEmpty() && todasLasVoces.isNotEmpty()) {
            vocesFiltradas = todasLasVoces.take(elegidas.size).mapIndexed { i, voz ->
                Triple(voz, "Opción ${i + 1}", elegidas[i].second.second)
            }
        }

        listaVoces = vocesFiltradas.map { it.first }
        spinnerVoz.adapter = VozAdapter(this, vocesFiltradas)

        ttsListo = true
    }

    // ---------- Utilidades ----------

    private fun actualizarEtiquetaVelocidad() {
        txtVelocidad.text = "Velocidad de lectura: ${"%.1f".format(velocidadVoz)}x"
    }

    private fun etiqueta(t: String) = TextView(this).apply {
        text = t
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun margenSuperior(dpValor: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(dpValor) }

    private fun redondo(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(color)
    }

    private fun crearBoton(texto: String, color: Int, altoDp: Int, accion: () -> Unit): Button =
        Button(this).apply {
            text = texto
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            minHeight = dp(altoDp)
            background = redondo(color)
            setOnClickListener { accion() }
        }

    private fun ocultarTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(campoTexto.windowToken, 0)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density + 0.5f).toInt()
}