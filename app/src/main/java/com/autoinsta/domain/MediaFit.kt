package com.autoinsta.domain

/**
 * Whether a piece of media is a shape Instagram will accept, and what to do when it isn't.
 *
 * Instagram rejects any image outside **4:5 to 1.91:1**. That window is narrow, and
 * digital art is often taller than 4:5 — a 9:16 piece is 0.5625 and well outside it. A
 * rejection lands at publish time, hours after scheduling, with the owner asleep, so the
 * shape has to be settled before the post is queued rather than discovered afterwards.
 *
 * Pure on purpose: every rule here is a number comparison, and getting them wrong is
 * expensive and slow to notice. No Android imports, so the awkward cases are unit tests.
 */
object MediaFit {

    /** Tallest Instagram accepts: 4:5 = 0.8. */
    const val MIN_ASPECT_RATIO = 0.8

    /** Widest Instagram accepts: 1.91:1. */
    const val MAX_ASPECT_RATIO = 1.91

    /** Instagram scales anything outside this; we cap on delivery to save bandwidth. */
    const val MAX_WIDTH_PX = 1440

    /** How the owner wants an out-of-range image brought into range. */
    enum class Mode {
        /** Pad to the nearest allowed ratio. Nothing is lost; bars fill the remainder. */
        PAD,

        /** Crop to the nearest allowed ratio, keeping the centre (or the owner's frame). */
        CROP,

        /** Already acceptable, or the owner wants it sent untouched. */
        AS_IS,
    }

    sealed interface Verdict {
        /** Inside Instagram's range; nothing needs doing. */
        data object Acceptable : Verdict

        /** Taller than 4:5 — the common case for art. */
        data class TooTall(val ratio: Double) : Verdict

        /** Wider than 1.91:1 — panoramas. */
        data class TooWide(val ratio: Double) : Verdict

        /** Dimensions unknown (not yet measured, or a video). */
        data object Unknown : Verdict
    }

    /** @param widthPx/[heightPx] the media's real pixel dimensions. */
    fun verdictFor(widthPx: Int, heightPx: Int): Verdict {
        if (widthPx <= 0 || heightPx <= 0) return Verdict.Unknown
        val ratio = widthPx.toDouble() / heightPx.toDouble()
        return when {
            ratio < MIN_ASPECT_RATIO -> Verdict.TooTall(ratio)
            ratio > MAX_ASPECT_RATIO -> Verdict.TooWide(ratio)
            else -> Verdict.Acceptable
        }
    }

    fun isAcceptable(widthPx: Int, heightPx: Int): Boolean =
        verdictFor(widthPx, heightPx) is Verdict.Acceptable

    /**
     * The closest ratio Instagram allows. A too-tall image is brought up to 4:5 rather
     * than to 1:1, so the padding or cropping is the least it can be.
     */
    fun nearestAllowedRatio(widthPx: Int, heightPx: Int): Double =
        when (verdictFor(widthPx, heightPx)) {
            is Verdict.TooTall -> MIN_ASPECT_RATIO
            is Verdict.TooWide -> MAX_ASPECT_RATIO
            else -> widthPx.toDouble() / heightPx.toDouble()
        }

    /**
     * The Cloudinary delivery transformation that makes this image publishable.
     *
     * Applied to the **URL**, not the upload: unsigned uploads accept almost no
     * parameters, and doing it this way keeps the stored original untouched and the
     * decision reversible.
     *
     * Always includes `f_jpg` — Instagram accepts JPEG only, and rejects PNG outright,
     * which matters because art is so often exported as PNG.
     */
    fun transformationFor(
        widthPx: Int,
        heightPx: Int,
        mode: Mode,
        padColour: String = DEFAULT_PAD_COLOUR,
    ): String {
        val base = "f_jpg,q_auto:good"
        val verdict = verdictFor(widthPx, heightPx)

        if (verdict is Verdict.Acceptable || verdict is Verdict.Unknown || mode == Mode.AS_IS) {
            // Still cap the width: Instagram scales anything larger anyway, and sending
            // 6000px of artwork just to have it resized wastes the 8 MB budget.
            return "$base,c_limit,w_$MAX_WIDTH_PX"
        }

        val ratio = nearestAllowedRatio(widthPx, heightPx)
        val targetWidth = MAX_WIDTH_PX
        val targetHeight = (targetWidth / ratio).toInt()

        return when (mode) {
            Mode.PAD ->
                // c_pad keeps the whole image and fills the remainder.
                "$base,c_pad,w_$targetWidth,h_$targetHeight,b_$padColour"
            Mode.CROP ->
                // c_fill crops to fill; g_auto lets Cloudinary pick the subject rather
                // than blindly taking the centre.
                "$base,c_fill,g_auto,w_$targetWidth,h_$targetHeight"
            Mode.AS_IS ->
                "$base,c_limit,w_$MAX_WIDTH_PX"
        }
    }

    /**
     * Plain wording for the compose screen. Deliberately avoids "aspect ratio" — the
     * owner cares that their art is safe, not about the arithmetic.
     */
    fun explain(verdict: Verdict, mode: Mode): String? = when (verdict) {
        is Verdict.Acceptable, Verdict.Unknown -> null
        is Verdict.TooTall -> when (mode) {
            Mode.PAD -> "Taller than Instagram allows — bars will be added so nothing is cut off."
            Mode.CROP -> "Taller than Instagram allows — the top and bottom will be cropped."
            Mode.AS_IS -> "Too tall for Instagram. This post will fail unless you fit or crop it."
        }
        is Verdict.TooWide -> when (mode) {
            Mode.PAD -> "Wider than Instagram allows — bars will be added so nothing is cut off."
            Mode.CROP -> "Wider than Instagram allows — the sides will be cropped."
            Mode.AS_IS -> "Too wide for Instagram. This post will fail unless you fit or crop it."
        }
    }

    /** White. Cloudinary colour syntax, e.g. `b_white`, `b_rgb:101014`. */
    const val DEFAULT_PAD_COLOUR = "white"
}
