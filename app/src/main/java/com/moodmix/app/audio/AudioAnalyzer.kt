package com.moodmix.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class SongAnalysis(
    val durationSec: Float,
    val bpm: Int?,
    val bpmConfidence: Float,
    val key: String?,
    val keyConfidence: Float,
    val energy: Float,
    val loudnessDb: Float,
    val mood: String,
    val energyCurve: List<Float>,
    val sections: List<Pair<Float, Float>>
)

object AudioAnalyzer {

    suspend fun analyze(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): SongAnalysis = withContext(Dispatchers.Default) {

        onProgress(0.05f)

        // 1. Multi-segment representative audio decoding to mono PCM @ 11025 Hz
        val decoded = decodeToMonoPcm(context, uri) { decodeProgress ->
            onProgress(0.05f + decodeProgress * 0.45f) // 0.05..0.50
        }

        val pcm = decoded.pcm
        val sampleRate = decoded.sampleRate
        val totalDurationSec = decoded.totalDurationSec

        if (pcm.isEmpty()) {
            throw IllegalArgumentException("Audio file contained no readable PCM data.")
        }

        onProgress(0.55f)

        // 2. Approximate RMS Loudness (dBFS) and Normalized Energy
        val (energy, loudnessDb) = computeEnergyAndLoudness(pcm)

        onProgress(0.65f)

        // 3. Energy Curve (60 segment breakdown across the analyzed PCM audio)
        val energyCurve = computeEnergyCurve(pcm, 60)

        // 4. Energy-Based Structural Segment Detection
        val sections = detectSections(energyCurve, totalDurationSec)

        onProgress(0.75f)

        // 5. BPM Estimation via Onset Spectral Flux & Autocorrelation
        val (estimatedBpm, bpmConfidence) = estimateBpmFromPcm(pcm, sampleRate)

        onProgress(0.88f)

        // 6. Musical Key Detection via Chroma Vectors & Profile Correlation
        val (detectedKey, keyConfidence) = estimateKeyFromPcm(pcm, sampleRate)

        onProgress(0.95f)

        // 7. Heuristic Acoustic Vibe / Mood Classification
        val mood = classifyMood(energy, estimatedBpm, detectedKey)

        onProgress(1.0f)

        SongAnalysis(
            durationSec = totalDurationSec,
            bpm = estimatedBpm,
            bpmConfidence = bpmConfidence,
            key = detectedKey,
            keyConfidence = keyConfidence,
            energy = energy,
            loudnessDb = loudnessDb,
            mood = mood,
            energyCurve = energyCurve,
            sections = sections
        )
    }

    private fun decodeToMonoPcm(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): DecodedPcm {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not read audio file: ${e.message}", e)
        }

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        var mime = ""

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (trackMime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                mime = trackMime
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in the selected file.")
        }

        extractor.selectTrack(audioTrackIndex)

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val durationSec = if (durationUs > 0) durationUs / 1_000_000f else 0f

        val targetSampleRate = 11025
        val targetMaxSamples = 120 * targetSampleRate // Up to 120s of PCM samples
        val rawPcm = FloatArray(targetMaxSamples)
        var pcmCount = 0

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            throw IllegalArgumentException("Unsupported audio format: $mime", e)
        }

        codec.configure(format, null, null, 0)
        codec.start()

        val step = max(1.0, sampleRate.toDouble() / targetSampleRate)
        val info = MediaCodec.BufferInfo()

        // Multi-segment representative sampling: sample 4 30-sec windows if long track
        val segmentsUs = if (durationUs <= 180_000_000L) {
            listOf(0L to (if (durationUs > 0L) durationUs else 120_000_000L))
        } else {
            val segLen = 30_000_000L
            listOf(
                0L to segLen,
                (durationUs * 0.25).toLong() to (durationUs * 0.25).toLong() + segLen,
                (durationUs * 0.50).toLong() to (durationUs * 0.50).toLong() + segLen,
                (durationUs * 0.75).toLong() to (durationUs * 0.75).toLong() + segLen
            )
        }

        var inputPos = 0.0

        for ((segIdx, seg) in segmentsUs.withIndex()) {
            if (pcmCount >= targetMaxSamples) break
            val (segStartUs, segEndUs) = seg
            if (segStartUs > 0) {
                extractor.seekTo(segStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
            }

            var isSegmentDone = false
            var isExtractorEof = false

            while (!isSegmentDone && pcmCount < targetMaxSamples) {
                if (!isExtractorEof) {
                    val inIndex = codec.dequeueInputBuffer(10000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (segEndUs > 0 && sampleTimeUs > segEndUs)) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEof = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10000L)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isSegmentDone = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        val totalShorts = shortBuffer.remaining()
                        val totalFrames = totalShorts / channelCount

                        var frameIdx = 0
                        while (frameIdx < totalFrames && pcmCount < targetMaxSamples) {
                            var sum = 0f
                            for (c in 0 until channelCount) {
                                if (shortBuffer.hasRemaining()) {
                                    sum += shortBuffer.get() / 32768.0f
                                }
                            }
                            val mono = sum / channelCount

                            while (inputPos <= frameIdx && pcmCount < targetMaxSamples) {
                                rawPcm[pcmCount++] = mono
                                inputPos += step
                            }
                            frameIdx++
                        }
                        inputPos -= totalFrames
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isExtractorEof) {
                    isSegmentDone = true
                }
            }

            val segProgress = (segIdx + 1).toFloat() / segmentsUs.size
            onProgress(segProgress)
        }

        try {
            codec.stop()
            codec.release()
            extractor.release()
        } catch (_: Exception) {}

        val finalPcm = rawPcm.copyOf(pcmCount)
        val finalDurationSec = if (durationSec > 0f) durationSec else (pcmCount.toFloat() / targetSampleRate)

        return DecodedPcm(finalPcm, targetSampleRate, finalDurationSec)
    }

    private data class DecodedPcm(
        val pcm: FloatArray,
        val sampleRate: Int,
        val totalDurationSec: Float
    )

    fun computeEnergyAndLoudness(pcm: FloatArray): Pair<Float, Float> {
        if (pcm.isEmpty()) return 0.0f to -80.0f
        var sumSq = 0.0
        for (sample in pcm) {
            sumSq += sample * sample
        }
        val rms = sqrt(max(1e-12, sumSq / pcm.size)).toFloat()

        if (rms < 1e-4f) {
            return 0.0f to -80.0f
        }

        val loudnessDb = (20.0 * log10(max(1e-4, rms.toDouble()))).toFloat().coerceIn(-80.0f, 0.0f)
        val energy = (rms * 4.5f).coerceIn(0.0f, 1.0f)
        return energy to loudnessDb
    }

    fun computeEnergyCurve(pcm: FloatArray, numSegments: Int = 60): List<Float> {
        if (pcm.isEmpty()) return List(numSegments) { 0.0f }
        val segmentSize = max(1, pcm.size / numSegments)
        val curve = FloatArray(numSegments)
        var maxRms = 1e-6f

        for (i in 0 until numSegments) {
            val start = i * segmentSize
            val end = min(pcm.size, start + segmentSize)
            var sumSq = 0.0
            for (j in start until end) {
                sumSq += pcm[j] * pcm[j]
            }
            val rms = sqrt(sumSq / max(1, end - start)).toFloat()
            curve[i] = rms
            if (rms > maxRms) maxRms = rms
        }

        if (maxRms < 1e-4f) {
            return List(numSegments) { 0.0f }
        }

        return curve.map { (it / maxRms).coerceIn(0.0f, 1.0f) }
    }

    fun detectSections(energyCurve: List<Float>, totalDurationSec: Float): List<Pair<Float, Float>> {
        val numSegments = energyCurve.size
        if (numSegments < 4 || totalDurationSec <= 0f) return listOf(0f to totalDurationSec)

        val minSectionLengthSec = 10.0f
        val minSegmentSpan = max(2, ((minSectionLengthSec / totalDurationSec) * numSegments).roundToInt())

        val boundaries = mutableListOf<Int>()
        boundaries.add(0)

        var lastBoundary = 0
        for (i in 1 until numSegments - 1) {
            val diff = abs(energyCurve[i + 1] - energyCurve[i - 1])
            if (diff > 0.30f && (i - lastBoundary) >= minSegmentSpan) {
                boundaries.add(i)
                lastBoundary = i
            }
        }
        if (boundaries.last() != numSegments) {
            boundaries.add(numSegments)
        }

        val result = mutableListOf<Pair<Float, Float>>()
        for (k in 0 until boundaries.size - 1) {
            val startSec = (boundaries[k].toFloat() / numSegments) * totalDurationSec
            val endSec = (boundaries[k + 1].toFloat() / numSegments) * totalDurationSec
            result.add(startSec to endSec)
        }
        return result
    }

    fun estimateBpmFromPcm(pcm: FloatArray, sampleRate: Int): Pair<Int?, Float> {
        val fftSize = 1024
        val hopSize = 256
        val numFrames = (pcm.size - fftSize) / hopSize
        if (numFrames < 16) return null to 0.0f

        // Check for silence / noise floor
        var totalAbs = 0.0
        for (sample in pcm) {
            totalAbs += abs(sample)
        }
        val avgAmp = totalAbs / pcm.size
        if (avgAmp < 1e-3) {
            return null to 0.0f // Silent audio
        }

        val prevSpectrum = FloatArray(fftSize / 2)
        val onsetEnvelope = FloatArray(numFrames)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val window = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
        }

        var maxFlux = 0.0f

        for (f in 0 until numFrames) {
            val offset = f * hopSize
            for (i in 0 until fftSize) {
                real[i] = pcm[offset + i] * window[i]
                imag[i] = 0f
            }

            fftRadix2(real, imag)

            var flux = 0f
            for (k in 0 until fftSize / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                val diff = mag - prevSpectrum[k]
                if (diff > 0) flux += diff
                prevSpectrum[k] = mag
            }
            onsetEnvelope[f] = flux
            if (flux > maxFlux) maxFlux = flux
        }

        if (maxFlux < 1e-3f) {
            return null to 0.0f // Insufficient spectral change/rhythm
        }

        // Autocorrelation over 60..180 BPM
        val frameRate = sampleRate.toDouble() / hopSize
        val minLag = max(1, (frameRate * 60.0 / 180.0).roundToInt())
        val maxLag = min(numFrames / 2, (frameRate * 60.0 / 60.0).roundToInt())

        var maxCorr = -1.0
        var bestLag = minLag
        var sumCorr = 0.0
        var countCorr = 0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until numFrames - lag) {
                corr += onsetEnvelope[i] * onsetEnvelope[i + lag]
            }
            sumCorr += corr
            countCorr++

            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        val avgCorr = if (countCorr > 0) sumCorr / countCorr else 1.0
        if (maxCorr <= 0.0 || avgCorr <= 0.0) {
            return null to 0.0f
        }

        var rawBpm = (frameRate * 60.0 / bestLag).roundToInt()

        // Octave folding with candidate scoring
        while (rawBpm < 70) rawBpm *= 2
        while (rawBpm > 170) rawBpm /= 2

        val peakToMeanRatio = maxCorr / avgCorr
        val confidence = ((peakToMeanRatio - 1.0) / 3.0).toFloat().coerceIn(0.0f, 0.95f)

        if (confidence < 0.25f) {
            return null to confidence
        }

        return rawBpm to confidence
    }

    fun estimateKeyFromPcm(pcm: FloatArray, sampleRate: Int): Pair<String?, Float> {
        val fftSize = 4096
        val hopSize = 2048
        val numFrames = (pcm.size - fftSize) / hopSize
        if (numFrames < 2) return null to 0.0f

        var totalAbs = 0.0
        for (sample in pcm) {
            totalAbs += abs(sample)
        }
        if (totalAbs / pcm.size < 1e-3) {
            return null to 0.0f // Silent audio
        }

        val chroma = FloatArray(12)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val window = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
        }

        val pitchNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        for (f in 0 until min(numFrames, 60)) {
            val offset = f * hopSize
            for (i in 0 until fftSize) {
                real[i] = pcm[offset + i] * window[i]
                imag[i] = 0f
            }

            fftRadix2(real, imag)

            for (k in 1 until fftSize / 2) {
                val freq = k.toDouble() * sampleRate / fftSize
                if (freq in 65.0..2100.0) {
                    val midiNote = 12.0 * (log2(freq / 440.0)) + 69.0
                    val pitchClass = ((midiNote.roundToInt() % 12) + 12) % 12
                    val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                    chroma[pitchClass] += mag
                }
            }
        }

        var sumChroma = 0f
        for (v in chroma) sumChroma += v
        if (sumChroma < 1e-4f) {
            return null to 0.0f
        }

        // Krumhansl-Schmuckler Profiles
        val majorProfile = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
        val minorProfile = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 2.69f, 3.34f, 3.17f, 3.28f)

        var bestCorr = -Float.MAX_VALUE
        var secondBestCorr = -Float.MAX_VALUE
        var bestKeyName = "C major"

        for (shift in 0 until 12) {
            val majorCorr = correlateChroma(chroma, majorProfile, shift)
            if (majorCorr > bestCorr) {
                secondBestCorr = bestCorr
                bestCorr = majorCorr
                bestKeyName = "${pitchNames[shift]} major"
            } else if (majorCorr > secondBestCorr) {
                secondBestCorr = majorCorr
            }

            val minorCorr = correlateChroma(chroma, minorProfile, shift)
            if (minorCorr > bestCorr) {
                secondBestCorr = bestCorr
                bestCorr = minorCorr
                bestKeyName = "${pitchNames[shift]} minor"
            } else if (minorCorr > secondBestCorr) {
                secondBestCorr = minorCorr
            }
        }

        val margin = if (bestCorr > 0) (bestCorr - secondBestCorr) / bestCorr else 0f
        val confidence = (margin * 3.5f).coerceIn(0.0f, 0.95f)

        if (confidence < 0.15f || bestCorr <= 0f) {
            return null to confidence
        }

        return bestKeyName to confidence
    }

    fun classifyMood(energy: Float, bpm: Int?, key: String?): String {
        if (energy < 0.08f) return "Quiet / Ambient"

        val isMajor = key?.contains("major", ignoreCase = true) == true
        val isMinor = key?.contains("minor", ignoreCase = true) == true

        return when {
            energy >= 0.58f && isMajor -> "Upbeat / Euphoric"
            energy >= 0.58f && isMinor -> "Intense / Dramatic"
            energy >= 0.58f -> "High Energy / Driving"
            energy in 0.30f..0.58f && (bpm ?: 0) >= 115 -> "Energetic / Rhythmic"
            energy in 0.30f..0.58f -> "Balanced / Flowing"
            isMajor -> "Peaceful / Gentle"
            else -> "Melancholic / Calm"
        }
    }

    private fun correlateChroma(chroma: FloatArray, profile: FloatArray, shift: Int): Float {
        var dot = 0f
        for (i in 0 until 12) {
            val profileIdx = (i - shift + 12) % 12
            dot += chroma[i] * profile[profileIdx]
        }
        return dot
    }

    private fun fftRadix2(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
