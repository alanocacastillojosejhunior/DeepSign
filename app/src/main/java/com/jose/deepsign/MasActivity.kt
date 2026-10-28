package com.jose.deepsign

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MasActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mas)

        lista = findViewById(R.id.listaOpciones)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        construirLista()
    }

    private fun construirLista() {
        lista.removeAllViews()

        val temaActual = when (Almacen.tema(this)) {
            1 -> "Claro"
            2 -> "Oscuro"
            else -> "Automático"
        }

        seccion("Herramientas")
        item("Historial", "Todas tus traducciones") {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        seccion("Ajustes")
        item("Tema", temaActual) { dialogoTema() }

        seccion("Ayuda")
        item("Ayuda y soporte", "Preguntas frecuentes") {
            mostrarInfo("Ayuda y soporte", "Consulta esta sección para resolver dudas sobre el uso de DeepSign.")
        }
        item("Enviar sugerencia", "Reporta una seña incorrecta o danos ideas") {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:deepsign3.0@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Sugerencia DeepSign")
            }
            startActivity(intent)
        }
        item("Compartir DeepSign", "Invita a otras personas") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Prueba DeepSign, la app para aprender y traducir la Lengua de Señas Boliviana.")
            }
            startActivity(Intent.createChooser(intent, "Compartir con"))
        }
        item("Acerca de DeepSign", "Versión 1.0") {
            mostrarInfo(
                "Acerca de DeepSign",
                "DeepSign es una aplicación para aprender y traducir la Lengua de Señas Boliviana (LSB).\n\n" +
                        "Privacidad: tus traducciones y favoritos se guardan solo en este teléfono."
            )
        }
    }

    private fun seccion(titulo: String) {
        val d = resources.displayMetrics.density
        val t = TextView(this).apply {
            text = titulo
            textSize = 13f
            setTextColor(Color.rgb(120, 130, 125))
            setPadding((4 * d).toInt(), (20 * d).toInt(), 0, (6 * d).toInt())
        }
        lista.addView(t)
    }

    private fun item(titulo: String, subtitulo: String, accion: () -> Unit) {
        val fila = Ui.tarjeta(this)
        fila.addView(Ui.textos(this, titulo, subtitulo))

        val flecha = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.rgb(232, 130, 92))
            gravity = Gravity.CENTER
        }
        fila.addView(flecha)

        fila.setOnClickListener { accion() }
        lista.addView(fila)
    }

    private fun mostrarInfo(titulo: String, mensaje: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun dialogoTema() {
        val nombres = arrayOf("Automático (según el sistema)", "Claro", "Oscuro")

        AlertDialog.Builder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(nombres, Almacen.tema(this)) { dialogo, seleccion ->
                Almacen.guardarTema(this, seleccion)
                Almacen.aplicarTema(this)
                dialogo.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}