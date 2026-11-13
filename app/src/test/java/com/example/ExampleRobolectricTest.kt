package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleAppTest {

    @Test
    fun testSanitizeUrl() {
        val testUrl = "https://vt.tiktok.com//ZSppKT4hc/"
        val clean = testUrl.replace("(?<!:)//+".toRegex(), "/").trim()
        assertEquals("https://vt.tiktok.com/ZSppKT4hc/", clean)
    }

    @Test
    fun testFormatFiltering() {
        val formats = listOf(
            mapOf("has_video" to true, "has_audio" to true),
            mapOf("has_video" to true, "has_audio" to false)
        )
        val filtered = formats.filter { it["has_video"] == true && it["has_audio"] == true }
        assertEquals(1, filtered.size)
        assertTrue(filtered.first()["has_audio"] == true)
    }
}
