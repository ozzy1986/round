package com.raund.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun parseDurationExpression_plainNumber() {
        assertEquals(60, parseDurationExpression("60"))
        assertEquals(300, parseDurationExpression("300"))
    }

    @Test
    fun parseDurationExpression_multiply() {
        assertEquals(300, parseDurationExpression("60*5"))
        assertEquals(300, parseDurationExpression("60 * 5"))
    }

    @Test
    fun parseDurationExpression_add() {
        assertEquals(150, parseDurationExpression("120+30"))
        assertEquals(150, parseDurationExpression("120 + 30"))
    }

    @Test
    fun parseDurationExpression_addAndMultiply() {
        assertEquals(330, parseDurationExpression("60*5+30"))
        assertEquals(330, parseDurationExpression("60 * 5 + 30"))
    }

    @Test
    fun parseDurationExpression_invalidReturnsNull() {
        assertNull(parseDurationExpression(""))
        assertNull(parseDurationExpression("  "))
        assertNull(parseDurationExpression("60a"))
        assertNull(parseDurationExpression("60-30"))
        assertNull(parseDurationExpression("60/2"))
    }
}
