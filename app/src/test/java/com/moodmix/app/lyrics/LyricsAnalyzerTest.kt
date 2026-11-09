package com.moodmix.app.lyrics

import com.moodmix.app.ui.screens.tools.translateLyricsOnDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LyricsAnalyzerTest {

    @Test
    fun testEmptyAndPunctuationOnlyLyrics() {
        val emptyAnalysis = LyricsAnalyzer.analyze("")
        assertEquals(0, emptyAnalysis.lineCount)
        assertEquals(0, emptyAnalysis.wordCount)

        val punctAnalysis = LyricsAnalyzer.analyze("!!! ??? ... ---")
        assertEquals(0, punctAnalysis.lineCount)
        assertEquals(0, punctAnalysis.wordCount)
        assertEquals("The provided lyrics text is empty.", punctAnalysis.summary)
    }

    @Test
    fun testSingleLineLyrics() {
        val analysis = LyricsAnalyzer.analyze("Walking under bright neon lights tonight")
        assertEquals(1, analysis.lineCount)
        assertEquals(6, analysis.wordCount)
        assertTrue("Syllables per line should be > 0", analysis.avgSyllablesPerLine > 0)
    }

    @Test
    fun testContractionsStraightAndCurlyApostrophes() {
        val straightApostrophe = "I don't love you"
        val straightAnalysis = LyricsAnalyzer.analyze(straightApostrophe)
        assertTrue("Expected negative sentiment for 'don't love'", straightAnalysis.sentiment.contains("Negative"))

        val curlyApostrophe = "I don’t love you"
        val curlyAnalysis = LyricsAnalyzer.analyze(curlyApostrophe)
        assertTrue("Expected negative sentiment for 'don’t love'", curlyAnalysis.sentiment.contains("Negative"))
    }

    @Test
    fun testContractionsAndNegationAllVariations() {
        // Positive words preceded by contractions -> should be negated to negative sentiment
        val cantJoy = LyricsAnalyzer.analyze("I can't feel joy")
        assertTrue("Expected negative sentiment for 'can't feel joy'", cantJoy.sentiment.contains("Negative"))

        val wontSmile = LyricsAnalyzer.analyze("I won't smile today")
        assertTrue("Expected negative sentiment for 'won't smile'", wontSmile.sentiment.contains("Negative"))

        val isntHappy = LyricsAnalyzer.analyze("She isn't happy")
        assertTrue("Expected negative sentiment for 'isn't happy'", isntHappy.sentiment.contains("Negative"))

        val wouldntHope = LyricsAnalyzer.analyze("They wouldn't hope for peace")
        assertTrue("Expected negative sentiment for 'wouldn't hope'", wouldntHope.sentiment.contains("Negative"))

        // Negative words preceded by contractions -> should be negated to positive sentiment
        val wontPain = LyricsAnalyzer.analyze("I won't feel pain")
        assertTrue("Expected positive sentiment for 'won't feel pain'", wontPain.sentiment.contains("Positive"))

        val didntCry = LyricsAnalyzer.analyze("He didn't cry")
        assertTrue("Expected positive sentiment for 'didn't cry'", didntCry.sentiment.contains("Positive"))

        val doesntHurt = LyricsAnalyzer.analyze("It doesn't hurt anymore")
        assertTrue("Expected positive sentiment for 'doesn't hurt'", doesntHurt.sentiment.contains("Positive"))
    }

    @Test
    fun testNegationWithInterveningWords() {
        val lyrics = "I don't really love this cold dark place"
        val analysis = LyricsAnalyzer.analyze(lyrics)
        assertTrue("Intervening words should retain negation scope", analysis.sentiment.contains("Negative"))
    }

    @Test
    fun testNegationLineScopingDoesNotLeak() {
        val lyrics = """
            I don't
            love you and smile
        """.trimIndent()

        val analysis = LyricsAnalyzer.analyze(lyrics)
        // Negation on Line 1 ("I don't") should NOT leak into Line 2 ("love you and smile")
        assertTrue("Line 2 positive words should not be negated by Line 1", analysis.sentiment.contains("Positive"))
    }

    @Test
    fun testUnicodeCombiningMarksPreservation() {
        val hindiLyrics = """
            तुम ही हो सब कुछ मेरे
            हर पल तुम्हारा ही तो है
        """.trimIndent()
        val hindiAnalysis = LyricsAnalyzer.analyze(hindiLyrics)
        assertEquals(2, hindiAnalysis.lineCount)
        assertTrue("Hindi word count should be > 0", hindiAnalysis.wordCount > 0)
        assertEquals("Unavailable (English lexicon only)", hindiAnalysis.sentiment)
        assertTrue(hindiAnalysis.summary.contains("Sentiment analysis is unavailable for non-English lyrics"))

        val bengaliLyrics = "আমার সোনার বাংলা আমি তোমায় ভালোবাসি"
        val bengaliAnalysis = LyricsAnalyzer.analyze(bengaliLyrics)
        assertEquals(1, bengaliAnalysis.lineCount)
        assertTrue("Bengali word count should be > 0", bengaliAnalysis.wordCount > 0)
        assertEquals("Unavailable (English lexicon only)", bengaliAnalysis.sentiment)

        val tamilLyrics = "வணக்கம் என் அன்பு நண்பனே"
        val tamilAnalysis = LyricsAnalyzer.analyze(tamilLyrics)
        assertEquals(1, tamilAnalysis.lineCount)
        assertTrue("Tamil word count should be > 0", tamilAnalysis.wordCount > 0)
        assertEquals("Unavailable (English lexicon only)", tamilAnalysis.sentiment)
    }

    @Test
    fun testMultipleSectionHeadersWithoutBlankLines() {
        val lyrics = """
            [Verse 1]
            Shining bright into the night
            [Chorus]
            We are soaring higher
        """.trimIndent()

        val analysis = LyricsAnalyzer.analyze(lyrics)
        assertEquals(2, analysis.lineCount)
        assertEquals(2, analysis.sections.size)
        assertEquals("[Verse 1]", analysis.sections[0].header)
        assertEquals("[Chorus]", analysis.sections[1].header)
    }

    @Test
    fun testRhymeSchemeMoreThan26Groups() {
        val sb = StringBuilder()
        val vowelSuffixes = listOf(
            "ba", "be", "bi", "bo", "bu", "da", "de", "di", "do", "du",
            "fa", "fe", "fi", "fo", "fu", "ga", "ge", "gi", "go", "gu",
            "ka", "ke", "ki", "ko", "ku", "la", "le", "li", "lo", "lu"
        )
        for (suffix in vowelSuffixes) {
            sb.append("Sing $suffix\n")
        }

        val analysis = LyricsAnalyzer.analyze(sb.toString())
        val rhymes = analysis.sections.first().lineRhymes
        assertEquals(30, rhymes.size)

        assertEquals("A", rhymes[0].second)
        assertEquals("Z", rhymes[25].second)
        assertEquals("A1", rhymes[26].second)
        assertEquals("D1", rhymes[29].second)
    }

    @Test
    fun testLyricsWithoutRepeatedLines() {
        val lyrics = """
            First unique line here
            Second completely different phrase
            Third sentence goes away
        """.trimIndent()

        val analysis = LyricsAnalyzer.analyze(lyrics)
        assertTrue("Repeated lines should be empty", analysis.repeatedLines.isEmpty())
        assertTrue("Summary should accurately report no repeated refrains", analysis.summary.contains("No exact repeated refrain lines"))
    }

    @Test
    fun testVeryLargeLyricsInput() {
        val sb = StringBuilder()
        for (i in 1..1000) {
            sb.append("Line number $i in a very long song text\n")
        }

        val analysis = LyricsAnalyzer.analyze(sb.toString())
        assertEquals(1000, analysis.lineCount)
        assertTrue("Word count should exceed 5000", analysis.wordCount > 5000)
    }

    @Test
    fun testTranslationIdenticalLanguagesAndUnidentified() = runBlocking {
        try {
            translateLyricsOnDevice("Hello lyrics", "und", "en") {}
            fail("Expected IllegalArgumentException for unidentified source language")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unidentified"))
        }

        val sameLangResult = translateLyricsOnDevice("Hello lyrics", "en", "en") {}
        assertTrue(sameLangResult.contains("identical"))
    }
}
