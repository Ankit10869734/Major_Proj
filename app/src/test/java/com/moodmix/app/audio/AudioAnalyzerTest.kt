package com.moodmix.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AudioAnalyzerTest {

    @Test
    fun testSilenceHandling() {
        val silentPcm = FloatArray(11025 * 5) { 0.0f }

        val (energy, loudnessDb) = AudioAnalyzer.computeEnergyAndLoudness(silentPcm)
        assertEquals(0.0f, energy, 1e-5f)
        assertEquals(-80.0f, loudnessDb, 1e-5f)

        val (bpm, bpmConfidence) = AudioAnalyzer.estimateBpmFromPcm(silentPcm, 11025)
        assertNull(bpm)
        assertEquals(0.0f, bpmConfidence, 1e-5f)

        val (key, keyConfidence) = AudioAnalyzer.estimateKeyFromPcm(silentPcm, 11025)
        assertNull(key)
        assertEquals(0.0f, keyConfidence, 1e-5f)

        val mood = AudioAnalyzer.classifyMood(energy, bpm, key)
        assertEquals("Quiet / Ambient", mood)
    }

    @Test
    fun testVeryShortAudio() {
        val shortPcm = FloatArray(100) { 0.1f }

        val (bpm, bpmConfidence) = AudioAnalyzer.estimateBpmFromPcm(shortPcm, 11025)
        assertNull(bpm)
        assertEquals(0.0f, bpmConfidence, 1e-5f)

        val (key, keyConfidence) = AudioAnalyzer.estimateKeyFromPcm(shortPcm, 11025)
        assertNull(key)
        assertEquals(0.0f, keyConfidence, 1e-5f)
    }

    @Test
    fun testSyntheticBpmPulse120() {
        val sampleRate = 11025
        val durationSec = 10
        val totalSamples = sampleRate * durationSec
        val pcm = FloatArray(totalSamples)

        // 120 BPM = 2 beats per second = pulse every 5512.5 samples
        val samplesPerBeat = sampleRate / 2
        for (i in 0 until totalSamples) {
            if (i % samplesPerBeat < 100) {
                pcm[i] = 0.8f
            } else {
                pcm[i] = 0.01f
            }
        }

        val (bpm, confidence) = AudioAnalyzer.estimateBpmFromPcm(pcm, sampleRate)
        assertNotNull(bpm)
        assertTrue("Expected BPM around 120, got $bpm", bpm in 110..130)
        assertTrue("Expected positive confidence, got $confidence", confidence > 0.2f)
    }

    @Test
    fun testNoisyAudioNumericStability() {
        val random = Random(42)
        val noisyPcm = FloatArray(11025 * 4) { (random.nextFloat() * 2f) - 1f }

        val (energy, loudnessDb) = AudioAnalyzer.computeEnergyAndLoudness(noisyPcm)
        assertTrue("Energy should be in 0..1 range", energy in 0.0f..1.0f)
        assertTrue("Loudness DBFS should be in -80..0 range", loudnessDb in -80.0f..0.0f)

        val curve = AudioAnalyzer.computeEnergyCurve(noisyPcm, 60)
        assertEquals(60, curve.size)
        for (valInCurve in curve) {
            assertTrue("Curve value should be valid number", !valInCurve.isNaN() && !valInCurve.isInfinite())
        }
    }

    @Test
    fun testMoodClassificationRules() {
        assertEquals("Upbeat / Euphoric", AudioAnalyzer.classifyMood(0.7f, 125, "C major"))
        assertEquals("Intense / Dramatic", AudioAnalyzer.classifyMood(0.7f, 130, "A minor"))
        assertEquals("High Energy / Driving", AudioAnalyzer.classifyMood(0.7f, null, null))
        assertEquals("Energetic / Rhythmic", AudioAnalyzer.classifyMood(0.45f, 120, "D major"))
        assertEquals("Balanced / Flowing", AudioAnalyzer.classifyMood(0.45f, 90, "E minor"))
        assertEquals("Peaceful / Gentle", AudioAnalyzer.classifyMood(0.2f, 80, "F major"))
        assertEquals("Melancholic / Calm", AudioAnalyzer.classifyMood(0.2f, 75, "G minor"))
        assertEquals("Quiet / Ambient", AudioAnalyzer.classifyMood(0.05f, 120, "C major"))
    }

    @Test
    fun testEnergyCurveAndSectionDetection() {
        val pcm = FloatArray(11025 * 30) { 0.2f }
        // Create an energy jump at 15 seconds (segment 30)
        for (i in (11025 * 15) until pcm.size) {
            pcm[i] = 0.9f
        }

        val curve = AudioAnalyzer.computeEnergyCurve(pcm, 60)
        assertEquals(60, curve.size)
        assertTrue("Second half of curve should have higher energy", curve[40] > curve[10])

        val sections = AudioAnalyzer.detectSections(curve, 30.0f)
        assertTrue("Should detect energy transition segments", sections.isNotEmpty())
    }
}
