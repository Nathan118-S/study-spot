package education.studyspot.app.auth

import android.content.Context
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.ApiEvent
import education.studyspot.app.core.ApiException
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.data.DataRepository
import education.studyspot.app.liveupdates.LiveUpdates
import education.studyspot.app.model.User
import education.studyspot.app.push.PushManager
import education.studyspot.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.TimeZone

/**
 * Session state for the whole app (mirrors ios/StudySpot/State/AuthStore.swift).
 *
 * Sign-in happens on the website ([WebSignIn]); the site hands the token back
 * via studyspot://oauth/callback#token=… which lands in [didSignIn]. The user
 * itself lives in [DataRepository.user] so feature screens read it from there.
 */
class AuthStore(
    private val context: Context,
    private val api: ApiClient,
    private val repository: DataRepository,
    private val scope: CoroutineScope,
) {
    sealed interface Phase {
        data object Loading : Phase
        data object SignedOut : Phase
        data class Unreachable(val message: String) : Phase
        data object SignedIn : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    val user: StateFlow<User?> get() = repository.user

    private val _disabledReason = MutableStateFlow<String?>(null)
    /** Shown on the welcome screen after a disabled account was signed out ("" = no reason given). */
    val disabledReason: StateFlow<String?> = _disabledReason.asStateFlow()

    /** The server marks a sign-in as pending until the second factor is verified. */
    val needsTwoFactor: StateFlow<Boolean> = repository.user
        .map { u -> u != null && u.mfaPending && u.enabledTwoFactorMethods.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val needsOnboarding: StateFlow<Boolean> = repository.user
        .map { u -> u != null && !u.mfaPending && !u.isDemo && !u.onboardingCompleted }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Set once per launch so the welcome greeting shows only once. */
    var hasGreetedThisLaunch = false

    init {
        scope.launch {
            api.events.collect { event ->
                when (event) {
                    ApiEvent.Unauthorized -> if (_phase.value == Phase.SignedIn) signOut()
                    ApiEvent.MfaRequired -> refreshUser()
                }
            }
        }
    }

    /** Validates the stored session (GET /api/auth/me) and sets [phase]. */
    suspend fun check() {
        if (api.token == null) {
            _phase.value = Phase.SignedOut
            return
        }
        if (repository.user.value == null) _phase.value = Phase.Loading
        try {
            val me = api.me()
            if (me.isDisabled) {
                _disabledReason.value = me.disabledReason.orEmpty()
                signOut()
                return
            }
            repository.setUser(me)
            _phase.value = Phase.SignedIn
            // Reminders and do-not-disturb run in the user's time zone.
            val tz = TimeZone.getDefault().id
            if (!me.mfaPending && me.data["timezone"].stringValue != tz) {
                scope.launch { runCatching { repository.updateUser(buildJsonObject { put("timezone", tz) }) } }
            }
        } catch (e: ApiException) {
            if (e.isAuthError) signOut() else unreachable(e)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            unreachable(e)
        }
    }

    private fun unreachable(e: Exception) {
        _phase.value = if (repository.user.value == null) Phase.Unreachable(e.userMessage) else Phase.SignedIn
    }

    /** Re-fetch the user (after a settings change) without flashing a loader. */
    suspend fun refreshUser() = repository.refreshUser()

    /** The website handed back a session token. */
    suspend fun didSignIn(token: String) {
        api.token = token
        _disabledReason.value = null
        hasGreetedThisLaunch = false
        check()
    }

    /** 2FA verification endpoints return a full session token that replaces the pending one. */
    suspend fun completeTwoFactor(response: JsonElement) {
        response["access_token"].stringValue?.takeIf { it.isNotEmpty() }?.let { api.token = it }
        refreshUser()
    }

    fun signOut() {
        val outgoing = api.token
        scope.launch { PushManager.unregister(context, api, bearer = outgoing) }
        scope.launch { LiveUpdates.endAll(context) }
        api.token = null // also drops the admin elevation
        repository.reset()
        _phase.value = Phase.SignedOut
        scope.launch { WidgetUpdater.refresh(context) }
    }
}
