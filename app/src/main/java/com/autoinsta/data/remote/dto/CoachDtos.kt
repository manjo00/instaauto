package com.autoinsta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The Anthropic Messages API, as much of it as the caption coach uses.
 *
 * Modelled by hand rather than via the official Java SDK. That was a measured decision,
 * not a shortcut: the SDK builds on Android but adds **6.9 MB to the APK** (18.9 → 25.8)
 * and pulls Jackson in alongside the kotlinx.serialization already here, for a single POST.
 * Two other APIs in this app are Retrofit + kotlinx already. See docs/STATUS.md.
 *
 * The wire shapes below were confirmed against the live API before being written, not
 * recalled — `output_config.format` in particular.
 */
@Serializable
data class CoachRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<CoachMessageDto>,
    @SerialName("output_config") val outputConfig: OutputConfigDto,
)

@Serializable
data class CoachMessageDto(
    val role: String,
    val content: List<CoachContentDto>,
)

/**
 * One block of a message. Instagram-bound artwork is sent as base64 rather than a URL
 * because the file is already on the device — Cloudinary only gets involved at publish
 * time, and the coach runs long before that.
 */
@Serializable
data class CoachContentDto(
    val type: String,
    val text: String? = null,
    val source: ImageSourceDto? = null,
)

/**
 * ⚠️ **No Kotlin defaults on any request field below.**
 *
 * The shared `NetworkModule.json` is tuned for reading Meta's responses, and one of its
 * settings bites on the way out: `encodeDefaults` is false, so a field left to its default
 * never leaves the device. `type = "base64"` and `type = "json_schema"` were written as
 * defaults, were silently dropped, and every call came back HTTP 400 while the DTO looked
 * perfectly correct in the debugger.
 *
 * A wire constant is not a default — it is a value this request must carry. Pass it.
 * `CoachRequestWireTest` fails if one goes missing again.
 */
@Serializable
data class ImageSourceDto(
    val type: String,
    @SerialName("media_type") val mediaType: String,
    val data: String,
)

@Serializable
data class OutputConfigDto(
    /**
     * Thinking depth. `low` is deliberate: this is a short creative suggestion task, and
     * measurement on the real API showed it answers well there. Effort is the first
     * cost lever before touching the model.
     */
    val effort: String,
    val format: OutputFormatDto,
)

/** Structured output. The labels on each suggestion are the teaching, so they cannot be
 *  left to whether the model felt like including them. */
@Serializable
data class OutputFormatDto(
    val type: String,
    val schema: JsonElement,
)

@Serializable
data class CoachResponseDto(
    val content: List<ResponseContentDto>? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
) {
    /** The model's answer, which with a json_schema format is the JSON document itself. */
    val text: String?
        get() = content
            ?.filter { it.type == "text" }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }
}

@Serializable
data class ResponseContentDto(
    val type: String,
    val text: String? = null,
)

/** What the coach actually returns, matching `CaptionCoach.responseSchema()`. */
@Serializable
data class CoachPayloadDto(
    val titles: List<TitleDto> = emptyList(),
    val caption: CaptionDto? = null,
    val hashtags: List<TagDto> = emptyList(),
)

@Serializable
data class TitleDto(val text: String = "", val source: String = "", val why: String = "")

@Serializable
data class CaptionDto(
    // Response DTO, so defaults are fine here: these are read, never sent.
    val materials: String = "",
    val process: String = "",
    val decision: String = "",
)

@Serializable
data class TagDto(val tag: String = "", val role: String = "", val note: String = "")
