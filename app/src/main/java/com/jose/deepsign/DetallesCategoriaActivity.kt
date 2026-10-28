package com.jose.deepsign

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView

class DetallesCategoriaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalles_categoria)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val categoriaNombre = intent.getStringExtra("categoria") ?: "Categoría"

        val txtCategoria = findViewById<TextView>(R.id.txtCategoria)
        val txtContenido = findViewById<TextView>(R.id.txtContenido)

        txtCategoria.text = categoriaNombre
        txtContenido.text = "Videos de: $categoriaNombre\n\n(La reproducción de videos será agregada próximamente)"
    }
}