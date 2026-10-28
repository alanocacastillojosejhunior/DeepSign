package com.jose.deepsign


// Esta es la "ficha" de cada video: describe un video de seña
data class VideoSeña(
    val titulo: String,      // Ej: "Hola"
    val categoria: String,   // Ej: "Saludos"
    val nombreArchivo: String // Ej: "saludos_hola" (SIN la extensión .mp4)
)