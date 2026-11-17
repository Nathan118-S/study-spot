package education.studyspot.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppConfigTest {
    @Test
    fun normalizesAddresses() {
        assertEquals("https://studyspot.education", AppConfig.normalize(" studyspot.education/ "))
        assertEquals("http://10.0.2.2:3001", AppConfig.normalize("http://10.0.2.2:3001//"))
        assertEquals("", AppConfig.normalize("  "))
    }

    @Test
    fun derivesApiBase() {
        assertEquals("https://studyspot.education/api", AppConfig.apiBase("https://studyspot.education"))
        assertEquals("https://x.example/api", AppConfig.apiBase("https://x.example/api/"))
    }

    @Test
    fun detectsLocalHosts() {
        assertTrue(AppConfig.isLocalHost(AppConfig.hostOf("http://localhost:5173")))
        assertTrue(AppConfig.isLocalHost("127.0.0.1"))
        assertFalse(AppConfig.isLocalHost(AppConfig.hostOf("https://studyspot.education")))
    }

    @Test
    fun parsesAndFormatsServerDates() {
        assertEquals(Instant.parse("2026-11-20T23:59:00Z"), Dates.parse("2026-11-20T23:59:00.000Z"))
        assertEquals(Instant.parse("2026-11-20T22:59:00Z"), Dates.parse("2026-11-20T23:59:00+01:00"))
        assertEquals("2026-11-20T23:59:00Z", Dates.format(Instant.parse("2026-11-20T23:59:00Z")))
    }
}
