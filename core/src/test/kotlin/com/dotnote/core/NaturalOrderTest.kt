package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sign

/** The tests of the natord crate (which uses the case-sensitive compare), plus case and Unicode checks. */
class NaturalOrderTest {
    /** Every pair must compare like its position in [strings]. */
    private fun checkTotalOrder(strings: List<String>) {
        for ((i, x) in strings.withIndex()) {
            for ((j, y) in strings.withIndex()) {
                assertEquals(i.compareTo(j).sign, NaturalOrder.compare(x, y).sign, "compare(\"$x\", \"$y\")")
            }
        }
    }

    @Test
    fun `numeric`() {
        checkTotalOrder(listOf("a", "a0", "a1", "a1a", "a1b", "a2", "a10", "a20"))
    }

    @Test
    fun `multiple parts`() {
        checkTotalOrder(listOf("x2-g8", "x2-y7", "x2-y8", "x8-y8"))
    }

    @Test
    fun `leading zeroes`() {
        checkTotalOrder(listOf("1.001", "1.002", "1.010", "1.02", "1.1", "1.3"))
    }

    @Test
    fun `longer`() {
        checkTotalOrder(
            listOf(
                "1-02",
                "1-2",
                "1-20",
                "10-20",
                "fred",
                "jane",
                "pic1",
                "pic2",
                "pic2a",
                "pic3",
                "pic4",
                "pic4   alpha",
                "pic 4 else",
                "pic4  last",
                "pic5",
                "pic5.07",
                "pic5.08",
                "pic5.13",
                "pic5.113",
                "pic 5 something",
                "pic 6",
                "pic   7",
                "pic100",
                "pic100a",
                "pic120",
                "pic121",
                "pic2000",
                "tom",
                "x2-g8",
                "x2-y7",
                "x2-y8",
                "x8-y8",
            ),
        )
    }

    @Test
    fun `ignores letter case`() {
        assertEquals(0, NaturalOrder.compare("Alpha", "alpha"))
        assertTrue(NaturalOrder.compare("alpha", "Zeta") < 0)
        assertTrue(NaturalOrder.compare("A2", "a10") < 0)
        assertEquals(0, NaturalOrder.compare("ÉCOLE", "école"))
    }

    @Test
    fun `compares code points, not utf-16 units`() {
        // U+FF5E (one UTF-16 unit) is smaller than U+1F600 (two units, starting with 0xD83D);
        // comparing UTF-16 units would get this backwards.
        assertTrue(NaturalOrder.compare("～", "😀") < 0)
        assertTrue(NaturalOrder.compare("😀2", "😀10") < 0)
        assertTrue(NaturalOrder.compare("日本2", "日本10") < 0)
    }

    @Test
    fun `only ascii digits are numbers`() {
        // Full-width digits are compared as ordinary characters, like desktop.
        assertTrue(NaturalOrder.compare("a１０", "a２") < 0)
    }
}
