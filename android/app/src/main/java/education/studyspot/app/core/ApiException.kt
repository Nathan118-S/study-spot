package education.studyspot.app.core

/**
 * A failed API call. [message] is the server's `{ error }` text (safe to show).
 * status 0 means the request never got a usable HTTP response.
 */
class ApiException(
    val status: Int,
    message: String,
    /** The Google/Blackboard integration isn't connected. */
    val notConnected: Boolean = false,
    /** The admin 2FA step-up (X-Admin-Elevation) is missing or expired. */
    val elevationRequired: Boolean = false,
    /** The session still needs its second factor verified. */
    val mfaRequired: Boolean = false,
    /**
     * The request never reached the server (no network, connection lost, timed
     * out). Writes that fail this way are queued for later (DataRepository);
     * HTTP error responses are not.
     */
    val isOffline: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {
    override val message: String get() = super.message.orEmpty()

    /** The session is gone or not allowed (sign out), as opposed to an admin step-up. */
    val isAuthError: Boolean get() = (status == 401 || status == 403) && !elevationRequired && !mfaRequired
}

/** A user-facing message for any error. */
val Throwable.userMessage: String
    get() = (this as? ApiException)?.message ?: localizedMessage ?: "Something went wrong."
