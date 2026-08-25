package com.autoinsta.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Meta is inconsistent about id types, and a mismatch fails the *entire* parse — which
 * in practice means throwing away a login the user has already completed and approved.
 * These pin both shapes down.
 *
 * The number form is taken from a real response observed on 2026-08-25.
 */
class AuthDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    // ── The bug that broke the first successful login ──────────────────────

    @Test
    fun `user_id sent as a bare number is parsed`() {
        val body = """
            {"access_token":"IGAAWxRNdE43b0hqdQZDZD","user_id":28044336998528158,
             "permissions":["instagram_business_basic"]}
        """.trimIndent()

        val dto = json.decodeFromString<ShortLivedTokenDto>(body)

        assertEquals("IGAAWxRNdE43b0hqdQZDZD", dto.accessToken)
        assertEquals("28044336998528158", dto.userId)
    }

    @Test
    fun `user_id sent as a quoted string is parsed`() {
        val body = """{"access_token":"abc","user_id":"28044336998528158"}"""
        assertEquals("28044336998528158", json.decodeFromString<ShortLivedTokenDto>(body).userId)
    }

    @Test
    fun `permissions as a comma-separated string does not break the parse`() {
        // Meta returns this shape in some responses. It is not modelled, so it must be
        // silently ignored rather than failing the whole token exchange.
        val body = """{"access_token":"abc","user_id":1,"permissions":"a,b,c"}"""
        assertEquals("abc", json.decodeFromString<ShortLivedTokenDto>(body).accessToken)
    }

    @Test
    fun `an unknown field Meta adds later does not break the parse`() {
        val body = """{"access_token":"abc","user_id":1,"something_new":{"nested":true}}"""
        assertEquals("abc", json.decodeFromString<ShortLivedTokenDto>(body).accessToken)
    }

    // ── Profile ────────────────────────────────────────────────────────────

    @Test
    fun `profile resolves user_id whether numeric or quoted`() {
        val numeric = """{"user_id":17841400000000000,"username":"my_art"}"""
        val quoted = """{"user_id":"17841400000000000","username":"my_art"}"""

        assertEquals("17841400000000000", json.decodeFromString<InstagramProfileDto>(numeric).resolvedId)
        assertEquals("17841400000000000", json.decodeFromString<InstagramProfileDto>(quoted).resolvedId)
    }

    @Test
    fun `profile falls back to id when user_id is absent`() {
        val body = """{"id":"17841400000000000","username":"my_art"}"""
        val dto = json.decodeFromString<InstagramProfileDto>(body)

        assertNull(dto.userId)
        assertEquals("17841400000000000", dto.resolvedId)
    }

    @Test
    fun `profile with neither id resolves to null rather than throwing`() {
        val dto = json.decodeFromString<InstagramProfileDto>("""{"username":"my_art"}""")
        assertNull(dto.resolvedId)
        assertEquals("my_art", dto.username)
    }

    // ── Tokens ─────────────────────────────────────────────────────────────

    @Test
    fun `long-lived token carries its expiry`() {
        val body = """{"access_token":"IGAA...","token_type":"bearer","expires_in":5183944}"""
        val dto = json.decodeFromString<LongLivedTokenDto>(body)

        assertEquals("IGAA...", dto.accessToken)
        assertEquals(5_183_944L, dto.expiresIn)
    }

    @Test
    fun `a token response missing expires_in still parses`() {
        // TokenLifecycle falls back to 60 days when this is absent.
        val dto = json.decodeFromString<LongLivedTokenDto>("""{"access_token":"x"}""")
        assertEquals("x", dto.accessToken)
        assertNull(dto.expiresIn)
    }

    // ── Errors ─────────────────────────────────────────────────────────────

    @Test
    fun `graph-style error message is readable`() {
        val body = """{"error":{"message":"Invalid platform app","type":"OAuthException","code":191}}"""
        assertEquals(
            "Invalid platform app",
            json.decodeFromString<MetaErrorEnvelopeDto>(body).readableMessage,
        )
    }

    @Test
    fun `instagram-style flat error message is readable`() {
        val body = """{"error_type":"OAuthException","code":400,"error_message":"Invalid authorization code"}"""
        assertEquals(
            "Invalid authorization code",
            json.decodeFromString<MetaErrorEnvelopeDto>(body).readableMessage,
        )
    }
}
