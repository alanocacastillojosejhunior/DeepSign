package com.jose.deepsign

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NotificacionesActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FBF6F2"))
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        encabezado.addView(TextView(this).apply {
            text = "←"
            textSize = 26f
            setTextColor(Color.parseColor("#2E4C3D"))
            setPadding(0, 0, dp(16), 0)
            setOnClickListener { finish() }
        })
        encabezado.addView(TextView(this).apply {
            text = "Notificaciones"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2E4C3D"))
        })
        raiz.addView(encabezado)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        lista.addView(fila("🧠", "Responde el quiz",
            "Pon a prueba lo que sabes de la LCB.", "#FF6F5E") {
            startActivity(Intent(this, QuizActivity::class.java))
        })
        lista.addView(fila("🤟", "Practica un gesto nuevo",
            "Mira los gestos y aprende uno hoy.", "#2E4C3D") {
            startActivity(Intent(this, GestosVideoActivity::class.java))
        })
        lista.addView(fila("🕘", "Revisa tu historial",
            "Mira tus últimas traducciones.", "#2E4C3D") {
            startActivity(Intent(this, HistorialActivity::class.java))
        })
        lista.addView(fila("✍", "Escribe una frase",
            "Convierte un texto en señas LCB.", "#FF6F5E") {
            startActivity(Intent(this, EscribirActivity::class.java))
        })

        raiz.addView(ScrollView(this).apply { addView(lista) })
        setContentView(raiz)
    }

    private fun fila(
        emoji: String,
        titulo: String,
        detalle: String,
        colorHex: String,
        alTocar: () -> Unit
    ): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            isClickable = true
            isFocusable = true
            setOnClickListener { alTocar() }
        }

        fila.addView(TextView(this).apply {
            text = emoji
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })

        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        textos.addView(TextView(this).apply {
            text = titulo
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2E4C3D"))
        })
        textos.addView(TextView(this).apply {
            text = detalle
            textSize = 13f
            setTextColor(Color.parseColor("#5C6F66"))
        })
        fila.addView(textos)

        fila.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.parseColor("#FF6F5E"))
        })
        return fila
    }
}