package com.jose.deepsign

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ver)

        val recycler = findViewById<RecyclerView>(R.id.recyclerCategorias)
        recycler.layoutManager = LinearLayoutManager(this)

        val categorias = RepositorioVideos.obtenerCategorias()

        recycler.adapter = CategoriaAdapter(categorias) { categoriaElegida ->
            val intent = android.content.Intent(this, DetallesCategoriaActivity::class.java)
            intent.putExtra("categoria", categoriaElegida)
            startActivity(intent)
        }
    }
}