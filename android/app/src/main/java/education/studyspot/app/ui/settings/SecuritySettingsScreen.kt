package education.studyspot.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import education.studyspot.app.navigation.AppNavigator
import education.studyspot.app.ui.components.StubContent

/**
 * Settings > Security: two-factor authentication
 * (ios/StudySpot/Views/Settings/TwoFactorSettings.swift).
 *
 * TODO(settings): STUB. Methods from user.enabledTwoFactorMethods.
 * - Authenticator app: functions/setup2fa -> show the otpauth:// URI as a QR code
 *   (ZXing core is on the classpath: com.google.zxing.qrcode.QRCodeWriter) and the
 *   secret, then functions/enable2fa { code }.
 * - Passkey: functions/passkeyRegisterStart -> androidx.credentials
 *   CreatePublicKeyCredentialRequest -> functions/passkeyRegisterFinish.
 *   Relying party: c.config.passkeyRelyingParty (needs Digital Asset Links on the server).
 * - Remove: functions/remove2faMethod / disable2fa.
 */
@Composable
fun SecuritySettingsScreen(modifier: Modifier = Modifier, navigator: AppNavigator? = null) {
    StubContent("Security", "Coming soon.", modifier)
}
