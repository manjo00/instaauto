package com.autoinsta.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Carries the result of the Instagram login from the Activity to whatever is waiting
 * for it.
 *
 * ## Why this exists
 * Custom Tabs run the login in the real browser, in a different task. When Instagram
 * redirects to our registered address, Android delivers it to [com.autoinsta.MainActivity]
 * as a fresh Intent — not to the screen that started the login. That Intent arrives with
 * no reference to the ViewModel that is waiting, so the two need somewhere to meet.
 *
 * A process-wide holder is the simplest thing that works. The result is consumed once
 * and cleared, so a configuration change (rotation, unfolding the phone) cannot replay
 * an old login code — codes are single-use and Instagram rejects the second attempt.
 */
object OAuthRedirectBus {

    sealed interface Result {
        data class Code(val value: String) : Result
        data class Error(val message: String) : Result
    }

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result

    fun publish(result: Result) {
        _result.value = result
    }

    /** Take the pending result, clearing it so it cannot be handled twice. */
    fun consume(): Result? = _result.value.also { _result.value = null }

    fun clear() {
        _result.value = null
    }
}
