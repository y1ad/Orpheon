/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.dj

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.spotui.di.Graph
import java.util.Locale
import java.util.UUID

/**
 * Native DJ: comments between songs with system TextToSpeech.
 * Inspired by Spotify DJ / DJ Livi.
 *
 * Does not require any external API or tokens.
 */
object DjManager : TextToSpeech.OnInitListener {

    private const val PREFS = "myspoti_prefs"
    private const val KEY_ENABLED = "dj_enabled"
    private const val KEY_NAME = "dj_name"
    private const val KEY_CHATTY = "dj_chatty" // 1, 3 or 5
    private const val KEY_VOICE = "dj_voice"   // system voice name or empty

    private var tts: TextToSpeech? = null
    private var ready = false
    private var songCount = 0
    private var lastGreeted = 0L

    /** Whether the native DJ is enabled. */
    var enabled by mutableStateOf(
        Graph.app.getSharedPreferences(PREFS, 0).getBoolean(KEY_ENABLED, true)
    )
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
        Graph.app.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) ensureTts() else shutdown()
    }

    var djName by mutableStateOf(
        Graph.app.getSharedPreferences(PREFS, 0).getString(KEY_NAME, null) ?: "Dj Eyal"
    )
        private set

    fun setDjName(value: String) {
        val clean = value.trim().ifEmpty { "Dj Eyal" }
        djName = clean
        Graph.app.getSharedPreferences(PREFS, 0).edit().putString(KEY_NAME, clean).apply()
    }

    /** How often the DJ talks: 1 = every song, 3, 5. */
    var chatty by mutableStateOf(
        Graph.app.getSharedPreferences(PREFS, 0).getInt(KEY_CHATTY, 3).coerceIn(1, 5)
    )
        private set

    fun setChatty(value: Int) {
        val v = value.coerceIn(1, 5)
        chatty = v
        Graph.app.getSharedPreferences(PREFS, 0).edit().putInt(KEY_CHATTY, v).apply()
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(Graph.app, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val locale = preferredLocale()
            tts?.language = locale
            // Prefer a Spanish (Mexico) voice when available
            tts?.voices?.firstOrNull { v ->
                v.locale.language == "es" && (v.locale.country == "MX" || v.locale.country == "US")
            }?.let { tts?.voice = it }
        }
    }

    private fun preferredLocale(): Locale {
        val appLang = try {
            Graph.app.resources.configuration.locales[0]
        } catch (_: Exception) {
            Locale.getDefault()
        }
        return if (appLang.language == "es") Locale("es", "MX") else Locale.US
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    /**
     * Called when a new track starts playing.
     */
    fun onTrackStarted(
        title: String?,
        artist: String?,
        album: String? = null,
        year: String? = null,
        bpm: Float? = null
    ) {
        if (!enabled) return
        ensureTts()
        if (!ready) return
        songCount++
        val now = System.currentTimeMillis()
        val shouldGreet = now - lastGreeted > 6 * 3600_000L // every 6 hours
        val shouldTalk = shouldGreet || (songCount - 1) % chatty == 0

        if (!shouldTalk) return

        if (shouldGreet) lastGreeted = now

        val text = buildComment(
            title = title?.takeIf { it.isNotBlank } ?: return,
            artist = artist?.takeIf { it.isNotBlank } ?: "",
            album = album,
            year = year,
            bpm = bpm,
            greet = shouldGreet
        )

        speak(text)
    }

    private fun buildComment(
        title: String,
        artist: String,
        album: String?,
        year: String?,
        bpm: Float?,
        greet: Boolean
    ): String {
        val es = preferredLocale().language == "es"
        val parts = mutableListOf<String>()

        if (greet) {
            parts += if (es) {
                "Hola, soy $djName. Te voy a acompanar con la mõsica."
            } else {
                "Hi, I'm $djName. I'll be keeping you company with the music."
            }
        }

        val introsEs = listOf(
            "Ahora suena \"$title\" de $artist.",
            "Esta es \"$title\", de $artist.",
            "Seguimos con $artist: \"$title\".",
            "Aquí va \"$title\" de $artist."
        )
        val introsEn = listOf(
            "Now playing \"$title\" by $artist.",
            "This is \"$title\" by $artist.",
            "Staying with $artist: \"$title\".",
            "Here's \"$title\" from $artist."
        )
        parts += (if (es) introsEs else introsEn).random()

        when {
            !album.isNullOrBlank() && Math.random() < 0.55 -> {
                val y = year?.takeIf { it.length == 4 }?.let { " ($it)" } ?: ""
                parts += if (es) {
                    "Es del album \"$album\"$y."
                } else {
                    "It's from the album \"$album\"$y."
                }
            }
            bpm != null && bpm > 0 && Math.random() < 0.4 -> {
                val rounded = bpm.toInt()
                parts += if (es) {
                    "Va a $rounded BPM."
                } else {
                    "It runs at $rounded BPM."
                }
            }
        }

        return parts.joinToString(" ")
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) return
        engine.stop()

        val params = Bundle()
        val utteranceId = UUID.randomUUID().toString()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
           @Deprecated("Deprecated in Java")
           override fun onError(utteranceId: String?) {}
           override fun onError(utteranceId: String?, errorCode: Int) {}
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /** Force a short spoken message (for testing or manual trigger). */
    fun say(text: String) {
        if (!enabled) return
        ensureTts()
        speak(text)
    }
}
