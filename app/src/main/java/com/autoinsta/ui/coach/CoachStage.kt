package com.autoinsta.ui.coach

import com.autoinsta.domain.CoachAnswers
import com.autoinsta.domain.CoachSuggestions

/**
 * Where the caption coach has got to.
 *
 * [Asking] comes before [Ready] by design and not by accident: the owner's own words about
 * the piece are collected first, so they exist before a model has said anything. A coach
 * that speaks first is a ghostwriter.
 *
 * [Failed] carries the answers back so a retry does not throw away what they typed —
 * losing two sentences to a dropped connection is the fastest way to stop using a thing.
 */
sealed interface CoachStage {

    data object Closed : CoachStage

    data class Asking(val answers: CoachAnswers = CoachAnswers()) : CoachStage

    data object Thinking : CoachStage

    data class Ready(val suggestions: CoachSuggestions) : CoachStage

    data class Failed(val reason: String, val answers: CoachAnswers) : CoachStage
}
