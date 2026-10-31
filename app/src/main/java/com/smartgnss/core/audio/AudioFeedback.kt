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
    private var proxSound = 0      // btn_trim_centre: approach beep (< 3 m)
    private var warnSound = 0      // sce_waring: looped while on point (< 3 cm)
    private var warnStreamId = 0
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
            val app = context.applicationContext
            clickSound = pool?.load(app, R.raw.click, 1) ?: 0
            proxSound = pool?.load(app, R.raw.btn_trim_centre, 1) ?: 0
            warnSound = pool?.load(app, R.raw.sce_waring, 1) ?: 0
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

    /** Stakeout approach beep (btn_trim_centre) — call repeatedly while
     *  3 cm ≤ distance < 3 m. [volume] 0..1 lets the caller make it louder (and
     *  the cadence faster) as the point gets closer. */
    fun stakeApproach(volume: Float = 1f) {
        if (!soundEnabled) return
        val p = pool ?: return
        val v = volume.coerceIn(0.2f, 1f)
        if (proxSound != 0) runCatching { p.play(proxSound, v, v, 1, 0, 1f) }
    }

    /** Start looping the on-point warning (sce_waring) and keep it playing until
     *  [stakeOnPointStop]. No-op if already looping. */
    fun stakeOnPointStart() {
        if (!soundEnabled) return
        val p = pool ?: return
        if (warnStreamId != 0 || warnSound == 0) return
        warnStreamId = runCatching { p.play(warnSound, 1f, 1f, 2, -1, 1f) }.getOrDefault(0)
    }

    /** Stop the looping on-point warning. */
    fun stakeOnPointStop() {
        val p = pool ?: return
        if (warnStreamId != 0) { runCatching { p.stop(warnStreamId) }; warnStreamId = 0 }
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
