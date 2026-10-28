package com.jose.deepsign

import android.content.Context
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class VozAdapter(
    context: Context,
    private val voces: List<Triple<Voice, String, Int>>
) : ArrayAdapter<Triple<Voice, String, Int>>(context, 0, voces) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return crearVista(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return crearVista(position, convertView, parent)
    }

    private fun crearVista(position: Int, convertView: View?, parent: ViewGroup): View {
        val vista = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_voz, parent, false)

        val (_, nombre, iconoRes) = voces[position]

        vista.findViewById<ImageView>(R.id.imgIconoVoz).setImageResource(iconoRes)
        vista.findViewById<TextView>(R.id.txtNombreVoz).text = nombre

        return vista
    }
}