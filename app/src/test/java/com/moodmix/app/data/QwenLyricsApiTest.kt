package com.moodmix.app.data

import com.moodmix.app.lyrics.LyricsAnalyzer
import com.moodmix.app.network.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenLyricsApiTest {

    @Test
    fun testQwenApiConfigEndpoint() {
        assertEquals("https://valiant-program-parched.ngrok-free.dev", ApiConfig.QWEN_BASE_URL)
        assertEquals("https://valiant-program-parched.ngrok-free.dev/analyze", ApiConfig.QWEN_ANALYZE_ENDPOINT)
    }

    @Test
    fun testQwenMarkdownFenceStripping() {
        val markdownJson = """
            ```json
            {
                "summary": "This song expresses deep affection and romance.",
                "sentiment": "Positive (Qwen LLM)",
                "sentimentScore": 0.85
            }
            ```
        """.trimIndent()

        fun stripFences(text: String): String {
            var trimmed = text.trim()
            if (trimmed.startsWith("```")) {
                val firstLineBreak = trimmed.indexOf('\n')
                if (firstLineBreak != -1) {
                    trimmed = trimmed.substring(firstLineBreak + 1)
                }
                if (trimmed.endsWith("```")) {
                    trimmed = trimmed.substring(0, trimmed.length - 3)
                }
            }
            return trimmed.trim()
        }

        val cleaned = stripFences(markdownJson)

        assertTrue(cleaned.startsWith("{"))
        assertTrue(cleaned.endsWith("}"))
        assertTrue(cleaned.contains("This song expresses deep affection and romance."))
    }

    @Test
    fun testHeuristicFallbackOnApiFailure() {
        val sampleLyrics = """
            I love the way you smile at me
            You make my world so bright and free
        """.trimIndent()

        val analysis = LyricsAnalyzer.analyze(sampleLyrics)

        assertNotNull(analysis)
        assertTrue(analysis.lineCount > 0)
        assertTrue(analysis.wordCount > 0)
        assertNotNull(analysis.summary)
    }
}
