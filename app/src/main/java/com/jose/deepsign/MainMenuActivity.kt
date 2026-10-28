package com.jose.deepsign

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainMenuActivity : AppCompatActivity() {

    private lateinit var barra: BottomNavigationView
    private lateinit var listaRecientes: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Almacen.aplicarTema(this)
        setContentView(R.layout.activity_main_menu)

        barra = findViewById(R.id.barraInferior)
        listaRecientes = findViewById(R.id.listaRecientes)

        val colores = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.rgb(255, 111, 94), Color.rgb(46, 76, 61))
        )
        barra.itemIconTintList = colores
        barra.itemTextColor = colores

        barra.selectedItemId = R.id.nav_inicio

        barra.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> true
                R.id.nav_traducir -> {
                    startActivity(Intent(this, TraducirActivity::class.java))
                    true
                }
                R.id.nav_quiz -> {
                    startActivity(Intent(this, QuizActivity::class.java))
                    true
                }
                R.id.nav_favoritos -> {
                    startActivity(Intent(this, FavoritosActivity::class.java))
                    true
                }
                R.id.nav_mas -> {
                    startActivity(Intent(this, MasActivity::class.java))
                    true
                }
                else -> false
            }
        }

        findViewById<View>(R.id.btnNotificaciones).setOnClickListener {
            startActivity(Intent(this, MasActivity::class.java))
        }
        findViewById<View>(R.id.txtVerTodo).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        findViewById<View>(R.id.cardVerGestos).setOnClickListener {
            startActivity(Intent(this, GestosVideoActivity::class.java))
        }
        findViewById<View>(R.id.cardEscribir).setOnClickListener {
            startActivity(Intent(this, EscribirActivity::class.java))
        }
        findViewById<View>(R.id.btnTraducirCentro).setOnClickListener {
            startActivity(Intent(this, TraducirActivity::class.java))
        }

    }

    override fun onResume() {
        super.onResume()
        barra.selectedItemId = R.id.nav_inicio
        refrescarRecientes()
    }

    private fun refrescarRecientes() {
        listaRecientes.removeAllViews()

        val items = Almacen.historial(this).take(4)

        if (items.isEmpty()) {
            listaRecientes.addView(TextView(this).apply {
                text = "Aún no hay traducciones. Prueba con Traducir."
                textSize = 13f
                setTextColor(Color.rgb(92, 111, 102))
            })
            return
        }

        items.forEach { agregarFila(it) }
    }

    private fun agregarFila(registro: Registro) {
        val fila = Ui.tarjeta(this)

        val (icono, etiqueta, color) = when (registro.tipo) {
            "Voz" -> Triple(R.drawable.ic_nav_hablar, "Voz → Texto", Color.rgb(46, 76, 61))
            "Texto" -> Triple(R.drawable.ic_escribir, "Texto → LCB", Color.rgb(46, 76, 61))
            else -> Triple(R.drawable.ic_ver_gestos, "LCB → Texto", Color.rgb(255, 111, 94))
        }

        fila.addView(circulo(icono, color))

        val textos = Ui.textos(this, registro.frase, etiqueta)
        (textos.layoutParams as LinearLayout.LayoutParams).marginStart = Ui.dp(this, 12)
        fila.addView(textos)

        fila.addView(TextView(this).apply {
            text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(registro.fecha))
            textSize = 11f
            setTextColor(Color.rgb(92, 111, 102))
        })

        fila.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        listaRecientes.addView(fila)
    }

    private fun circulo(icono: Int, color: Int): ImageView =
        ImageView(this).apply {
            setImageResource(icono)
            setColorFilter(Color.WHITE)
            setPadding(Ui.dp(this@MainMenuActivity, 10), Ui.dp(this@MainMenuActivity, 10),
                Ui.dp(this@MainMenuActivity, 10), Ui.dp(this@MainMenuActivity, 10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(
                Ui.dp(this@MainMenuActivity, 44),
                Ui.dp(this@MainMenuActivity, 44)
            )
        }
}