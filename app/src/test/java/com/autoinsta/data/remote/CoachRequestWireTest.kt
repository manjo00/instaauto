package com.autoinsta.data.remote

import com.autoinsta.data.remote.dto.CoachContentDto
import com.autoinsta.data.remote.dto.CoachMessageDto
import com.autoinsta.data.remote.dto.CoachRequestDto
import com.autoinsta.data.remote.dto.ImageSourceDto
import com.autoinsta.data.remote.dto.OutputConfigDto
import com.autoinsta.data.remote.dto.OutputFormatDto
import com.autoinsta.domain.CaptionCoach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the coach's request actually looks like on the wire.
 *
 * ## Why this test exists
 * `NetworkModule.json` is configured for *reading* Meta's responses. One of its
 * consequences for *writing* is easy to miss: `encodeDefaults` defaults to **false**, so a
 * request field left to a Kotlin default never leaves the device. Two of the coach's
 * fields were wire constants written as defaults — `source.type = "base64"` and
 * `format.type = "json_schema"` — and both were silently dropped, so every call came back
 * HTTP 400 with a valid-looking DTO in the debugger.
 *
 * It serialises with the app's real [NetworkModule.json], not a copy: the bug was a
 * mismatch between the assumed config and the actual one, so a test with its own config
 * would have passed while the app failed.
 */
class CoachRequestWireTest {

    private fun request() = CoachRequestDto(
        model = "claude-opus-5",
        maxTokens = AnthropicApi.MAX_TOKENS,
        system = CaptionCoach.systemPrompt(),
        outputConfig = OutputConfigDto(
            effort = "low",
            format = OutputFormatDto(
                type = "json_schema",
                schema = Json.parseToJsonElement(CaptionCoach.responseSchema()),
            ),
        ),
        messages = listOf(
            CoachMessageDto(
                role = "user",
                content = listOf(
                    CoachContentDto(
                        type = "image",
                        source = ImageSourceDto(
                            type = "base64",
                            mediaType = "image/jpeg",
                            data = "AAAA",
                        ),
                    ),
                    CoachContentDto(type = "text", text = "hello"),
                ),
            )
        ),
    )

    @Test
    fun `every field the API requires actually reaches the wire`() {
        val body = NetworkModule.json.encodeToString(request())

        assertTrue("source.type is missing", body.contains("\"type\":\"base64\""))
        assertTrue("format.type is missing", body.contains("\"type\":\"json_schema\""))
        assertTrue("max_tokens is missing", body.contains("\"max_tokens\":"))
        assertTrue("the thinking budget is missing", body.contains("\"effort\":"))
        assertTrue("output_config is missing", body.contains("\"output_config\":"))
    }

    @Test
    fun `the image block carries its data and no stray null text field`() {
        val body = NetworkModule.json.encodeToString(request())

        assertTrue(body.contains("\"media_type\":\"image/jpeg\""))
        assertTrue(body.contains("\"data\":\"AAAA\""))
        // explicitNulls = false keeps the unused half of each content block out.
        assertTrue("nulls should not be encoded", !body.contains(":null"))
    }
}
