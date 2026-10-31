package com.customchess

import com.customchess.net.parseProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class LichessProfileTest {
    @Test
    fun readsNameAndRatingsOfPlayedPerfsOnly() {
        val json = """
            {"id":"foo","username":"Foo","perfs":{
              "blitz":{"games":120,"rating":1523,"rd":60,"prog":3},
              "rapid":{"games":0,"rating":1500,"prov":true},
              "classical":{"games":5,"rating":1710}}}
        """.trimIndent()
        val p = parseProfile(json)
        assertEquals("Foo", p.name)
        assertEquals(listOf(1523, 0, 1710), p.ratings)
    }

    @Test
    fun fallsBackToIdAndToleratesMissingPerfs() {
        val p = parseProfile("""{"id":"bar"}""")
        assertEquals("bar", p.name)
        assertEquals(listOf(0, 0, 0), p.ratings)
    }
}
