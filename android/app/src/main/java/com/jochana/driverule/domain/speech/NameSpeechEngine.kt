package com.jochana.driverule.domain.speech

/** A single, explicitly started recognition session. Implementations must release capture on close. */
interface NameSpeechEngine {
    fun start(listener: Listener)

    fun close()

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onEnded()
        fun onError(error: NameSpeechError)
    }
}

enum class NameSpeechError {
    Unavailable,
    PermissionDenied,
    Microphone,
    NoSpeech,
    Timeout,
    Engine,
    UnsupportedLanguage,
}
