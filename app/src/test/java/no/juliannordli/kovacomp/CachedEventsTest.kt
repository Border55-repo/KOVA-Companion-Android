package no.juliannordli.kovacomp

import org.junit.Assert.*
import org.junit.Test

class CachedEventsTest {
    @Test fun malformedCacheDoesNotCrashStartup() {
        for (raw in listOf("broken", "{}", "[null,{}]", "")) assertTrue(decodeCachedEvents(raw).isEmpty())
    }
    @Test fun damagedRecordDoesNotDiscardValidRecords() {
        val raw = """[{}, {"id":"a","dateIso":"2026-10-01","dateLabel":"torsdag","time":"10:00","type":"Øvelse","description":"Test","sourceUrl":"https://example.invalid"}]"""
        assertEquals(listOf("a"), decodeCachedEvents(raw).map { it.id })
    }
}
