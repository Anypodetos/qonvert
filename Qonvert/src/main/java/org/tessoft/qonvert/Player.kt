package org.tessoft.qonvert

/*
Copyright 2016, 2017 Maddie Abboud
Copyright 2022 Anypodetos (Michael Weber)

This file is part of Qonvert.

Qonvert is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Qonvert is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Qonvert. If not, see <https://www.gnu.org/licenses/>.

Contact: <https://lemizh.conlang.org/home/contact.php?about=qonvert>


OneTimeBuzzer is a buzzer that will end after the set duration has ended or stop() is called.
created by Maddie Abboud, based on a Stack Overflow thread
published under a GNU Lesser General Public License v3.0
<https://github.com/m-abboud/android-tone-player>

converted to Kotlin, with minor modifications, by Anypodetos.
*/

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.view.MotionEvent
import android.view.View
import kotlin.concurrent.thread
import kotlin.math.*

class OneTimeBuzzer(private var toneFreq: Double, private var volume: Int, private var duration: Double) {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playerWorker: Thread? = null

    fun play() {
        if (!isPlaying) {
            stop()
            isPlaying = true
            playerWorker = thread { playTone(toneFreq, duration) }
        }
    }

    fun stop() {
        isPlaying = false
        tryStopPlayer()
    }

    private fun tryStopPlayer() {
        isPlaying = false
        if (audioTrack != null) try {
            playerWorker?.interrupt()

            /* pause() appears to be more snappy in audio cutoff than stop() */
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
            audioTrack = null
        } catch(_: IllegalStateException) {
            /* Likely issue with concurrency, doesn't matter, since means it's stopped anyway; so we just eat the exception */
        }
    }

    /* below from http://stackoverflow.com/questions/2413426/playing-an-arbitrary-tone-with-android */

    private fun playTone(freqInHz: Double, seconds: Double) {
        val sampleRate = 8000
        val numSamples = ceil(seconds * sampleRate).toInt()
        val soundData = ByteArray(2 * numSamples)

        /* amplitude ramps as a percent of sample count */
        val rampUp = numSamples / 20
        val rampDown = numSamples / 4

        for (i in 0 until numSamples) {
            val v = (sin(freqInHz * 2 * PI * i / sampleRate) * 32767 * when(i) {
               in 0 until rampUp -> (1 + cos((i - rampUp) * PI / rampUp)) / 2
               in rampUp until numSamples - rampDown -> 1.0
               else -> (1 + cos((i - numSamples + rampDown) * PI / rampDown)) / 2
            }).toInt()

            /* convert to 16 bit pcm sound array; assumes the sample buffer is normalized */
            soundData[2 * i] = v.toByte()
            soundData[2 * i + 1] = (v shr 8).toByte()
        }

        try {
            audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioTrack.MODE_STREAM)
            val gain = volume / 100f
            audioTrack?.setStereoVolume(gain, gain)
            audioTrack?.play()
            audioTrack?.write(soundData, 0, soundData.size)
        } catch(_: Exception) { }

        try {
            tryStopPlayer()
        } catch(_: Exception) { }
    }
}

class WaveView(context: Context) : View(context) {

    var ratio = 1f
        set(value) { field = (if (value > 1) value else 1 / value) }
    var autoClose = true
    private var waveWidth = 0f
    private var waveHeight = 0f
    private val path = Path()
    private var oldX = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = resolveColor(context, R.attr.editTextColor)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        color = resolveColor(context, android.R.attr.textColorHint)
    }

    init {
        isClickable = true
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> oldX = event.x
                MotionEvent.ACTION_MOVE -> {
                    MainActivity.playPhaseShift += 10 * PI.toFloat() * (event.x - oldX) / waveWidth
                    while (MainActivity.playPhaseShift > 2 * PI) MainActivity.playPhaseShift -= 2 * PI.toFloat()
                    while (MainActivity.playPhaseShift < 0) MainActivity.playPhaseShift += 2 * PI.toFloat()
                    oldX = event.x
                    calcWave()
                }
            }
            if (autoClose) performClick()
            true
        }
        setOnClickListener {
            MainActivity.playDialogTimer?.cancel()
            autoClose = false
            invalidate()
        }
    }

    private fun calcWave() {
        if (waveWidth > 0) with(path) {
            reset()
            moveTo(waveWidth, waveHeight * 0.4f)
            lineTo(0f, waveHeight * 0.4f)
            for (x in 0..waveWidth.toInt()) {
                val t = 40 * PI.toFloat() * x / waveWidth
                lineTo(x.toFloat(), waveHeight * (2 - sin(t) - sin(t * ratio - MainActivity.playPhaseShift)) / 5)
            }
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        waveWidth = w.toFloat()
        waveHeight = h.toFloat()
        calcWave()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let{
            it.drawPath(path, linePaint)
            it.drawText(resources.getString(if (autoClose) R.string.interval_keep else R.string.interval_swipe),
                waveWidth / 2, waveHeight - textPaint.textSize / 2, textPaint)
        }
    }
}
