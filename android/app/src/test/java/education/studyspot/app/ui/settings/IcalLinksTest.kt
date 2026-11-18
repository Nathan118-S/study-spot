package education.studyspot.app.ui.settings

import education.studyspot.app.core.AppJson
import education.studyspot.app.model.Connections
import education.studyspot.app.model.IcalAddResult
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.model.IcalFeeds
import education.studyspot.app.model.IcalImportResult
import education.studyspot.app.model.IcalKind
import education.studyspot.app.model.IcalSyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
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

    @Test
    fun decodesFeedKinds() {
        val json = """
            {"feeds":[
              {"id":"g","label":"AP Biology","kind":"google","mode":"auto"},
              {"id":"b","label":"Blackboard","kind":"blackboard","url_hint":"school.edu/…/learn.ics (Blackboard)","mode":"all"},
              {"id":"b2","label":null,"calendar_name":null,"kind":"blackboard","mode":"all"},
              {"id":"old","label":"Old link","mode":"auto"}
            ]}
        """.trimIndent()
        val feeds = AppJson.decodeFromString(IcalFeeds.serializer(), json).feeds
        assertEquals(listOf(IcalKind.Google, IcalKind.Blackboard, IcalKind.Blackboard, IcalKind.Google), feeds.map { it.feedKind })
        assertEquals("Blackboard", feeds[2].displayName)
        assertEquals("Google Calendar", IcalFeed(id = "x", kind = "something new").displayName)
        assertEquals(listOf("g", "old"), IcalLinks.feeds(feeds, IcalKind.Google).map { it.id })
        assertEquals(listOf("b", "b2"), IcalLinks.feeds(feeds, IcalKind.Blackboard).map { it.id })
    }

    @Test
    fun decodesLmsKinds() {
        val json = """
            {"feeds":[
              {"id":"c","label":"Canvas","kind":"canvas","url_hint":"school.instructure.com/…/user_….ics","mode":"auto"},
              {"id":"s","label":null,"calendar_name":null,"kind":"Schoology","mode":"all"},
              {"id":"d","label":"","kind":" brightspace ","mode":"auto"},
              {"id":"b","label":"Blackboard","kind":"blackboard","mode":"all"},
              {"id":"g","label":"AP Biology","kind":"google","mode":"auto"}
            ]}
        """.trimIndent()
        val feeds = AppJson.decodeFromString(IcalFeeds.serializer(), json).feeds
        assertEquals(
            listOf(IcalKind.Canvas, IcalKind.Schoology, IcalKind.Brightspace, IcalKind.Blackboard, IcalKind.Google),
            feeds.map { it.feedKind },
        )
        assertEquals(listOf("Canvas", "Schoology", "Brightspace"), feeds.take(3).map { it.displayName })
        assertEquals(listOf("c", "s", "d"), IcalLinks.lmsFeeds(feeds).map { it.id })
        assertEquals(listOf("c"), IcalLinks.feeds(feeds, IcalKind.Canvas).map { it.id })
        assertEquals(listOf("b"), IcalLinks.feeds(feeds, IcalKind.Blackboard).map { it.id })
        assertEquals(listOf("g"), IcalLinks.feeds(feeds, IcalKind.Google).map { it.id })
        assertEquals(listOf("canvas", "schoology", "brightspace"), IcalKind.LMS.map { it.key })
    }

    @Test
    fun lmsStatusLineNamesTheSystem() {
        val now = Instant.parse("2026-11-18T15:00:00Z")
        val canvas = IcalFeed(id = "1", kind = "canvas", lastSyncedAt = now.minusSeconds(300))
        assertEquals("Canvas · Synced 5 min ago", IcalLinks.status(canvas, now, showKind = true).text)
        assertEquals("Synced 5 min ago", IcalLinks.status(canvas, now).text)
        assertEquals("Schoology · Not synced yet", IcalLinks.status(IcalFeed(id = "2", kind = "schoology"), now, showKind = true).text)
        val broken = IcalLinks.status(IcalFeed(id = "3", kind = "brightspace", lastError = "Not public"), now, showKind = true)
        assertEquals("Brightspace · Couldn't sync: Not public", broken.text)
        assertTrue(broken.error)
    }

    @Test
    fun lmsGuidesText() {
        for (kind in IcalKind.LMS) assertEquals(3, IcalLinks.steps(kind))
        assertEquals("Add your Canvas calendar with a link", IcalLinks.guideTitle(IcalKind.Canvas))
        assertEquals("Add your Schoology calendar with a link", IcalLinks.guideTitle(IcalKind.Schoology))
        assertEquals("Add your Brightspace calendar with a link", IcalLinks.guideTitle(IcalKind.Brightspace))
        assertEquals("Brightspace calendar link", IcalLinks.urlLabel(IcalKind.Brightspace))
        // Server defaults: every event for Blackboard and Schoology, "auto" for the rest.
        assertEquals(
            listOf(false, true, false, true, false),
            listOf(IcalKind.Google, IcalKind.Blackboard, IcalKind.Canvas, IcalKind.Schoology, IcalKind.Brightspace).map { it.defaultImportsAll },
        )
        assertEquals("Only assignments and due dates", IcalLinks.autoModeTitle(IcalKind.Canvas))
        assertEquals("Only assignments and due dates", IcalLinks.autoModeTitle(IcalKind.Brightspace))
        assertEquals("Only events that look like assignments", IcalLinks.autoModeTitle(IcalKind.Schoology))
        assertEquals(
            "Keep this link private. Anyone with it can see your Schoology calendar. If you reset it in Schoology, add the new one here.",
            IcalLinks.privacyNote(IcalKind.Schoology),
        )
    }

    @Test
    fun decodesImportFileResult() {
        // icalImportFile -> { ok, imported, calendars: [{ name, imported, events }] }; a malformed entry is skipped.
        val json = """
            {"ok":true,"imported":5,"calendars":[
              {"name":"AP Biology","imported":"3","events":12},
              {"name":"Alex Rivera","imported":0,"events":40},
              {"name":"World History","imported":2,"events":7},
              42
            ]}
        """.trimIndent()
        val result = AppJson.decodeFromString(IcalImportResult.serializer(), json)
        assertEquals(5, result.imported)
        assertEquals(listOf("AP Biology", "Alex Rivera", "World History"), result.calendars.map { it.name })
        assertEquals(3, result.calendars[0].imported)
        assertEquals(12, result.calendars[0].events)
        assertEquals("Imported 5 assignments from AP Biology and World History.", IcalLinks.importFileMessage(result))

        val none = AppJson.decodeFromString(IcalImportResult.serializer(), """{"ok":true,"imported":0,"calendars":[{"name":"A","imported":0,"events":3}]}""")
        assertEquals("No new assignments found.", IcalLinks.importFileMessage(none))
        assertEquals(0, AppJson.decodeFromString(IcalImportResult.serializer(), "{}").imported)
    }

    @Test
    fun importFileMessageWording() {
        fun r(vararg cals: Pair<String, Int>) = IcalImportResult(
            imported = cals.sumOf { it.second },
            calendars = cals.map { education.studyspot.app.model.IcalImportedCalendar(it.first, it.second) },
        )
        assertEquals("Imported 1 assignment from AP Biology.", IcalLinks.importFileMessage(r("AP Biology" to 1)))
        assertEquals("Imported 6 assignments from A, B and C.", IcalLinks.importFileMessage(r("A" to 1, "B" to 2, "C" to 3)))
        assertEquals("Imported 2 assignments.", IcalLinks.importFileMessage(r(" " to 2)))
    }

    @Test
    fun readCappedStopsPastTheLimit() {
        val ok = ByteArray(100) { it.toByte() }
        assertTrue(ok.contentEquals(IcalLinks.readCapped(ByteArrayInputStream(ok), max = 100)))
        assertNull(IcalLinks.readCapped(ByteArrayInputStream(ByteArray(101)), max = 100))
        assertEquals(0, IcalLinks.readCapped(ByteArrayInputStream(ByteArray(0)))?.size)
    }

    @Test
    fun guidesHaveTheirOwnStepsAndText() {
        assertEquals(5, IcalLinks.steps(IcalKind.Google))
        assertEquals(3, IcalLinks.steps(IcalKind.Blackboard))
        assertEquals("Add your Blackboard calendar with a link", IcalLinks.guideTitle(IcalKind.Blackboard))
        assertEquals(
            "Study Spot checks this calendar for changes every couple of hours and whenever you sync.",
            IcalLinks.doneFooter(IcalKind.Blackboard),
        )
    }
}
