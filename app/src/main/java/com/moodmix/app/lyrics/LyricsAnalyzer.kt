package com.moodmix.app.lyrics

import java.util.Locale
import kotlin.math.abs

data class LyricsSection(
    val header: String?,
    val lines: List<String>,
    val lineRhymes: List<Pair<String, String>>
)

data class LyricsAnalysis(
    val lineCount: Int,
    val wordCount: Int,
    val uniqueWordCount: Int,
    val sections: List<LyricsSection>,
    val rhymeScheme: String,
    val rhymeGroups: Map<String, List<String>>,
    val avgSyllablesPerLine: Float,
    val sentiment: String,
    val sentimentScore: Float,
    val topRepeatedWords: List<Pair<String, Int>>,
    val repeatedLines: List<String>,
    val summary: String
)

object LyricsAnalyzer {

    private val positiveWords = setOf(
        "love", "happy", "smile", "sunshine", "light", "joy", "bright", "dance", "hope", "peace",
        "dream", "sweet", "beautiful", "free", "golden", "heart", "heaven", "paradise", "good", "great",
        "wonderful", "alive", "shine", "sing", "fly", "sky", "star", "warm", "gentle", "pure",
        "radiant", "blessed", "spark", "glory", "triumph", "forever", "together", "magic", "embrace", "soul",
        "spirit", "bloom", "grace", "harmony", "melody", "rhythm", "passion", "true", "comfort", "cozy",
        "bliss", "delight", "cherish", "treasure", "wonder", "laugh", "laughter", "cheer", "friend", "family",
        "home", "calm", "soothe", "serene", "soft", "shining", "kind", "angel", "miracle", "hero",
        "victory", "winner", "glow", "blooming", "soaring", "ecstasy", "feeling", "vibe", "groove", "grooving",
        "party", "celebrate", "celebration", "euphoria", "radiance", "optimism", "shimmer", "gleam", "sparkle", "breeze",
        "flower", "summer", "spring", "sweetheart", "darling", "honey", "beloved", "kiss", "hug", "desire",
        "faith", "trust", "inspire", "inspiration", "freedom", "soar", "rise", "lift", "higher", "brightest",
        "sun", "daylight", "morning", "clarity", "clear", "peaceful", "tranquil", "content", "thrill", "excited",
        "fun", "playful", "energy", "power", "strong", "courage", "bold", "unbroken", "resilient", "glimmer",
        "sweetness", "charm", "precious", "adore", "adoration", "amaze", "amazing", "awesome", "fantastic", "brilliant",
        "legend", "legendary", "classic", "perfect", "symphony", "serenade", "uplift", "soothing", "healing"
    )

    private val negativeWords = setOf(
        "pain", "sad", "dark", "night", "tear", "tears", "cry", "crying", "lonely", "alone",
        "cold", "broken", "bleed", "bleeding", "hurt", "hurting", "fear", "scared", "loss", "lost",
        "shadow", "ghost", "die", "dying", "death", "dead", "grave", "black", "empty", "hollow",
        "storm", "rain", "drown", "drowning", "fall", "falling", "hate", "hating", "anger", "angry",
        "rage", "scream", "shout", "curse", "devil", "demon", "hell", "sin", "guilt", "shame",
        "sorrow", "grief", "agony", "despair", "misery", "tragedy", "tragic", "lie", "lies", "liar",
        "cheat", "betray", "betrayal", "fake", "poison", "venom", "sick", "disease", "waste", "wasted",
        "ruin", "ruined", "destroy", "destruction", "burn", "burning", "ashes", "dust", "trap", "trapped",
        "prison", "cage", "chain", "chains", "bound", "slave", "dread", "panic", "anxiety", "suffering",
        "suffer", "sore", "wound", "scar", "scars", "bitter", "harsh", "cruel", "mean", "ugly",
        "numb", "blind", "silent", "silence", "goodbye", "fade", "fading", "gone", "away", "leave",
        "leaving", "nevermore", "doom", "gloom", "nightmare", "disaster", "fallout", "wreck", "shipwrecked",
        "shattered", "crushed", "defeated", "failure", "fail", "wrong", "fault", "blame", "regret", "remorse",
        "hopeless", "pointless", "worthless"
    )

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "in", "on", "at", "to", "is", "it", "my", "i", "you",
        "me", "of", "that", "this", "we", "they", "he", "she", "was", "be", "for", "with", "as",
        "are", "so", "but", "by", "from", "up", "down", "if", "out", "all", "your", "no", "not"
    )

    private val negations = setOf(
        "not", "never", "no", "don't", "doesn't", "didn't", "cannot", "can't", "won't",
        "wouldn't", "shouldn't", "couldn't", "ain't", "haven't", "hasn't", "hadn't",
        "wasn't", "weren't", "isn't", "aren't", "mustn't", "nobody", "nothing", "neither", "nor",
        "dont", "cant", "wont", "isnt", "didnt", "doesnt", "wouldnt", "shouldnt", "couldnt",
        "havent", "hasnt", "hadnt", "wasnt", "werent", "arent"
    )

    fun analyze(lyricsText: String): LyricsAnalysis {
        val rawLines = lyricsText.lines()
        val validLines = rawLines
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !(line.startsWith("[") && line.endsWith("]")) &&
                line.any { it.isLetterOrDigit() }
            }

        if (validLines.isEmpty()) {
            return LyricsAnalysis(
                lineCount = 0,
                wordCount = 0,
                uniqueWordCount = 0,
                sections = emptyList(),
                rhymeScheme = "N/A",
                rhymeGroups = emptyMap(),
                avgSyllablesPerLine = 0f,
                sentiment = "Neutral",
                sentimentScore = 0f,
                topRepeatedWords = emptyList(),
                repeatedLines = emptyList(),
                summary = "The provided lyrics text is empty."
            )
        }

        val allWords = mutableListOf<String>()
        val lineSyllables = mutableListOf<Int>()
        val lineCountsMap = mutableMapOf<String, Int>()

        // Line-scoped sentiment evaluation
        var posHits = 0
        var negHits = 0

        for (line in validLines) {
            // Unicode-aware tokenization preserving combining marks (\p{M}) and apostrophes
            val normalizedLine = line.replace(Regex("[\u2018\u2019\u201B\u02BC]"), "'")
            val words = normalizedLine.lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{M}\\p{N}'\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            allWords.addAll(words)
            val syllables = words.sumOf { countSyllables(it) }
            lineSyllables.add(syllables)

            val cleanLineKey = line.lowercase(Locale.ROOT)
            lineCountsMap[cleanLineKey] = (lineCountsMap[cleanLineKey] ?: 0) + 1

            // Line-scoped negation check (scope window of 3 words)
            var negationWindow = 0
            for (w in words) {
                val cleanToken = w.trim('\'')
                val isNegationWord = negations.contains(cleanToken) || cleanToken.endsWith("n't")

                if (isNegationWord) {
                    negationWindow = 3 // Negate next 3 words on the same line
                    continue
                }

                val cleanW = cleanToken.replace(Regex("[^\\p{L}\\p{M}]"), "")
                val isPos = positiveWords.contains(cleanW)
                val isNeg = negativeWords.contains(cleanW)

                if (isPos) {
                    if (negationWindow > 0) negHits++ else posHits++
                } else if (isNeg) {
                    if (negationWindow > 0) posHits++ else negHits++
                }

                if (negationWindow > 0) {
                    negationWindow--
                }
            }
        }

        val lineCount = validLines.size
        val wordCount = allWords.size
        val uniqueWordCount = allWords.toSet().size
        val avgSyllables = if (lineCount > 0) lineSyllables.sum().toFloat() / lineCount else 0f

        // Structural Section Breakdown (handles section headers even without blank lines between them)
        val parsedSections = parseSections(lyricsText)
        val rhymeGroupsMap = mutableMapOf<String, MutableList<String>>()
        val globalRhymeSchemeBuilder = StringBuilder()

        for (sec in parsedSections) {
            val secScheme = sec.lineRhymes.map { it.second }.joinToString("")
            if (globalRhymeSchemeBuilder.isNotEmpty()) globalRhymeSchemeBuilder.append(" ")
            globalRhymeSchemeBuilder.append(secScheme)

            for ((line, _) in sec.lineRhymes) {
                val rhymeKey = extractRhymeKey(line)
                rhymeGroupsMap.getOrPut(rhymeKey) { mutableListOf() }.add(line)
            }
        }

        val isNonEnglishScript = allWords.any { word ->
            word.any { c -> c.code > 0x024F }
        }

        val totalHits = posHits + negHits
        val (sentiment, sentimentScore) = when {
            isNonEnglishScript -> "Unavailable (English lexicon only)" to 0f
            totalHits == 0 -> "Neutral (Heuristic)" to 0f
            else -> {
                val score = (posHits - negHits).toFloat() / totalHits
                val label = when {
                    posHits > 0 && negHits > 0 && abs(posHits - negHits) <= 1 -> "Mixed (Heuristic)"
                    score > 0.15f -> "Positive (Heuristic)"
                    score < -0.15f -> "Negative (Heuristic)"
                    else -> "Neutral (Heuristic)"
                }
                label to score
            }
        }

        // Top repeated words
        val wordFreq = mutableMapOf<String, Int>()
        for (w in allWords) {
            val cleanW = w.replace(Regex("[^\\p{L}\\p{M}\\p{N}]"), "")
            if (!stopWords.contains(cleanW) && cleanW.length >= 3) {
                wordFreq[cleanW] = (wordFreq[cleanW] ?: 0) + 1
            }
        }

        val topWords = wordFreq.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        // Repeated lines
        val repeatedLines = validLines
            .filter { (lineCountsMap[it.lowercase(Locale.ROOT)] ?: 0) >= 2 }
            .distinct()

        // Summary
        val summaryText = buildString {
            append("The lyrics contain $lineCount line(s) and $wordCount total word(s) ($uniqueWordCount unique word(s)), averaging ${"%.1f".format(avgSyllables)} estimated syllables per line. ")
            if (isNonEnglishScript) {
                append("Sentiment analysis is unavailable for non-English lyrics (English lexicon only). ")
            } else {
                append("Heuristic sentiment score is ${"%.2f".format(sentimentScore)} ($sentiment). ")
            }
            if (topWords.isNotEmpty()) {
                append("Top keywords: ${topWords.take(3).joinToString(", ") { it.first }}. ")
            } else {
                append("No top repeated keywords detected. ")
            }
            if (repeatedLines.isNotEmpty()) {
                append("Detected ${repeatedLines.size} repeated refrain line(s).")
            } else {
                append("No exact repeated refrain lines were detected.")
            }
        }

        return LyricsAnalysis(
            lineCount = lineCount,
            wordCount = wordCount,
            uniqueWordCount = uniqueWordCount,
            sections = parsedSections,
            rhymeScheme = globalRhymeSchemeBuilder.toString(),
            rhymeGroups = rhymeGroupsMap.mapValues { it.value.toList() },
            avgSyllablesPerLine = avgSyllables,
            sentiment = sentiment,
            sentimentScore = sentimentScore,
            topRepeatedWords = topWords,
            repeatedLines = repeatedLines,
            summary = summaryText
        )
    }

    private fun parseSections(lyricsText: String): List<LyricsSection> {
        val lines = lyricsText.lines()
        val sections = mutableListOf<LyricsSection>()

        var currentHeader: String? = null
        val currentLines = mutableListOf<String>()

        fun flushSection() {
            if (currentLines.isNotEmpty()) {
                val keyToLabel = mutableMapOf<String, String>()
                var groupCount = 0
                val lineRhymes = mutableListOf<Pair<String, String>>()

                for (line in currentLines) {
                    val rhymeKey = extractRhymeKey(line)
                    val label = keyToLabel.getOrPut(rhymeKey) {
                        indexToRhymeLabel(groupCount++)
                    }
                    lineRhymes.add(line to label)
                }

                sections.add(
                    LyricsSection(
                        header = currentHeader ?: "Section ${sections.size + 1}",
                        lines = ArrayList(currentLines),
                        lineRhymes = lineRhymes
                    )
                )
                currentLines.clear()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) {
                flushSection()
                continue
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                flushSection()
                currentHeader = line
            } else if (line.any { it.isLetterOrDigit() }) {
                currentLines.add(line)
            }
        }
        flushSection()

        return sections
    }

    private fun indexToRhymeLabel(index: Int): String {
        val baseChar = ('A'.code + (index % 26)).toChar()
        val cycle = index / 26
        return if (cycle == 0) "$baseChar" else "$baseChar$cycle"
    }

    private fun extractRhymeKey(line: String): String {
        val words = line.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return "x"

        val lastWord = words.last()

        val normalized = lastWord
            .replace("ight", "ite")
            .replace("ay", "ey")
            .replace("ee", "ey")
            .replace("tion", "shun")
            .replace("sion", "shun")
            .replace("ea", "ee")
            .replace("ck", "k")

        val match = Regex("[\\p{L}\\p{M}]+$").find(normalized)
        val clean = match?.value ?: normalized
        return if (clean.length >= 3) clean.takeLast(3) else clean
    }

    private fun countSyllables(word: String): Int {
        val w = word.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{M}]"), "")
        if (w.isEmpty()) return 0
        if (w.length <= 3) return 1

        var count = Regex("[aeiouy]+").findAll(w).count()
        if (w.endsWith("e") && !w.endsWith("le")) {
            count--
        }
        if (w.endsWith("ed") && !w.endsWith("ted") && !w.endsWith("ded")) {
            count--
        }
        return count.coerceAtLeast(1)
    }
}
