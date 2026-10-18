package com.shippopotamus.whisper

/**
 * Parameters for a [WhisperEngine.load] call.
 *
 * @property language ISO language code the model should transcribe in (e.g. "en", "es").
 *   Ignored by English-only models.
 * @property translate When true, whisper translates the transcription to English.
 * @property threads Native thread count for transcription. Null = auto-detect
 *   (half the available processors, clamped to 1..4).
 */
data class WhisperParams(
    val language: String = "en",
    val translate: Boolean = false,
    val threads: Int? = null,
)

/**
 * On-device speech-to-text engine backed by whisper.cpp. Implementations keep the
 * native context loaded between utterances; call [close] to release it.
 */
interface WhisperEngine {
    /** Loads the model at [modelPath], replacing any previously loaded model. */
    suspend fun load(modelPath: String, params: WhisperParams = WhisperParams())

    /** Transcribes 16 kHz mono PCM samples (normalized floats) with the loaded model. */
    suspend fun transcribe(samples: FloatArray): String

    /** True while a model context is loaded. */
    fun isLoaded(): Boolean

    /** Frees the native context. Safe to call when nothing is loaded. */
    suspend fun close()
}
