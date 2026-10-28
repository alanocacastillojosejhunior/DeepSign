package com.jose.deepsign

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EscribirActivity : AppCompatActivity(), android.speech.tts.TextToSpeech.OnInitListener {

    private lateinit var tts: android.speech.tts.TextToSpeech
    private var velocidadVoz: Float = 1.0f
    private lateinit var spinnerVoz: android.widget.Spinner
    private var listaVoces: List<android.speech.tts.Voice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_escribir)

        tts = android.speech.tts.TextToSpeech(this, this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val editTexto = findViewById<android.widget.EditText>(R.id.editTexto)
        val btnTraducir = findViewById<android.widget.Button>(R.id.btnTraducir)
        val seekVelocidad = findViewById<android.widget.SeekBar>(R.id.seekVelocidad)
        spinnerVoz = findViewById(R.id.spinnerVoz)
        val btnProbarVoces = findViewById<android.widget.Button>(R.id.btnProbarVoces)


        seekVelocidad.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                velocidadVoz = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        btnTraducir.setOnClickListener {
            val texto = editTexto.text.toString()
            if (texto.isNotBlank()) {
                Almacen.agregarHistorial(this, texto, "Texto")
            }
            val posicion = spinnerVoz.selectedItemPosition
            if (posicion >= 0 && posicion < listaVoces.size) {
                tts.voice = listaVoces[posicion]
            }
            tts.setSpeechRate(velocidadVoz)
            tts.speak(texto, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
        }
        btnProbarVoces.setOnClickListener {
            listaVoces.forEachIndexed { index, voz ->
                tts.voice = voz
                tts.speak("Voz número ${index + 1}", android.speech.tts.TextToSpeech.QUEUE_ADD, null, null)
                tts.speak("Hola, esta es una prueba de voz para DeepSign", android.speech.tts.TextToSpeech.QUEUE_ADD, null, null)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
            tts.language = java.util.Locale("spa", "BOL")

            val todasLasVoces = tts.voices
                ?.filter { it.locale.language == "es" }
                ?.toList() ?: emptyList()

            val elegidas = listOf(
                9 to Pair("Opción 1", R.drawable.icono_joven1),
                17 to Pair("Opción 2", R.drawable.icono_joven2),
                1 to Pair("Opción 3", R.drawable.icono_senorita1),
                5 to Pair("Opción 4", R.drawable.icono_senor),
                7 to Pair("Opción 5", R.drawable.icono_senora),
                10 to Pair("Opción 6", R.drawable.icono_senorita2)
            )

            val vocesFiltradas = elegidas.mapNotNull { (indice, datos) ->
                if (indice < todasLasVoces.size) Triple(todasLasVoces[indice], datos.first, datos.second) else null
            }

            listaVoces = vocesFiltradas.map { it.first }

            val adapter = VozAdapter(this, vocesFiltradas)
            spinnerVoz.adapter = adapter
        }
    }
}