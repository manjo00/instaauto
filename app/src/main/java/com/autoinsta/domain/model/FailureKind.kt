package com.autoinsta.domain.model

/**
 * Whose fault a failed publish was.
 *
 * The distinction decides whether the queue may hand the slot to the next post, so it has
 * to be recorded rather than inferred from an error string later.
 *
 * - [PERMANENT] — Instagram rejected *this* media and always will. The next post in the
 *   pool deserves the slot; nothing is wrong with it.
 * - [TRANSIENT] — no network, an expired token, Instagram having a moment. The next post
 *   would fail identically, so walking the queue would mark every piece FAILED in a minute.
 */
enum class FailureKind {
    PERMANENT,
    TRANSIENT,
}
