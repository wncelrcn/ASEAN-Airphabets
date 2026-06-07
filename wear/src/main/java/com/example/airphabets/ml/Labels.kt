package com.example.airphabets.ml

/**
 * Label mapping for model outputs. Adjust to match your training label order.
 */
object Labels {
    val CHARACTERS: List<String> = listOf(
        // Lowercase letters a-z (indices 0-25)
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        // Uppercase letters A-Z (indices 26-51)
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )
}
