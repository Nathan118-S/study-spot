package education.studyspot.app.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The app-wide JSON configuration: tolerant of new or missing server fields. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

// region Lenient decoding
//
// PostgreSQL NUMERIC columns arrive as JSON strings ("25"), and rows may carry
// nulls where the app expects defaults (see ios/StudySpot/Core/JSONValue.swift).

private fun Decoder.element(): JsonElement =
    (this as? JsonDecoder)?.decodeJsonElement() ?: JsonPrimitive(decodeString())

fun JsonElement?.flexString(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    else -> null
}

fun JsonElement?.flexDouble(): Double? = when (this) {
    is JsonPrimitive -> if (this is JsonNull) null else doubleOrNull ?: content.trim().toDoubleOrNull()
    else -> null
}

fun JsonElement?.flexBool(): Boolean? = when (this) {
    is JsonPrimitive -> if (this is JsonNull) null else booleanOrNull ?: when (content.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    else -> null
}

fun JsonElement?.flexInstant(): Instant? = when (this) {
    is JsonPrimitive -> if (this is JsonNull) null else longOrNull?.let { Instant.ofEpochMilli(it) } ?: Dates.parse(content)
    else -> null
}

/** Strings, numbers and booleans as text; anything else -> null. */
object FlexStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? = decoder.element().flexString()
    override fun serialize(encoder: Encoder, value: String?) =
        (encoder as JsonEncoder).encodeJsonElement(value?.let(::JsonPrimitive) ?: JsonNull)
}

/** Like [FlexStringSerializer] but never null: missing/null -> "". */
object FlexTextSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexText", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String = decoder.element().flexString() ?: ""
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Numbers or numeric strings; null/garbage -> 0.0. */
object FlexDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double = decoder.element().flexDouble() ?: 0.0
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

object FlexDoubleOrNullSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDoubleOrNull", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double? = decoder.element().flexDouble()
    override fun serialize(encoder: Encoder, value: Double?) =
        (encoder as JsonEncoder).encodeJsonElement(value?.let(::JsonPrimitive) ?: JsonNull)
}

object FlexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.element().flexDouble()?.toInt() ?: 0
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Booleans or "true"/"false" strings; null/garbage -> false. */
object FlexBoolSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean = decoder.element().flexBool() ?: false
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** ISO-8601 timestamps, date-only strings (local midnight) or epoch millis. */
object FlexInstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInstant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant? = decoder.element().flexInstant()
    override fun serialize(encoder: Encoder, value: Instant?) =
        (encoder as JsonEncoder).encodeJsonElement(value?.let { JsonPrimitive(Dates.format(it)) } ?: JsonNull)
}

/** A list where one bad element (or a non-array) doesn't fail the whole model. */
abstract class LenientListSerializer<T>(private val item: KSerializer<T>) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientList<${item.descriptor.serialName}>", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): List<T> {
        val arr = decoder.element() as? JsonArray ?: return emptyList()
        return arr.mapNotNull { runCatching { AppJson.decodeFromJsonElement(item, it) }.getOrNull() }
    }
    override fun serialize(encoder: Encoder, value: List<T>) =
        (encoder as JsonEncoder).encodeJsonElement(JsonArray(value.map { AppJson.encodeToJsonElement(item, it) }))
}

/** Any JSON object (e.g. the user's free-form `data` bag); non-objects -> {}. */
object LenientObjectSerializer : KSerializer<JsonObject> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientObject", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): JsonObject = decoder.element() as? JsonObject ?: JsonObject(emptyMap())
    override fun serialize(encoder: Encoder, value: JsonObject) = (encoder as JsonEncoder).encodeJsonElement(value)
}

/** Enums stored as strings, falling back to [default] for unknown values. */
abstract class LenientEnumSerializer<T : Enum<T>>(
    name: String,
    private val values: Array<T>,
    private val default: T,
    private val wire: (T) -> String,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.element().flexString() ?: return default
        return values.firstOrNull { wire(it) == raw } ?: default
    }
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(wire(value))
}

// endregion

object Dates {
    private val dateOnly = DateTimeFormatter.ISO_LOCAL_DATE

    /** Parses the server's timestamps ("2025-03-01T12:00:00.000Z", offsets, or "2025-03-01"). */
    fun parse(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10), dateOnly).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
    }

    /** ISO-8601 in UTC with milliseconds, like JavaScript's toISOString(). */
    fun format(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
}

// Small helpers for reading free-form JSON (function results, user.data).
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
val JsonElement?.stringValue: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
val JsonElement?.boolValue: Boolean? get() = flexBool()
val JsonElement?.doubleValue: Double? get() = flexDouble()
val JsonElement?.arrayValue: JsonArray? get() = this as? JsonArray
