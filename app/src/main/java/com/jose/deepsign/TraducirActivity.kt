package com.jose.deepsign

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class TraducirActivity : AppCompatActivity() {

    private lateinit var txtVoz: TextView
    private var reconocedor: SpeechRecognizer? = null

    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                escuchar()
            } else {
                Toast.makeText(
                    this,
                    "Necesito permiso del micrófono para escucharte",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traducir)

        txtVoz = findViewById(R.id.txtVoz)

        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }

        findViewById<View>(R.id.cardGestos).setOnClickListener {
            startActivity(Intent(this, TraductorActivity::class.java))
        }
        findViewById<View>(R.id.cardTexto).setOnClickListener {
            startActivity(Intent(this, EscribirActivity::class.java))
        }
        findViewById<View>(R.id.cardVoz).setOnClickListener {
            verificarPermisoYEscuchar()
        }
    }

    private fun verificarPermisoYEscuchar() {
        val tiene = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (tiene) escuchar() else pedirPermiso.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun escuchar() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "Este teléfono no tiene reconocimiento de voz",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        reconocedor?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        reconocedor = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                txtVoz.text = "Te escucho, habla ahora..."
            }

            override fun onResults(results: Bundle?) {
                val texto = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (!texto.isNullOrBlank()) {
                    txtVoz.text = texto
                    Almacen.agregarHistorial(this@TraducirActivity, texto, "Voz")
                } else {
                    txtVoz.text = "No se entendió lo que dijiste"
                }
            }

            override fun onError(error: Int) {
                txtVoz.text = mensajeError(error)
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        r.startListening(intent)
    }

    private fun mensajeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No se escuchó ninguna frase. Toca Voz e intenta de nuevo."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Falta el permiso del micrófono para DeepSign."
        11 ->
            "El servicio de voz de Google no respondió. Prueba en un celular físico."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Sin conexión a internet. Conéctate e intenta de nuevo."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Tu teléfono no tiene el idioma español para voz."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "El reconocedor está ocupado. Intenta de nuevo en unos segundos."
        else -> "No se pudo escuchar (código $error)."
    }

    override fun onDestroy() {
        reconocedor?.destroy()
        super.onDestroy()
    }
}