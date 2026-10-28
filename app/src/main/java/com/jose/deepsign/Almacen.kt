package com.jose.deepsign

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

data class Registro(val frase: String, val tipo: String, val fecha: Long)

object Almacen {

    private const val PREFS = "deepsign_prefs"
    private const val KEY_HISTORIAL = "historial"
    private const val KEY_FAVORITOS = "favoritos"
    private const val KEY_TEMA = "tema"

    private val favoritosIniciales = listOf(
        "Hola", "Gracias", "Por favor", "¿Cómo estás?", "Buenos días", "Te quiero"
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Historial ----------

    fun historial(c: Context): List<Registro> {
        val arr = JSONArray(prefs(c).getString(KEY_HISTORIAL, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Registro(o.getString("frase"), o.getString("tipo"), o.getLong("fecha"))
        }.reversed()
    }

    fun agregarHistorial(c: Context, frase: String, tipo: String) {
        val arr = JSONArray(prefs(c).getString(KEY_HISTORIAL, "[]"))
        arr.put(
            JSONObject()
                .put("frase", frase)
                .put("tipo", tipo)
                .put("fecha", System.currentTimeMillis())
        )
        // Se conservan solo las 100 traducciones más recientes.
        val recortado = JSONArray()
        for (i in maxOf(0, arr.length() - 100) until arr.length()) {
            recortado.put(arr.getJSONObject(i))
        }
        prefs(c).edit().putString(KEY_HISTORIAL, recortado.toString()).apply()
    }

    // ---------- Favoritos ----------

    fun favoritos(c: Context): List<String> {
        val guardado = prefs(c).getString(KEY_FAVORITOS, null) ?: return favoritosIniciales
        val arr = JSONArray(guardado)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun guardarFavoritos(c: Context, lista: List<String>) {
        prefs(c).edit().putString(KEY_FAVORITOS, JSONArray(lista).toString()).apply()
    }

    // ---------- Tema ----------

    /** 0 = automático (sistema), 1 = claro, 2 = oscuro. */
    fun tema(c: Context): Int = prefs(c).getInt(KEY_TEMA, 0)

    fun guardarTema(c: Context, valor: Int) {
        prefs(c).edit().putInt(KEY_TEMA, valor).apply()
    }

    fun aplicarTema(c: Context) {
        val modo = when (tema(c)) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(modo)
    }
}