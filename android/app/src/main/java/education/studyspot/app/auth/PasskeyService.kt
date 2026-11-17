package education.studyspot.app.auth

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.ApiException
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.arrayValue
import education.studyspot.app.core.get
import education.studyspot.app.core.stringValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * WebAuthn JSON for Credential Manager (pure, unit-tested). The server's
 * options (server/src/functions/twofa.js) are WebAuthn-shaped but omit the
 * relying-party ID, which Android needs explicitly.
 */
object PasskeyJson {
    /** passkeyRegisterStart options -> the PublicKeyCredentialCreationOptions JSON Android expects. */
    fun creationRequest(options: JsonElement, rpId: String): String {
        val challenge = options["challenge"].stringValue ?: throw invalid()
        val user = options["user"]
        val userId = user["id"].stringValue ?: throw invalid()
        val name = user["name"].stringValue ?: "Study Spot"
        val json = buildJsonObject {
            put("challenge", challenge)
            put("rp", buildJsonObject {
                put("id", rpId)
                put("name", options["rp"]["name"].stringValue ?: "Study Spot")
            })
            put("user", buildJsonObject {
                put("id", userId)
                put("name", name)
                put("displayName", user["displayName"].stringValue ?: name)
            })
            put("pubKeyCredParams", options["pubKeyCredParams"] as? JsonArray ?: buildJsonArray {
                add(buildJsonObject { put("type", "public-key"); put("alg", -7) })
                add(buildJsonObject { put("type", "public-key"); put("alg", -257) })
            })
            put("timeout", (options["timeout"] as? JsonPrimitive) ?: JsonPrimitive(60000))
            put("attestation", "none")
            put("authenticatorSelection", options["authenticatorSelection"] as? JsonObject ?: buildJsonObject {
                put("residentKey", "required")
                put("requireResidentKey", true)
                put("userVerification", "required")
            })
            put("excludeCredentials", descriptors(options["excludeCredentials"]))
        }
        return AppJson.encodeToString(JsonObject.serializer(), json)
    }

    /** passkeyLoginStart / adminElevateStart options ({challenge, allowCredentials}) -> request JSON. */
    fun assertionRequest(options: JsonElement, rpId: String, userVerification: String = "preferred"): String {
        val challenge = options["challenge"].stringValue ?: throw invalid()
        val json = buildJsonObject {
            put("challenge", challenge)
            put("rpId", rpId)
            put("allowCredentials", descriptors(options["allowCredentials"]))
            put("userVerification", userVerification)
            put("timeout", 60000)
        }
        return AppJson.encodeToString(JsonObject.serializer(), json)
    }

    /** registrationResponseJson -> passkeyRegisterFinish payload. */
    fun registrationPayload(responseJson: String): JsonObject {
        val r = runCatching { AppJson.parseToJsonElement(responseJson) }.getOrNull() ?: throw failed("Passkey setup failed")
        val id = r["rawId"].stringValue ?: r["id"].stringValue ?: throw failed("Passkey setup failed")
        val response = r["response"]
        return buildJsonObject {
            put("credentialId", unpad(id))
            put("attestationObject", unpad(response["attestationObject"].stringValue ?: throw failed("Passkey setup failed")))
            put("clientDataJSON", unpad(response["clientDataJSON"].stringValue ?: throw failed("Passkey setup failed")))
        }
    }

    /** authenticationResponseJson -> the fields the server verifies (passkeyLoginFinish / adminElevate). */
    fun assertionPayload(responseJson: String): JsonObject {
        val r = runCatching { AppJson.parseToJsonElement(responseJson) }.getOrNull() ?: throw failed("Passkey verification failed")
        val id = r["rawId"].stringValue ?: r["id"].stringValue ?: throw failed("Passkey verification failed")
        val response = r["response"]
        fun field(key: String) = unpad(response[key].stringValue ?: throw failed("Passkey verification failed"))
        return buildJsonObject {
            put("credentialId", unpad(id))
            put("authenticatorData", field("authenticatorData"))
            put("clientDataJSON", field("clientDataJSON"))
            put("signature", field("signature"))
        }
    }

    private fun descriptors(list: JsonElement?): JsonArray = buildJsonArray {
        list.arrayValue.orEmpty().forEach { d ->
            val id = d["id"].stringValue ?: return@forEach
            add(buildJsonObject { put("type", "public-key"); put("id", id) })
        }
    }

    /** The server compares base64url without padding. */
    private fun unpad(s: String) = s.trimEnd('=')

    private fun invalid() = ApiException(0, "Invalid passkey options from server")
    private fun failed(message: String) = ApiException(0, message)
}

/**
 * Native passkeys through Android Credential Manager, using the server's
 * WebAuthn endpoints (mirrors ios/StudySpot/Services/PasskeyService.swift).
 *
 * Android only lets the app use passkeys for the relying party (the server's
 * host) if that domain serves /.well-known/assetlinks.json naming this app's
 * signing certificate; see android/README.md.
 *
 * [context] must be an Activity context (Credential Manager shows its UI over it).
 */
class PasskeyService(private val api: ApiClient, private val rpId: String) {
    /** Thrown when the user dismisses the system sheet. */
    class Cancelled : Exception("Cancelled")

    /** passkeyRegisterStart -> create a credential -> passkeyRegisterFinish. */
    suspend fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw unsupported()
        val options = api.invoke("passkeyRegisterStart")
        val request = CreatePublicKeyCredentialRequest(PasskeyJson.creationRequest(options, rpId))
        val result = try {
            CredentialManager.create(context).createCredential(context, request)
        } catch (_: CreateCredentialCancellationException) {
            throw Cancelled()
        } catch (e: CreateCredentialException) {
            throw ApiException(0, friendly(e.errorMessage?.toString(), "Couldn't create the passkey."), cause = e)
        }
        val response = result as? CreatePublicKeyCredentialResponse ?: throw ApiException(0, "Passkey setup failed")
        api.invoke("passkeyRegisterFinish", PasskeyJson.registrationPayload(response.registrationResponseJson))
    }

    /** passkeyLoginStart -> assertion -> passkeyLoginFinish. Returns the response (it carries a full session token). */
    suspend fun verify(context: Context): JsonElement {
        val options = api.invoke("passkeyLoginStart")
        return api.invoke("passkeyLoginFinish", assertion(context, options))
    }

    /** Signs server-issued options and returns the fields the server verifies. */
    suspend fun assertion(context: Context, options: JsonElement, userVerification: String = "preferred"): JsonObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw unsupported()
        val option = GetPublicKeyCredentialOption(PasskeyJson.assertionRequest(options, rpId, userVerification))
        val result = try {
            CredentialManager.create(context).getCredential(context, GetCredentialRequest(listOf(option)))
        } catch (_: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (e: NoCredentialException) {
            throw ApiException(0, "No passkey for Study Spot was found on this device.", cause = e)
        } catch (e: GetCredentialException) {
            throw ApiException(0, friendly(e.errorMessage?.toString(), "Passkey verification failed."), cause = e)
        }
        val credential = result.credential as? PublicKeyCredential ?: throw ApiException(0, "Passkey verification failed")
        return PasskeyJson.assertionPayload(credential.authenticationResponseJson)
    }

    private fun unsupported() = ApiException(0, "Passkeys need Android 9 or later. Use your authenticator app instead.")

    private fun friendly(message: String?, fallback: String): String =
        if (message.isNullOrBlank()) fallback else "$fallback $message"
}
