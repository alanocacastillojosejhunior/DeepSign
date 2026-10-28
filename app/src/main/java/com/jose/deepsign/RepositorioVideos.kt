package com.jose.deepsign

object RepositorioVideos {

    val listaVideos = listOf(
        VideoSeña(
            titulo = "Hola",
            categoria = "Saludos",
            nombreArchivo = "saludos_hola"
        ),
        VideoSeña(
            titulo = "Chau",
            categoria = "Saludos",
            nombreArchivo = "saludos_chau"
        ),

    )

    // Devuelve solo los nombres de categorias, sin repetir
    fun obtenerCategorias(): List<String> {
        return listaVideos.map { it.categoria }.distinct()
    }

    // Devuelve los videos que pertenecen a una categoria especifica
    fun obtenerVideosPorCategoria(categoria: String): List<VideoSeña> {
        return listaVideos.filter { it.categoria == categoria }
    }
}