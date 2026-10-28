package com.jose.deepsign

import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class FavoritosActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tts: TextToSpeech
    private var ttsListo = false
    private val favoritos = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favoritos)

        lista = findViewById(R.id.listaFavoritos)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAgregar).setOnClickListener { dialogoAgregar() }

        tts = TextToSpeech(this) { estado ->
            ttsListo = estado == TextToSpeech.SUCCESS
            if (ttsListo) tts.language = Locale("es")
        }

        favoritos.clear()
        favoritos.addAll(Almacen.favoritos(this))
        refrescar()
    }

    private fun refrescar() {
        lista.removeAllViews()

        if (favoritos.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "No tienes frases favoritas. Toca «+ Agregar frase»."
                textSize = 14f
                setTextColor(Color.rgb(92, 111, 102))
            })
            return
        }

        favoritos.forEachIndexed { indice, frase ->
            val fila = Ui.tarjeta(this)
            fila.addView(Ui.textos(this, frase))
            fila.addView(Ui.boton(this, "▶", Color.rgb(46, 76, 61)) { hablar(frase) })
            fila.addView(Ui.boton(this, "✕", Color.rgb(232, 130, 92)) { quitar(indice) })
            lista.addView(fila)
        }
    }

    private fun quitar(indice: Int) {
        favoritos.removeAt(indice)
        Almacen.guardarFavoritos(this, favoritos)
        refrescar()
    }

    private fun dialogoAgregar() {
        val campo = EditText(this).apply { hint = "Escribe la frase" }

        AlertDialog.Builder(this)
            .setTitle("Nueva frase favorita")
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val nueva = campo.text.toString().trim()
                if (nueva.isNotEmpty()) {
                    favoritos.add(nueva)
                    Almacen.guardarFavoritos(this, favoritos)
                    refrescar()
                }
            }
            .show()
    }

    private fun hablar(texto: String) {
        if (ttsListo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "favorito")
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}