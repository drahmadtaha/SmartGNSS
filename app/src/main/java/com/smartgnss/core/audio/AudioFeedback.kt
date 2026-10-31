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

    // Louder, separate generator for stakeout proximity so it can be heard
    // outdoors. STREAM_ALARM follows the alarm volume (usually high) and the
    // generator volume is maxed.
    private var alarmTone: ToneGenerator? = null

    init {
        runCatching {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        }
        runCatching {
            alarmTone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
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

    /**
     * Stakeout proximity that gets louder/sharper the closer you are.
     * [level] 0 (far) … 3 (very close). Uses the alarm stream at max volume and
     * a higher-pitched, longer tone as level rises so it's clearly audible
     * outdoors and intensifies on approach.
     */
    fun proximity(level: Int) {
        if (!soundEnabled) return
        val t = alarmTone ?: tone ?: return
        val (type, ms) = when (level.coerceIn(0, 3)) {
            0 -> ToneGenerator.TONE_PROP_BEEP to 50
            1 -> ToneGenerator.TONE_CDMA_PIP to 70
            2 -> ToneGenerator.TONE_CDMA_HIGH_L to 110
            else -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 160
        }
        runCatching { t.startTone(type, ms) }
    }

    /** "On point" loud double tone for stakeout success (alarm stream). */
    fun onPoint() {
        if (!soundEnabled) return
        val t = alarmTone ?: tone ?: return
        runCatching { t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250) }
    }

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
        runCatching { alarmTone?.release() }
        runCatching { pool?.release() }
        tts = null; tone = null; alarmTone = null; pool = null
    }
}
