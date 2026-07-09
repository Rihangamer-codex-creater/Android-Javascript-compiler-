package com.example.ui.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceAssistant(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isEnabled: Boolean = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("VoiceAssistant", "Failed to create TextToSpeech engine", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceAssistant", "Language is not supported or missing data")
            } else {
                isInitialized = true
                Log.d("VoiceAssistant", "TTS initialized successfully")
            }
        } else {
            Log.e("VoiceAssistant", "Initialization failed")
        }
    }

    fun speak(text: String, overrideEnabled: Boolean = false) {
        if ((isEnabled || overrideEnabled) && isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceAssistantUtteranceId")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("VoiceAssistant", "Failed to shutdown TTS", e)
        }
    }
}
