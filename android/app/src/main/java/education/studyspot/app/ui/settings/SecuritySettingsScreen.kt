package education.studyspot.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import education.studyspot.app.LocalAppContainer
import education.studyspot.app.auth.OtpAuth
import education.studyspot.app.auth.PasskeyService
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import education.studyspot.app.core.userMessage
import education.studyspot.app.model.TwoFactorMethod
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.Pill
import education.studyspot.app.ui.components.SubScreenScaffold
import education.studyspot.app.ui.theme.StudySpotTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val TwoFactorMethod.icon: ImageVector
    get() = when (this) {
        TwoFactorMethod.Totp -> Icons.Outlined.Password
        TwoFactorMethod.Passkey -> Icons.Outlined.Key
    }

private sealed interface SecuritySheet {
    data object Chooser : SecuritySheet
    data object Totp : SecuritySheet
    data object Passkey : SecuritySheet
    data class Remove(val method: TwoFactorMethod) : SecuritySheet
}

/**
 * Settings > Security: two-factor authentication
 * (ios/StudySpot/Views/Settings/TwoFactorSettings.swift).
 *
 * - Authenticator app: functions/setup2fa -> QR code + secret -> functions/enable2fa { code, secret }.
 * - Passkey: functions/passkeyRegisterStart -> Credential Manager -> functions/passkeyRegisterFinish.
 *   Relying party: the server's host (needs Digital Asset Links; see android/README.md).
 * - Remove: verify with that method first, then functions/remove2faMethod.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SecuritySettingsScreen(modifier: Modifier = Modifier, navigator: AppNavigator? = null) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val user by c.repository.user.collectAsStateWithLifecycle()
    val methods = user?.enabledTwoFactorMethods.orEmpty()
    val available = TwoFactorMethod.entries.filter { it !in methods }
    var sheet by remember { mutableStateOf<SecuritySheet?>(null) }

    fun done() {
        sheet = null
        scope.launch { c.auth.refreshUser() }
    }

    SettingsColumn(modifier) {
        SettingsSection {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = StudySpotTheme.colors.accent)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Two-factor authentication", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (methods.isEmpty()) {
                            Pill("Disabled")
                        } else {
                            Pill("Enabled" + if (methods.size > 1) " · ${methods.size} methods" else "", color = StudySpotTheme.colors.success, filled = true)
                        }
                    }
                    Text(
                        "Add a second step at sign-in using an authenticator app or a passkey.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (methods.isEmpty()) {
                SettingsActions {
                    BusyButton("Enable", onClick = { sheet = SecuritySheet.Chooser }, enabled = user?.isDemo != true, icon = Icons.Outlined.Key)
                }
            }
        }
        if (methods.isNotEmpty()) {
            SettingsSection(header = "Methods") {
                methods.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = StudySpotTheme.colors.border)
                    ListItem(
                        headlineContent = { Text(m.label) },
                        supportingContent = { Text(m.detail) },
                        leadingContent = { Icon(m.icon, contentDescription = null, tint = StudySpotTheme.colors.secondary) },
                        trailingContent = {
                            TextButton(onClick = { sheet = SecuritySheet.Remove(m) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        },
                        colors = settingsRowColors(),
                    )
                }
                if (available.isNotEmpty()) {
                    HorizontalDivider(color = StudySpotTheme.colors.border)
                    ListItem(
                        headlineContent = { Text("Add another method", color = StudySpotTheme.colors.accent) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null, tint = StudySpotTheme.colors.accent) },
                        colors = settingsRowColors(),
                        modifier = Modifier.clickable { sheet = SecuritySheet.Chooser },
                    )
                }
            }
        }
    }

    when (val s = sheet) {
        SecuritySheet.Chooser -> MethodChooser(enabled = methods, onPick = { m ->
            sheet = if (m == TwoFactorMethod.Totp) SecuritySheet.Totp else SecuritySheet.Passkey
        }, onDismiss = { sheet = null })
        SecuritySheet.Totp -> TotpSetupDialog(onDone = ::done, onDismiss = { sheet = null })
        SecuritySheet.Passkey -> PasskeySetupDialog(onDone = ::done, onDismiss = { sheet = null })
        is SecuritySheet.Remove -> RemoveMethodDialog(s.method, onDone = ::done, onDismiss = { sheet = null })
        null -> Unit
    }
}

@Composable
private fun MethodChooser(enabled: List<TwoFactorMethod>, onPick: (TwoFactorMethod) -> Unit, onDismiss: () -> Unit) {
    val options = TwoFactorMethod.entries.filter { it !in enabled }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (enabled.isEmpty()) "Choose a second factor" else "Add another method") },
        text = {
            Column {
                if (options.isEmpty()) Text("All methods are already enabled.")
                options.forEach { o ->
                    ListItem(
                        headlineContent = { Text(o.label) },
                        supportingContent = { Text(o.detail) },
                        leadingContent = { Icon(o.icon, contentDescription = null, tint = StudySpotTheme.colors.secondary) },
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(o) },
                    )
                }
                Text(
                    if (enabled.isEmpty()) "Pick how you want to verify at sign-in. You can add more later." else "Add a backup way to verify at sign-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
    )
}

/** A QR code for [text] as a bitmap (black on white, 1-module quiet zone). */
fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

@Composable
private fun TotpSetupDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var otpauth by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val parsed = remember(otpauth) { OtpAuth.parse(otpauth) }
    val qr = remember(otpauth) { otpauth.takeIf { it.isNotEmpty() }?.let { runCatching { qrBitmap(it).asImageBitmap() }.getOrNull() } }

    LaunchedEffect(Unit) {
        try {
            val res = c.api.invoke("setup2fa")
            secret = res["secret"].stringValue.orEmpty()
            otpauth = res["otpauth_url"].stringValue.orEmpty()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            c.messages.show("Failed to start setup", e.userMessage, error = true)
            onDismiss()
        }
    }

    fun confirm() {
        scope.launch {
            busy = true
            error = ""
            try {
                c.api.invoke("enable2fa", buildJsonObject { put("code", code); put("secret", secret) })
                c.messages.show("Authenticator app enabled")
                onDone()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SubScreenScaffold(
            title = "Authenticator app",
            onBack = onDismiss,
            modifier = Modifier.fillMaxSize(),
            actions = {
                TextButton(onClick = ::confirm, enabled = !busy && code.length == 6 && secret.isNotEmpty()) { Text("Enable") }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsSection(
                    header = "Account: ${parsed?.issuer ?: "Study Spot"}" + (parsed?.account?.let { " ($it)" } ?: ""),
                    footer = { SettingsFootnote("Scan the code or add the key to your authenticator app, then enter the 6-digit code it generates.") },
                ) {
                    if (secret.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        if (qr != null) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = qr,
                                    contentDescription = "QR code for your authenticator app",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, otpauth.toUri()))
                                } catch (_: ActivityNotFoundException) {
                                    c.messages.show("No authenticator app found", "Install one, or add the key below by hand.", error = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Open in authenticator app", modifier = Modifier.padding(start = 6.dp))
                        }
                        HorizontalDivider(color = StudySpotTheme.colors.border)
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                parsed?.groupedSecret ?: secret,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Study Spot key", secret)))
                                    c.messages.show("Key copied")
                                }
                            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy key", tint = StudySpotTheme.colors.secondary) }
                        }
                    }
                }
                SettingsSection(
                    header = "Verification code",
                    footer = if (error.isEmpty()) null else ({ SettingsFootnote(error, error = true) }),
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CodeField(code, { code = it; error = "" }, onDone = ::confirm)
                    }
                    SettingsActions { BusyButton("Enable", onClick = ::confirm, busy = busy, enabled = code.length == 6 && secret.isNotEmpty()) }
                }
            }
        }
    }
}

@Composable
private fun PasskeySetupDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { PasskeyService(c.api, c.config.passkeyRelyingParty) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = StudySpotTheme.colors.accent) },
        title = { Text("Set up a passkey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Use your fingerprint, face, or screen lock to create a passkey for sign-in. It's saved to your Google Password Manager (or another passkey provider).",
                    textAlign = TextAlign.Center,
                )
                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Follow your device prompts…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        error = ""
                        busy = true
                        try {
                            service.register(context)
                            c.messages.show("Passkey enabled")
                            onDone()
                        } catch (_: PasskeyService.Cancelled) {
                            // Dismissed the system sheet: stay here so they can retry.
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = e.userMessage
                        }
                        busy = false
                    }
                },
            ) { Text(if (error.isEmpty()) "Create passkey" else "Retry") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
    )
}

@Composable
private fun RemoveMethodDialog(method: TwoFactorMethod, onDone: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passkeys = remember { PasskeyService(c.api, c.config.passkeyRelyingParty) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun confirm() {
        scope.launch {
            error = ""
            busy = true
            try {
                when (method) {
                    TwoFactorMethod.Totp -> c.api.invoke("verify2fa", buildJsonObject { put("code", code) })
                    TwoFactorMethod.Passkey -> passkeys.verify(context)
                }
                c.api.invoke("remove2faMethod", buildJsonObject { put("method", method.wire) })
                c.messages.show("${method.label} removed")
                onDone()
            } catch (_: PasskeyService.Cancelled) {
                // Stay open.
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Remove ${method.label}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (method) {
                    TwoFactorMethod.Totp -> {
                        Text("Enter a current 6-digit code from your authenticator app.")
                        CodeField(code, { code = it; error = "" }, onDone = ::confirm)
                    }
                    TwoFactorMethod.Passkey -> Text("Verify with your passkey to confirm removal.")
                }
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = !busy && (method == TwoFactorMethod.Passkey || code.length == 6)) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = StudySpotTheme.colors.secondary) } },
    )
}
