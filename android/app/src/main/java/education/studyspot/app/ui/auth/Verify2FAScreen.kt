package education.studyspot.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.PasskeyService
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.TwoFactorMethod
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.settings.BusyButton
import education.studyspot.app.ui.settings.CodeField
import education.studyspot.app.ui.settings.SettingsFootnote
import education.studyspot.app.ui.settings.SettingsSection
import education.studyspot.app.ui.settings.icon
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Second-factor check after a website sign-in whose session is `mfa_pending`
 * (ios/StudySpot/Views/Auth/Verify2FAView.swift): authenticator code
 * (functions/verify2fa) or passkey (passkeyLoginStart -> Credential Manager ->
 * passkeyLoginFinish). The result carries a full `access_token`, which
 * [education.studyspot.app.auth.AuthStore.completeTwoFactor] stores.
 */
@Composable
fun Verify2FAScreen() {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val methods = user?.enabledTwoFactorMethods.orEmpty().ifEmpty { listOf(TwoFactorMethod.Totp) }
    val preferred = user?.twofaMethod?.let(TwoFactorMethod::from)?.takeIf { it in methods } ?: methods.first()
    var selected by remember(preferred) { mutableStateOf(preferred) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val passkeys = remember { PasskeyService(c.api, c.config.passkeyRelyingParty) }

    fun submit(action: suspend () -> JsonElement) {
        if (loading) return
        scope.launch {
            error = ""
            loading = true
            try {
                c.auth.completeTwoFactor(action())
            } catch (_: PasskeyService.Cancelled) {
                // Dismissed the passkey sheet.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
                code = ""
            }
            loading = false
        }
    }

    fun verifyCode() = submit { c.api.invoke("verify2fa", buildJsonObject { put("code", code) }) }

    SubScreenScaffold(title = "Two-factor authentication", onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = StudySpotTheme.colors.accent, modifier = Modifier.size(52.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Verify it's you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StudySpotTheme.colors.secondary)
                Text(user?.username.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (methods.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    methods.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = selected == m,
                            onClick = { selected = m; code = ""; error = "" },
                            shape = SegmentedButtonDefaults.itemShape(i, methods.size),
                            icon = { SegmentedButtonDefaults.Icon(selected == m) { Icon(m.icon, contentDescription = null, modifier = Modifier.size(18.dp)) } },
                            label = { Text(m.label) },
                        )
                    }
                }
            }

            when (selected) {
                TwoFactorMethod.Totp -> SettingsSection(
                    header = "Enter the 6-digit code from your authenticator app",
                    footer = if (error.isEmpty()) null else ({ SettingsFootnote(error, error = true) }),
                ) {
                    Box(Modifier.padding(16.dp)) { CodeField(code, { code = it; error = "" }, onDone = ::verifyCode) }
                    BusyButton(
                        "Verify",
                        onClick = ::verifyCode,
                        busy = loading,
                        enabled = code.length == 6,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
                TwoFactorMethod.Passkey -> SettingsSection(
                    footer = if (error.isEmpty()) null else ({ SettingsFootnote(error, error = true) }),
                ) {
                    Text(
                        "Use your fingerprint, face, or screen lock to confirm.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    BusyButton(
                        "Use passkey",
                        onClick = { submit { passkeys.verify(context) } },
                        busy = loading,
                        icon = TwoFactorMethod.Passkey.icon,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            TextButton(onClick = { c.auth.signOut() }) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text("Sign out", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
