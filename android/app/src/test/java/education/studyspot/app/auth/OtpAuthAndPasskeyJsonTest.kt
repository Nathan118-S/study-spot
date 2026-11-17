package education.studyspot.app.auth

import education.studyspot.app.core.AppJson
import education.studyspot.app.core.ApiException
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAuthTest {

    @Test
    fun parsesServerSetupUri() {
        // Exactly what server/src/functions/twofa.js setup2fa returns.
        val uri = "otpauth://totp/StudySpot:alex.doe?secret=JBSWY3DPEHPK3PXP&issuer=Study%20Spot&algorithm=SHA1&digits=6&period=30"
        val otp = OtpAuth.parse(uri)!!
        assertEquals("totp", otp.type)
        assertEquals("Study Spot", otp.issuer)
        assertEquals("alex.doe", otp.account)
        assertEquals("JBSWY3DPEHPK3PXP", otp.secret)
        assertEquals("SHA1", otp.algorithm)
        assertEquals(6, otp.digits)
        assertEquals(30, otp.period)
        assertEquals("JBSW Y3DP EHPK 3PXP", otp.groupedSecret)
    }

    @Test
    fun fallsBackToLabelIssuerAndDefaults() {
        val otp = OtpAuth.parse("otpauth://totp/Acme%3Ajo?secret=abcd%20efgh")!!
        assertEquals("Acme", otp.issuer)
        assertEquals("jo", otp.account)
        assertEquals("ABCDEFGH", otp.secret)
        assertEquals(6, otp.digits)
        assertEquals(30, otp.period)
    }

    @Test
    fun rejectsNonOtpauthOrMissingSecret() {
        assertNull(OtpAuth.parse(null))
        assertNull(OtpAuth.parse("https://example.com"))
        assertNull(OtpAuth.parse("otpauth://totp/StudySpot:alex?issuer=Study%20Spot"))
        assertNull(OtpAuth.parse("otpauth://"))
    }
}

class PasskeyJsonTest {

    private val registerStart = """
        {
          "rp": { "name": "Study Spot" },
          "user": { "id": "dXNlci1pZA", "name": "alex", "displayName": "Alex Doe" },
          "challenge": "Y2hhbGxlbmdl",
          "pubKeyCredParams": [ { "type": "public-key", "alg": -7 }, { "type": "public-key", "alg": -257 } ],
          "timeout": 60000,
          "attestation": "none",
          "authenticatorSelection": { "userVerification": "required", "residentKey": "required", "requireResidentKey": true },
          "excludeCredentials": [ { "type": "public-key", "id": "b2xkLWNyZWQ" } ]
        }
    """.trimIndent()

    @Test
    fun creationRequestAddsRelyingPartyId() {
        val json = AppJson.parseToJsonElement(PasskeyJson.creationRequest(AppJson.parseToJsonElement(registerStart), "studyspot.education"))
        assertEquals("studyspot.education", json["rp"]["id"].stringValue)
        assertEquals("Study Spot", json["rp"]["name"].stringValue)
        assertEquals("Y2hhbGxlbmdl", json["challenge"].stringValue)
        assertEquals("dXNlci1pZA", json["user"]["id"].stringValue)
        assertEquals("Alex Doe", json["user"]["displayName"].stringValue)
        assertEquals(2, (json["pubKeyCredParams"] as JsonArray).size)
        assertEquals("required", json["authenticatorSelection"]["residentKey"].stringValue)
        assertEquals("b2xkLWNyZWQ", (json["excludeCredentials"] as JsonArray)[0]["id"].stringValue)
    }

    @Test
    fun assertionRequestCarriesChallengeAndAllowList() {
        val opts = AppJson.parseToJsonElement("""{"challenge":"abc","allowCredentials":[{"type":"public-key","id":"Y3JlZA"}]}""")
        val json = AppJson.parseToJsonElement(PasskeyJson.assertionRequest(opts, "example.com", "required")).jsonObject
        assertEquals("abc", json["challenge"].stringValue)
        assertEquals("example.com", json["rpId"].stringValue)
        assertEquals("required", json["userVerification"].stringValue)
        assertEquals("Y3JlZA", (json["allowCredentials"] as JsonArray)[0]["id"].stringValue)
    }

    @Test(expected = ApiException::class)
    fun missingChallengeIsAnError() {
        PasskeyJson.assertionRequest(AppJson.parseToJsonElement("{}"), "example.com")
    }

    @Test
    fun registrationPayloadMapsCredentialManagerResponse() {
        val response = """
            {"id":"Y3JlZA","rawId":"Y3JlZA","type":"public-key",
             "response":{"clientDataJSON":"eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0","attestationObject":"o2NmbXRk","transports":["internal"]}}
        """.trimIndent()
        val p = PasskeyJson.registrationPayload(response)
        assertEquals("Y3JlZA", p["credentialId"].stringValue)
        assertEquals("o2NmbXRk", p["attestationObject"].stringValue)
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0", p["clientDataJSON"].stringValue)
    }

    @Test
    fun assertionPayloadMapsAndStripsPadding() {
        val response = """
            {"id":"Y3JlZA","rawId":"Y3JlZA==","type":"public-key",
             "response":{"clientDataJSON":"Y2Q=","authenticatorData":"YWQ","signature":"c2ln","userHandle":"dQ"}}
        """.trimIndent()
        val p = PasskeyJson.assertionPayload(response)
        assertEquals("Y3JlZA", p["credentialId"].stringValue)
        assertEquals("Y2Q", p["clientDataJSON"].stringValue)
        assertEquals("YWQ", p["authenticatorData"].stringValue)
        assertEquals("c2ln", p["signature"].stringValue)
        assertTrue(p.keys.containsAll(listOf("credentialId", "authenticatorData", "clientDataJSON", "signature")))
    }
}
