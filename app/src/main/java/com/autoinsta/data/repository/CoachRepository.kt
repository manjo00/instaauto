package com.autoinsta.data.repository

import android.util.Base64
import com.autoinsta.BuildConfig
import com.autoinsta.data.db.dao.PostHistoryDao
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.data.remote.AnthropicApi
import com.autoinsta.data.remote.dto.CaptionDto
import com.autoinsta.data.remote.dto.CoachContentDto
import com.autoinsta.data.remote.dto.CoachMessageDto
import com.autoinsta.data.remote.dto.CoachPayloadDto
import com.autoinsta.data.remote.dto.CoachRequestDto
import com.autoinsta.data.remote.dto.ImageSourceDto
import com.autoinsta.data.remote.dto.OutputConfigDto
import com.autoinsta.data.remote.dto.OutputFormatDto
import com.autoinsta.domain.CaptionCoach
import com.autoinsta.domain.CaptionDraft
import com.autoinsta.domain.CoachAnswers
import com.autoinsta.domain.CoachSuggestions
import com.autoinsta.domain.TagSuggestion
import com.autoinsta.domain.TitleSuggestion
import com.autoinsta.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** What came back from asking for help. */
sealed interface CoachResult {
    data class Ready(val suggestions: CoachSuggestions) : CoachResult

    /** Something went wrong, said in words the owner can act on. */
    data class Failed(val reason: String) : CoachResult
}

/**
 * Asks Claude for title, caption and hashtag suggestions for one finished piece.
 *
 * ## What this is not
 * It is not a ghostwriter. The owner's own two answers go into the request ahead of
 * everything else, and every suggestion comes back carrying the move it used — see
 * [CaptionCoach], where all the rules live. This class only carries them to the API and
 * back.
 *
 * ## Optional by construction
 * With no API key configured this returns [CoachResult.Failed] and nothing else in the app
 * changes. A caption helper must never be able to stop someone posting.
 *
 * `open` so instrumented tests can substitute a fake, the same seam
 * [PublishRepository] uses — and for the same reason: a test suite must not spend the
 * owner's API credit.
 */
open class CoachRepository(
    private val api: AnthropicApi,
    private val historyDao: PostHistoryDao,
    private val mediaFileStore: MediaFileStore,
    private val apiKey: String = BuildConfig.ANTHROPIC_API_KEY,
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** False when no key is configured — the UI hides the coach entirely rather than
     *  offering a button that can only fail. */
    open fun isAvailable(): Boolean = apiKey.isNotBlank()

    /**
     * @param mediaUri either a picker `content://` address or a path in our own storage —
     *   the coach is used both while composing a new post and when editing a saved one.
     */
    open suspend fun suggest(
        mediaUri: String,
        mediaType: MediaType,
        answers: CoachAnswers,
    ): CoachResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext CoachResult.Failed(
                "No Anthropic API key set — add one in secrets.properties to use the coach."
            )
        }

        // Downscaled and re-encoded to JPEG on the way out; a video becomes one frame.
        val snapshot = mediaFileStore.coachSnapshot(mediaUri, mediaType)
            ?: return@withContext CoachResult.Failed(
                if (mediaType == MediaType.VIDEO) {
                    "Couldn't read a frame from that video."
                } else {
                    "Couldn't read the image for this post."
                }
            )

        val encoded = Base64.encodeToString(snapshot, Base64.NO_WRAP)

        val request = CoachRequestDto(
            model = MODEL,
            maxTokens = AnthropicApi.MAX_TOKENS,
            system = CaptionCoach.systemPrompt(),
            outputConfig = OutputConfigDto(
                format = OutputFormatDto(schema = schemaElement())
            ),
            messages = listOf(
                CoachMessageDto(
                    role = "user",
                    content = listOf(
                        CoachContentDto(
                            type = "image",
                            source = ImageSourceDto(
                                // Always JPEG — coachSnapshot re-encodes whatever it was given.
                                mediaType = "image/jpeg",
                                data = encoded,
                            ),
                        ),
                        CoachContentDto(
                            type = "text",
                            text = CaptionCoach.userPrompt(answers, recentCaptions()),
                        ),
                    ),
                )
            ),
        )

        try {
            val body = api.createMessage(apiKey = apiKey, body = request).text
                ?: return@withContext CoachResult.Failed("Claude didn't answer. Try again.")

            val payload = json.decodeFromString<CoachPayloadDto>(body)
            CoachResult.Ready(payload.toSuggestions())
        } catch (e: retrofit2.HttpException) {
            CoachResult.Failed(explain(e.code()))
        } catch (e: java.net.UnknownHostException) {
            CoachResult.Failed("No internet connection.")
        } catch (e: java.net.SocketTimeoutException) {
            CoachResult.Failed("That took too long. Try again.")
        } catch (e: Exception) {
            CoachResult.Failed(e.message ?: "Couldn't get suggestions.")
        }
    }

    /**
     * Past captions, for voice. History rows only — a queued post has not been written in
     * anger yet, and matching a draft would teach the coach the wrong voice.
     */
    private suspend fun recentCaptions(): List<String> = runCatching {
        historyDao.recentCaptions(CaptionCoach.VOICE_SAMPLE_SIZE)
    }.getOrDefault(emptyList())

    private fun schemaElement(): JsonElement =
        json.parseToJsonElement(CaptionCoach.responseSchema())

    /** HTTP codes turned into something worth reading at the moment it happens. */
    private fun explain(code: Int): String = when (code) {
        401 -> "That API key was rejected. Check it in secrets.properties."
        400 -> "Claude couldn't read that request — the image may be too large."
        429 -> "Rate limited. Wait a moment and try again."
        in 500..599 -> "Anthropic had a problem. Try again shortly."
        else -> "Couldn't get suggestions (HTTP $code)."
    }

    private companion object {
        /**
         * Opus 5 with `effort: low`, which measurement on the real API showed is ample
         * for a short creative task. Roughly two pence a post at one post a week.
         */
        const val MODEL = "claude-opus-5"
    }
}

private fun CoachPayloadDto.toSuggestions(): CoachSuggestions = CoachSuggestions(
    // Capped here rather than by the schema — structured output cannot constrain array
    // length at all. See CaptionCoach.responseSchema.
    titles = CaptionCoach.titlesToOffer(
        titles.map { TitleSuggestion(text = it.text.trim(), source = it.source, why = it.why) }
    ),
    caption = (caption ?: CaptionDto()).let {
        CaptionDraft(hook = it.hook, process = it.process, invitation = it.invitation)
    },
    hashtags = hashtags
        .filter { it.tag.isNotBlank() }
        .map { TagSuggestion(tag = it.tag.trim(), role = it.role, note = it.note) },
)
