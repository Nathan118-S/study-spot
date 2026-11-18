package education.studyspot.app.ui.settings

import education.studyspot.app.ui.settings.SchoolITEmail.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class SchoolITEmailTest {

    @Test
    fun senderNamePrefersFullNameThenNameThenUsername() {
        assertEquals("Alex Doe", SchoolITEmail.senderName("  Alex Doe ", "Alex", "alex"))
        assertEquals("Alex", SchoolITEmail.senderName("", "Alex", "alex"))
        assertEquals("Alex", SchoolITEmail.senderName(null, " Alex ", "alex"))
        assertEquals("alex", SchoolITEmail.senderName(null, null, "alex@school.edu"))
        assertEquals("A student", SchoolITEmail.senderName(null, "  ", ""))
    }

    @Test
    fun googleBodyMatchesIos() {
        val expected = listOf(
            "Hello [School name] IT team,",
            "",
            "My name is Alex Doe, and I'm a student at [School name]. I use Study Spot (https://studyspot.education), a study planner that brings my assignments and due dates into one place so I can keep up with my schoolwork.",
            "",
            "When I try to connect my school Google account (Google Calendar and Google Classroom), it looks like the school has to approve third-party apps first. Could you please approve Study Spot so students can connect it?",
            "",
            "Study Spot only asks for read-only access to Google Calendar and to my Google Classroom courses and coursework (and, if I choose, marking my own Classroom work as turned in). It doesn't ask for access to Gmail or Drive.",
            "In the Google Admin console this is under Security → Access and data control → API controls → App access control, where Study Spot can be marked as trusted.",
            "",
            "Everything your team needs to review and approve it, including the IDs to allow and step-by-step instructions, is at https://studyspot.education/schools",
            "",
            "Thank you for your help!",
            "",
            "Alex Doe",
        ).joinToString("\n")
        assertEquals(expected, SchoolITEmail.body("Alex Doe", Service.Google))
    }

    @Test
    fun blackboardBodyHasOnlyBlackboardDetails() {
        val body = SchoolITEmail.body("Sam", Service.Blackboard)
        assertTrue(body.contains("When I try to connect my Blackboard account, it looks like"))
        assertTrue(body.contains("REST API Integrations."))
        assertFalse(body.contains("Google Admin console"))
    }

    @Test
    fun bothBodyHasGoogleThenBlackboard() {
        val body = SchoolITEmail.body("Sam", Service.Both)
        val google = body.indexOf("Google Admin console")
        val blackboard = body.indexOf("Blackboard's REST API")
        assertTrue(google in 0 until blackboard)
        assertTrue(body.contains("(Google Calendar and Google Classroom) and my Blackboard account"))
    }

    @Test
    fun mailtoIsPercentEncodedWithoutPlusSigns() {
        val uri = SchoolITEmail.mailto("Alex Doe", Service.Google)
        assertTrue(uri.startsWith("mailto:?subject=Request%20to%20approve%20Study%20Spot%20at%20%5BSchool%20name%5D&body="))
        assertFalse(uri.contains("+"))
        assertFalse(uri.contains(" "))
        val body = uri.substringAfter("&body=")
        assertEquals(SchoolITEmail.body("Alex Doe", Service.Google), URLDecoder.decode(body.replace("+", "%2B"), "UTF-8"))
    }

    @Test
    fun noticeNamesTheService() {
        assertEquals(
            "Some schools block connecting third-party apps like Study Spot to school Google accounts. " +
                "If the connection is blocked or fails, ask your school's IT team, or add your assignments manually.",
            SchoolITEmail.noticeText(Service.Google),
        )
        assertTrue(SchoolITEmail.noticeText(Service.Blackboard).contains("to Blackboard accounts."))
    }
}
