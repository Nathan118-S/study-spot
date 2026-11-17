package education.studyspot.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.PasskeyService
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.ApiException
import education.studyspot.app.core.Dates
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.CodeField
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/**
 * Admin step-up (ios/StudySpot/Views/Admin/AdminUnlock.swift): opening or
 * changing anyone's account needs a recent two-factor confirmation. The server
 * issues a 15-minute elevation token, kept in memory by [ApiClient.adminElevation]
 * and sent as X-Admin-Elevation.
 *
 * Callers suspend in [ensureUnlocked] / [run] while [AdminUnlockHost] shows
 * the unlock dialog; dismissing it throws [Cancelled].
 */
object AdminUnlock {
    class Cancelled : Exception("Unlock cancelled")

    private val _expiresAt = MutableStateFlow<Instant?>(null)
    val expiresAt: StateFlow<Instant?> = _expiresAt.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val waiters = mutableListOf<CompletableDeferred<Unit>>()

    fun isUnlocked(api: ApiClient, now: Instant = Instant.now()): Boolean {
        val expires = _expiresAt.value ?: return false
        return expires > now && api.adminElevation != null
    }

    /** Shows the unlock dialog if needed; throws [Cancelled] if it's dismissed. */
    suspend fun ensureUnlocked(api: ApiClient) {
        if (!isUnlocked(api)) requestUnlock()
    }

    suspend fun requestUnlock() {
        val waiter = CompletableDeferred<Unit>()
        synchronized(waiters) { waiters += waiter }
        _showDialog.value = true
        waiter.await()
    }

    /** Runs a gated call, unlocking first; if the server says the unlock expired, asks again and retries once. */
    suspend fun <T> run(api: ApiClient, operation: suspend () -> T): T {
        ensureUnlocked(api)
        return try {
            operation()
        } catch (e: ApiException) {
            if (!e.elevationRequired) throw e
            lock(api)
            requestUnlock()
            operation()
        }
    }

    fun unlocked(api: ApiClient, token: String, expiresAt: Instant) {
        api.adminElevation = token
        _expiresAt.value = expiresAt
        finish(null)
    }

    fun cancel() = finish(Cancelled())

    fun lock(api: ApiClient) {
        api.adminElevation = null
        _expiresAt.value = null
    }

    private fun finish(error: Throwable?) {
        _showDialog.value = false
        val pending = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
        pending.forEach { if (error != null) it.completeExceptionally(error) else it.complete(Unit) }
    }
}

/** Hosts the unlock dialog. Place once at the root of the admin screens. */
@Composable
fun AdminUnlockHost() {
    val show by AdminUnlock.showDialog.collectAsStateWithLifecycle()
    if (show) AdminUnlockDialog()
}

@Composable
private fun AdminUnlockDialog() {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passkeys = remember { PasskeyService(c.api, c.config.passkeyRelyingParty) }
    var methods by remember { mutableStateOf<List<String>>(emptyList()) }
    var minutes by remember { mutableIntStateOf(15) }
    var passkeyOptions by remember { mutableStateOf<JsonElement?>(null) }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val res = c.api.invoke("adminElevateStart")
            methods = res["methods"].arrayValue.orEmpty().mapNotNull { it.stringValue }
            minutes = (res["minutes"].doubleValue ?: 15.0).toInt()
            passkeyOptions = res["passkey"]
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            setupError = e.userMessage
        }
        loading = false
    }

    fun unlock(payload: JsonObject) {
        scope.launch {
            error = ""
            busy = true
            try {
                val res = c.api.invoke("adminElevate", payload)
                val token = res["elevation"].stringValue?.takeIf { it.isNotEmpty() }
                    ?: throw ApiException(0, "The server didn't confirm the unlock.")
                val expires = Dates.parse(res["expires_at"].stringValue) ?: Instant.now().plusSeconds(minutes * 60L)
                AdminUnlock.unlocked(c.api, token, expires)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
                code = ""
            }
            busy = false
        }
    }

    fun usePasskey() {
        val options = passkeyOptions ?: return
        scope.launch {
            error = ""
            try {
                unlock(passkeys.assertion(context, options, userVerification = "required"))
            } catch (_: PasskeyService.Cancelled) {
                // Stay open.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
                // A challenge is single-use; fetch a fresh one for a retry.
                runCatching { c.api.invoke("adminElevateStart") }.getOrNull()?.let { passkeyOptions = it["passkey"] }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) AdminUnlock.cancel() },
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = StudySpotTheme.colors.accent) },
        title = { Text("Confirm it's you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Managing user accounts needs two-factor authentication. You'll stay unlocked for $minutes minutes on this device.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    loading -> CircularProgressIndicator()
                    setupError.isNotEmpty() -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = StudySpotTheme.colors.dueToday)
                            Text(setupError, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Turn on two-factor authentication in Settings → Security, then try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        if ("totp" in methods) {
                            Text("Code from your authenticator app", style = MaterialTheme.typography.labelLarge)
                            CodeField(code, { code = it; error = "" }, onDone = { unlock(buildJsonObject { put("code", code) }) })
                            BusyButton(
                                "Unlock",
                                onClick = { unlock(buildJsonObject { put("code", code) }) },
                                busy = busy,
                                enabled = code.length == 6,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if ("passkey" in methods && passkeyOptions != null) {
                            BusyButton(
                                "Use passkey",
                                onClick = ::usePasskey,
                                enabled = !busy,
                                prominent = "totp" !in methods,
                                icon = Icons.Outlined.Key,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (error.isNotEmpty()) {
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { AdminUnlock.cancel() }, enabled = !busy) { Text("Cancel", color = StudySpotTheme.colors.secondary) }
        },
    )
}

/** Minutes left (at least 1) until [expires]. */
fun minutesLeft(expires: Instant, now: Instant = Instant.now()): Int =
    maxOf(1, ceil(Duration.between(now, expires).seconds / 60.0).toInt())

/** "Unlocked · N min left" with a Lock button, or "Locked" with Unlock. */
@Composable
fun AdminUnlockStatus(modifier: Modifier = Modifier) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val expires by AdminUnlock.expiresAt.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val nowInstant = Instant.ofEpochMilli(now)
    val unlocked = expires?.let { it > nowInstant && c.api.adminElevation != null } == true

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tint = if (unlocked) StudySpotTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            if (unlocked) "Unlocked · ${minutesLeft(expires!!, nowInstant)} min left" else "Locked",
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (unlocked) {
            TextButton(onClick = { AdminUnlock.lock(c.api); now = System.currentTimeMillis() }) {
                Text("Lock", color = StudySpotTheme.colors.secondary)
            }
        } else {
            TextButton(onClick = {
                scope.launch { runCatching { AdminUnlock.ensureUnlocked(c.api) }; now = System.currentTimeMillis() }
            }) { Text("Unlock") }
        }
    }
}
