package com.jose.deepsign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// El Adapter recibe la lista de categorias y una funcion que se ejecuta
// cuando el usuario toca una de ellas (onClick)
class CategoriaAdapter(
    private val categorias: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<CategoriaAdapter.CategoriaViewHolder>() {

    // El ViewHolder "envuelve" el diseño de un solo item (item_categoria.xml)
    class CategoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNombre: TextView = view.findViewById(R.id.txtNombreCategoria)
    }

    // Se llama una vez por cada item nuevo que hay que crear en pantalla
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria, parent, false)
        return CategoriaViewHolder(vista)
    }

    // Se llama para "llenar" cada item con los datos correctos
    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        val nombreCategoria = categorias[position]
        holder.txtNombre.text = nombreCategoria

        holder.itemView.setOnClickListener {
            onClick(nombreCategoria)
        }
    }

    // Le dice al RecyclerView cuantos items hay en total
    override fun getItemCount(): Int {
        return categorias.size
    }
}