package com.scouty.app.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpellCorrectorTest {
    private val vocab = mapOf(
        "focul" to 50L,
        "foc" to 40L,
        "stinge" to 15L,
        "stingere" to 9L,
        "bricheta" to 21L,
        "entorsa" to 4L,
        "glezna" to 8L,
        "apa" to 30L,
        "adapost" to 12L,
        "amnar" to 6L
    )
    private val corrector = SpellCorrector(vocab)

    @Test
    fun doesNotMangleValidStemsThatArePrefixes() {
        // "sting" is a prefix of "stinge"/"stingere" -> valid stem, must not become "stang".
        assertEquals("sting", corrector.correctToken("sting"))
        // "focu" is a prefix of "focul" -> FTS prefix matching handles it, leave as-is.
        assertEquals("focu", corrector.correctToken("focu"))
    }

    @Test
    fun keepsKnownWords() {
        assertEquals("focul", corrector.correctToken("focul"))
        assertEquals("bricheta", corrector.correctToken("bricheta"))
    }

    @Test
    fun correctsSingleEditTypos() {
        assertEquals("bricheta", corrector.correctToken("brichetta"))
        assertEquals("entorsa", corrector.correctToken("entorsza"))
        assertEquals("adapost", corrector.correctToken("adaost"))
    }

    @Test
    fun leavesShortTokensUntouched() {
        assertEquals("kum", corrector.correctToken("kum"))
        assertEquals("fac", corrector.correctToken("fac"))
    }

    @Test
    fun leavesUnknownDistantTokens() {
        // far from any vocab word -> not corrected to garbage
        assertEquals("helicopter", corrector.correctToken("helicopter"))
    }

    @Test
    fun correctsWordsInsideAQuery() {
        assertEquals("cum aprind focul cu amnar", corrector.correctQuery("cum aprind focul cu amnnar"))
    }

    @Test
    fun emptyVocabIsNoOp() {
        val empty = SpellCorrector(emptyMap())
        assertEquals("brichetta", empty.correctQuery("brichetta"))
    }
}
