package com.jose.deepsign

import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tts: TextToSpeech
    private var ttsListo = false
    private var todos: List<Registro> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        lista = findViewById(R.id.listaHistorial)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }

        tts = TextToSpeech(this) { estado ->
            ttsListo = estado == TextToSpeech.SUCCESS
            if (ttsListo) tts.language = Locale("es")
        }

        findViewById<EditText>(R.id.txtBuscar).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                mostrar(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        todos = Almacen.historial(this)
        mostrar("")
    }

    private fun mostrar(filtro: String) {
        lista.removeAllViews()

        val visibles = todos.filter { it.frase.contains(filtro, ignoreCase = true) }

        if (visibles.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = if (todos.isEmpty()) "Aún no hay traducciones guardadas."
                else "No se encontraron resultados."
                textSize = 14f
                setTextColor(Color.rgb(92, 111, 102))
            })
            return
        }

        visibles.forEach { registro ->
            val fila = Ui.tarjeta(this)
            fila.addView(
                Ui.textos(
                    this,
                    registro.frase,
                    "${registro.tipo} · ${formatearFecha(registro.fecha)}"
                )
            )
            fila.addView(Ui.boton(this, "▶", Color.rgb(46, 76, 61)) {
                hablar(registro.frase)
            })
            lista.addView(fila)
        }
    }

    private fun formatearFecha(milis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(milis))

    private fun hablar(texto: String) {
        if (ttsListo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "historial")
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}