package education.studyspot.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** The "Ask your teacher to share the class calendar" email (import-spec section C). */
class TeacherEmailTest {

    @Test
    fun bodyMatchesSpec() {
        val expected = "Hi [Teacher's name],\n" +
            "\n" +
            "I use Study Spot (https://studyspot.education) to keep track of my assignments, but our school doesn't let me connect my Google account to it.\n" +
            "\n" +
            "If you make our class's Google Calendar public, I (and anyone in our class) can add our assignments to Study Spot with a link. " +
            "It only shares the assignment titles and due dates already on the class calendar, not any student information, and it takes about a minute. " +
            "The steps are here: https://studyspot.education/teachers\n" +
            "\n" +
            "Thank you!\n" +
            "\n" +
            "Alex Doe"
        assertEquals(expected, TeacherEmail.body("Alex Doe"))
        assertEquals("Could you share our class calendar?", TeacherEmail.SUBJECT)
    }

    @Test
    fun signedWithTheSameNameRuleAsTheItEmail() {
        val name = SchoolITEmail.senderName(null, null, "sam.lee@school.edu")
        assertEquals("sam.lee", name)
        assertTrue(TeacherEmail.body(name).endsWith("\n\nsam.lee"))
        assertTrue(TeacherEmail.body(SchoolITEmail.senderName("Sam Lee", "Sam", "sam")).endsWith("\nSam Lee"))
    }

    @Test
    fun mailtoHasNoRecipientAndIsPercentEncoded() {
        val uri = TeacherEmail.mailto("Alex Doe")
        assertTrue(uri.startsWith("mailto:?subject=Could%20you%20share%20our%20class%20calendar%3F&body="))
        assertFalse(uri.contains("+"))
        assertFalse(uri.contains(" "))
        val body = uri.substringAfter("&body=")
        assertEquals(TeacherEmail.body("Alex Doe"), URLDecoder.decode(body.replace("+", "%2B"), "UTF-8"))
    }
}
