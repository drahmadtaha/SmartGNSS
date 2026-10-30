package com.smartgnss.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * App-wide audio feedback: short tones for button clicks / measuring / stakeout
 * proximity, and spoken announcements (e.g. the fix quality when it changes).
 * All calls are best-effort and never throw into the UI.
 */
class AudioFeedback(context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var tone: ToneGenerator? = null
    var soundEnabled = true
    var voiceEnabled = true

    init {
        runCatching {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        }
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) runCatching { tts?.language = Locale.US }
            }
        }
    }

    /** Light click for buttons. */
    fun click() = tone(ToneGenerator.TONE_PROP_BEEP, 35)

    /** Confirmation tone when a point is recorded. */
    fun measure() = tone(ToneGenerator.TONE_PROP_ACK, 150)

    /** A single proximity beep for stakeout (call more often as you get closer). */
    fun proximity() = tone(ToneGenerator.TONE_PROP_BEEP, 60)

    /** "On point" double tone for stakeout success. */
    fun onPoint() = tone(ToneGenerator.TONE_PROP_BEEP2, 200)

    private fun tone(type: Int, ms: Int) {
        if (!soundEnabled) return
        runCatching { tone?.startTone(type, ms) }
    }

    /** Speak a short phrase (e.g. "RTK Fixed"), replacing anything queued. */
    fun speak(text: String) {
        if (!voiceEnabled || !ttsReady) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sg") }
    }

    fun release() {
        runCatching { tts?.shutdown() }
        runCatching { tone?.release() }
        tts = null; tone = null
    }
}
