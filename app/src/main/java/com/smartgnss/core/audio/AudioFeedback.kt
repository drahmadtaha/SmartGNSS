package com.smartgnss.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.smartgnss.R
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
    private var pool: SoundPool? = null
    private var clickSound = 0
    var soundEnabled = true
    var voiceEnabled = true

    init {
        runCatching {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        }
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
            clickSound = pool?.load(context.applicationContext, R.raw.click, 1) ?: 0
        }
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) runCatching { tts?.language = Locale.US }
            }
        }
    }

    /** Light click for buttons — plays the bundled click sound. */
    fun click() {
        if (!soundEnabled) return
        val p = pool
        if (p != null && clickSound != 0) {
            runCatching { p.play(clickSound, 0.9f, 0.9f, 1, 0, 1f) }
        } else tone(ToneGenerator.TONE_PROP_BEEP, 35)
    }

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
        runCatching { pool?.release() }
        tts = null; tone = null; pool = null
    }
}
