package com.jose.deepsign

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object Ui {

    fun dp(c: Context, valor: Int): Int =
        (valor * c.resources.displayMetrics.density + 0.5f).toInt()

    fun tarjeta(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(c, 16), dp(c, 14), dp(c, 12), dp(c, 14))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 18).toFloat()
            setColor(Color.WHITE)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 10) }
    }

    fun textos(c: Context, principal: String, secundario: String = ""): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(c).apply {
                text = principal
                textSize = 16f
                setTextColor(Color.rgb(46, 76, 61))
                setTypeface(null, Typeface.BOLD)
            })
            if (secundario.isNotEmpty()) {
                addView(TextView(c).apply {
                    text = secundario
                    textSize = 12f
                    setTextColor(Color.rgb(92, 111, 102))
                })
            }
        }

    fun boton(c: Context, texto: String, color: Int, accion: () -> Unit): TextView =
        TextView(c).apply {
            text = texto
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 12).toFloat()
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(c, 8) }
            setOnClickListener { accion() }
        }
}