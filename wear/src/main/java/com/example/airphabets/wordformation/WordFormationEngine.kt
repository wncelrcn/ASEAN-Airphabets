package com.example.airphabets.wordformation

/**
 * Trie-based word formation engine for efficient word matching.
 * This provides instant word lookup from the word bank as letters are predicted.
 *
 * Note: All letter matching is case-insensitive. Letters with similar writing
 * structures (c, k, o, p, s, u, v, w, x, z) are handled by normalizing to uppercase.
 */
class WordFormationEngine {

    private val trie = Trie()
    private val wordBank = mutableSetOf<String>()

    /**
     * Load words from the word bank into the trie
     */
    fun loadWordBank(words: List<String>) {
        wordBank.clear()
        trie.clear()
        words.forEach { word ->
            val normalized = word.uppercase().trim()
            if (normalized.isNotEmpty()) {
                wordBank.add(normalized)
                trie.insert(normalized)
            }
        }
    }

    /**
     * Add a single word to the word bank
     */
    fun addWord(word: String) {
        val normalized = word.uppercase().trim()
        if (normalized.isNotEmpty()) {
            wordBank.add(normalized)
            trie.insert(normalized)
        }
    }

    /**
     * Check if the current letter sequence forms a complete word
     */
    fun isCompleteWord(letters: String): Boolean {
        return trie.search(letters.uppercase())
    }

    /**
     * Check if the current letter sequence is a valid prefix of any word
     */
    fun isValidPrefix(letters: String): Boolean {
        return trie.startsWith(letters.uppercase())
    }

    /**
     * Get all words that start with the given prefix
     */
    fun getWordsWithPrefix(prefix: String): List<String> {
        return trie.getWordsWithPrefix(prefix.uppercase())
    }

    /**
     * Analyze the current letter buffer and return formation result.
     * Matching is case-insensitive - all comparisons done in uppercase.
     */
    fun analyzeLetterBuffer(letterBuffer: List<String>): WordFormationResult {
        if (letterBuffer.isEmpty()) {
            return WordFormationResult.Empty
        }

        // Normalize to uppercase for case-insensitive matching
        val currentSequence = letterBuffer.joinToString("").uppercase()

        // Check if it's a complete word
        if (isCompleteWord(currentSequence)) {
            val possibleContinuations = getWordsWithPrefix(currentSequence)
                .filter { it.uppercase() != currentSequence }

            return WordFormationResult.CompleteWord(
                word = currentSequence,
                canContinue = possibleContinuations.isNotEmpty(),
                possibleContinuations = possibleContinuations.take(3) // Limit suggestions
            )
        }

        // Check if it's a valid prefix
        if (isValidPrefix(currentSequence)) {
            val possibleWords = getWordsWithPrefix(currentSequence)
            return WordFormationResult.ValidPrefix(
                prefix = currentSequence,
                possibleWords = possibleWords.take(5) // Limit suggestions
            )
        }

        // Not a valid prefix - might be starting a new word
        return WordFormationResult.InvalidSequence(
            sequence = currentSequence,
            lastValidPrefix = findLastValidPrefix(letterBuffer)
        )
    }

    /**
     * Find the longest valid prefix from the letter buffer.
     * Case-insensitive matching.
     */
    private fun findLastValidPrefix(letterBuffer: List<String>): String? {
        for (i in letterBuffer.size - 1 downTo 1) {
            val prefix = letterBuffer.take(i).joinToString("").uppercase()
            if (isValidPrefix(prefix) || isCompleteWord(prefix)) {
                return prefix
            }
        }
        return null
    }

    /**
     * Get all loaded words
     */
    fun getAllWords(): Set<String> = wordBank.toSet()

    /**
     * Get word count
     */
    fun getWordCount(): Int = wordBank.size
}

/**
 * Result of word formation analysis
 */
sealed class WordFormationResult {
    object Empty : WordFormationResult()

    data class CompleteWord(
        val word: String,
        val canContinue: Boolean,
        val possibleContinuations: List<String>
    ) : WordFormationResult()

    data class ValidPrefix(
        val prefix: String,
        val possibleWords: List<String>
    ) : WordFormationResult()

    data class InvalidSequence(
        val sequence: String,
        val lastValidPrefix: String?
    ) : WordFormationResult()
}

/**
 * Trie data structure for efficient prefix matching
 */
private class Trie {
    private val root = TrieNode()

    fun insert(word: String) {
        var current = root
        for (char in word) {
            current = current.children.getOrPut(char) { TrieNode() }
        }
        current.isEndOfWord = true
        current.word = word
    }

    fun search(word: String): Boolean {
        val node = findNode(word)
        return node?.isEndOfWord == true
    }

    fun startsWith(prefix: String): Boolean {
        return findNode(prefix) != null
    }

    fun getWordsWithPrefix(prefix: String): List<String> {
        val node = findNode(prefix) ?: return emptyList()
        val words = mutableListOf<String>()
        collectWords(node, words)
        return words
    }

    private fun findNode(prefix: String): TrieNode? {
        var current = root
        for (char in prefix) {
            current = current.children[char] ?: return null
        }
        return current
    }

    private fun collectWords(node: TrieNode, words: MutableList<String>) {
        if (node.isEndOfWord && node.word != null) {
            words.add(node.word!!)
        }
        for (child in node.children.values) {
            collectWords(child, words)
        }
    }

    fun clear() {
        root.children.clear()
    }
}

private class TrieNode {
    val children = mutableMapOf<Char, TrieNode>()
    var isEndOfWord = false
    var word: String? = null
}
