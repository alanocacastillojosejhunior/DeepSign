package com.jose.deepsign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

data class FrameGrabado(
    val manos: List<List<Pair<Float, Float>>>,
    val cuerpo: List<Pair<Float, Float>>?
)

class CuerpoDetector(context: Context) {

    private var detector: PoseLandmarker? = null

    init {
        try {
            val opciones = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("pose_landmarker_lite.task")
                        .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .build()
            detector = PoseLandmarker.createFromOptions(context, opciones)
        } catch (e: Exception) {
            Log.e("DeepSign", "No se pudo cargar el modelo de cuerpo", e)
        }
    }

    /** Devuelve 9 puntos: nariz, orejas, hombros, codos y muñecas. Null si no hay cuerpo. */
    fun detectar(bitmap: Bitmap, timestampMs: Long): List<PointF>? {
        val d = detector ?: return null
        val imagen = BitmapImageBuilder(bitmap).build()
        val resultado = try {
            d.detectForVideo(imagen, timestampMs)
        } catch (e: Exception) {
            Log.e("DeepSign", "Error detectando cuerpo", e)
            return null
        } finally {
            imagen.close()
        }
        val pose = resultado.landmarks().firstOrNull() ?: return null
        if (pose.size < 17) return null
        return INDICES.map { i -> PointF(pose[i].x(), pose[i].y()) }
    }

    fun cerrar() {
        detector?.close()
    }

    companion object {
        // 0 nariz, 7 oreja izq, 8 oreja der, 11-12 hombros, 13-14 codos, 15-16 muñecas
        val INDICES = listOf(0, 7, 8, 11, 12, 13, 14, 15, 16)
    }
}