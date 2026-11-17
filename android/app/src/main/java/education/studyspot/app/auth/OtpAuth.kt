package education.studyspot.app.auth

import java.net.URLDecoder

/**
 * An `otpauth://totp/<issuer>:<account>?secret=…&issuer=…` URI, as returned by
 * functions/setup2fa (server/src/functions/twofa.js).
 */
data class OtpAuth(
    val type: String,
    val issuer: String?,
    val account: String,
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
) {
    /** The secret in groups of four, easier to type into an authenticator app. */
    val groupedSecret: String get() = secret.chunked(4).joinToString(" ")

    companion object {
        /** Parses an otpauth URI; null if it isn't one or has no secret. */
        fun parse(uri: String?): OtpAuth? {
            val raw = uri?.trim().orEmpty()
            val prefix = "otpauth://"
            if (!raw.startsWith(prefix, ignoreCase = true)) return null
            val rest = raw.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash <= 0) return null
            val type = rest.substring(0, slash).lowercase()
            val afterType = rest.substring(slash + 1)
            val q = afterType.indexOf('?')
            val labelPart = decode(if (q >= 0) afterType.substring(0, q) else afterType)
            val params = if (q >= 0) query(afterType.substring(q + 1)) else emptyMap()
            val secret = params["secret"]?.replace(" ", "")?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null

            val colon = labelPart.indexOf(':')
            val labelIssuer = if (colon >= 0) labelPart.substring(0, colon).trim() else null
            val account = (if (colon >= 0) labelPart.substring(colon + 1) else labelPart).trim()
            return OtpAuth(
                type = type,
                issuer = params["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer?.takeIf { it.isNotBlank() },
                account = account,
                secret = secret,
                algorithm = params["algorithm"]?.uppercase() ?: "SHA1",
                digits = params["digits"]?.toIntOrNull() ?: 6,
                period = params["period"]?.toIntOrNull() ?: 30,
            )
        }

        private fun query(q: String): Map<String, String> = q.split("&").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else decode(pair.substring(0, i)).lowercase() to decode(pair.substring(i + 1))
        }.toMap()

        // URLDecoder treats "+" as a space, which is right for query strings
        // and harmless for labels (usernames can't contain "+").
        private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}
