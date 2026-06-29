package com.spacemishka.app.ft_81xcompanion.service

import java.util.Locale

object MorseTranslator {
    private val morseMap = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '@' to ".--.-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-",
        '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...",
        ';' to "-.-.-.", '$' to "...-..-", '!' to "-.-.--", '\'' to ".----."
    )

    /**
     * Converts a single character to its Morse code representation.
     * Returns null if the character cannot be translated.
     */
    fun getMorse(char: Char): String? {
        return morseMap[char.uppercaseChar()]
    }
}
