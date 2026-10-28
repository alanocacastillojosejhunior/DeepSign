package com.jose.deepsign

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val sonido = MediaPlayer.create(this, R.raw.sonido_guino)
        val imgLogo = findViewById<android.widget.ImageView>(R.id.imgLogo)

        // Apenas el logo tiene tamaño medido, arrancan sonido + guiño de inmediato
        imgLogo.post {
            sonido.start()
            hacerGuino(imgLogo) {
                // Al terminar el guiño, pasamos directo al menú
                startActivity(Intent(this, MainMenuActivity::class.java))
                finish()
            }
        }
    }

    /** Anima el círculo del logo como si fuera un ojo que se cierra y abre (guiño) */
    private fun hacerGuino(view: android.widget.ImageView, alTerminar: () -> Unit) {
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f

        view.animate()
            .scaleY(0.05f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { alTerminar() }
                    .start()
            }
            .start()
    }
}