package education.studyspot.app.ui.settings

import education.studyspot.app.core.AppJson
import education.studyspot.app.model.Connections
import education.studyspot.app.model.IcalAddResult
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.model.IcalFeeds
import education.studyspot.app.model.IcalSyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Calendar links: decoding server/src/functions/ical.js responses, and the Settings status line. */
class IcalLinksTest {

    @Test
    fun decodesFeedList() {
        // icalListFeeds -> { feeds: [publicFeed(row)] }; one malformed entry is skipped.
        val json = """
            {"feeds":[
              {"id":"7b0c","label":"AP Biology","calendar_name":"AP Biology","url_hint":"calendar.google.com/…/basic.ics",
               "mode":"auto","last_synced_at":"2026-11-18T14:55:00.000Z","last_error":null,"last_imported":3,
               "created_at":"2026-11-01T10:00:00.000Z"},
              {"id":"9d2e","label":null,"calendar_name":"Alex Rivera","mode":"all","last_synced_at":null,
               "last_error":"Google returned 404","last_imported":"0"},
              "garbage"
            ]}
        """.trimIndent()
        val feeds = AppJson.decodeFromString(IcalFeeds.serializer(), json).feeds
        assertEquals(2, feeds.size)
        val bio = feeds[0]
        assertEquals("7b0c", bio.id)
        assertEquals("AP Biology", bio.displayName)
        assertEquals(Instant.parse("2026-11-18T14:55:00Z"), bio.lastSyncedAt)
        assertEquals(3, bio.lastImported)
        assertFalse(bio.importsAll)
        assertNull(bio.lastError)
        val main = feeds[1]
        assertEquals("Alex Rivera", main.displayName)
        assertTrue(main.importsAll)
        assertEquals("Google returned 404", main.lastError)
        assertEquals("Google Calendar", IcalFeed(id = "x").displayName)
        assertEquals(emptyList<IcalFeed>(), AppJson.decodeFromString(IcalFeeds.serializer(), "{}").feeds)
    }

    @Test
    fun decodesAddAndSyncResults() {
        val add = AppJson.decodeFromString(
            IcalAddResult.serializer(),
            """{"ok":true,"feed":{"id":"1","label":"Chemistry","mode":"auto"},"imported":5,"updated":0,"reviewed":7,"events":40}""",
        )
        assertEquals(5, add.imported)
        assertEquals("Chemistry", add.feed?.displayName)
        val sync = AppJson.decodeFromString(
            IcalSyncResult.serializer(),
            """{"synced":true,"feeds":2,"imported":1,"updated":2,"errors":[{"id":"9","label":"History","error":"Calendar not public"}]}""",
        )
        assertEquals(1, sync.imported)
        assertEquals("History", sync.errors.single().label)
    }

    @Test
    fun connectionsCountCalendarLinks() {
        val c = AppJson.decodeFromString(Connections.serializer(), """{"calendar":false,"classroom":false,"blackboard":false,"ical":true}""")
        assertTrue(c.ical)
        assertTrue(c.any)
        assertFalse(AppJson.decodeFromString(Connections.serializer(), """{"calendar":false}""").any)
    }

    @Test
    fun statusLine() {
        val now = Instant.parse("2026-11-18T15:00:00Z")
        fun feed(minutesAgo: Long?, imported: Int = 0, error: String? = null) =
            IcalFeed(id = "1", lastSyncedAt = minutesAgo?.let { now.minusSeconds(it * 60) }, lastImported = imported, lastError = error)

        assertEquals(IcalLinks.Status("Synced 5 min ago · 3 new", false), IcalLinks.status(feed(5, 3), now))
        assertEquals("Synced just now", IcalLinks.status(feed(0), now).text)
        assertEquals("Synced 2 hr ago", IcalLinks.status(feed(125), now).text)
        assertEquals("Synced 1 day ago · 1 new", IcalLinks.status(feed(60 * 30, 1), now).text)
        assertEquals("Synced 3 days ago", IcalLinks.status(feed(60 * 24 * 3), now).text)
        assertEquals("Not synced yet", IcalLinks.status(feed(null), now).text)
        assertEquals(IcalLinks.Status("Couldn't sync: Calendar not public", true), IcalLinks.status(feed(5, 3, "Calendar not public"), now))
    }

    @Test
    fun importedMessageAndBold() {
        assertEquals("Imported 4 assignments from AP Biology.", IcalLinks.importedMessage(4, "AP Biology"))
        assertEquals("Imported 1 assignment from AP Biology.", IcalLinks.importedMessage(1, "AP Biology"))
        assertEquals("No assignments found yet — new ones will appear automatically.", IcalLinks.importedMessage(0, "X"))
        val s = IcalLinks.bold("choose **Settings and sharing** now")
        assertEquals("choose Settings and sharing now", s.text)
        assertEquals(1, s.spanStyles.size)
        assertEquals("Settings and sharing", s.text.substring(s.spanStyles[0].start, s.spanStyles[0].end))
    }
}
