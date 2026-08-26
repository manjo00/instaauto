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
        cropOffset: Float = 0.5f,
    ): String {
        val base = "f_jpg,q_auto:good"
        val verdict = verdictFor(widthPx, heightPx)

        if (verdict is Verdict.Acceptable || verdict is Verdict.Unknown || mode == Mode.AS_IS) {
            // Still cap the width: Instagram scales anything larger anyway, and sending
            // 6000px of artwork just to have it resized wastes the 8 MB budget.
            return "$base,c_limit,w_$MAX_WIDTH_PX"
        }

        val ratio = nearestAllowedRatio(widthPx, heightPx)

        return when (mode) {
            Mode.PAD -> {
                val targetWidth = MAX_WIDTH_PX
                val targetHeight = (targetWidth / ratio).toInt()
                // c_pad keeps the whole image and fills the remainder.
                "$base,c_pad,w_$targetWidth,h_$targetHeight,b_$padColour"
            }

            Mode.CROP -> {
                // Crop in the original's own pixels first, positioned by the owner, then
                // cap the width. Two steps chained with "/" — cropping in target-space
                // instead would silently re-centre and throw away their choice.
                val window = cropWindow(widthPx, heightPx, cropOffset)
                "$base,c_crop,w_${window.width},h_${window.height},x_${window.x},y_${window.y}" +
                    "/c_limit,w_$MAX_WIDTH_PX"
            }

            Mode.AS_IS -> "$base,c_limit,w_$MAX_WIDTH_PX"
        }
    }

    /** The rectangle kept by a crop, in the original image's pixels. */
    data class CropWindow(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Which rectangle survives, given where the owner positioned the frame.
     *
     * @param offset 0 = top (or left edge), 0.5 = centre, 1 = bottom (or right edge).
     *
     * The window is always the largest rectangle of an allowed ratio that fits inside the
     * image, so cropping never scales anything up or invents pixels — it only chooses
     * which part to keep.
     */
    fun cropWindow(widthPx: Int, heightPx: Int, offset: Float): CropWindow {
        val safeOffset = offset.coerceIn(0f, 1f)
        return when (verdictFor(widthPx, heightPx)) {
            is Verdict.TooTall -> {
                // Full width, shortened. The owner slides it up and down.
                val height = (widthPx / MIN_ASPECT_RATIO).toInt().coerceAtMost(heightPx)
                val y = ((heightPx - height) * safeOffset).toInt().coerceAtLeast(0)
                CropWindow(x = 0, y = y, width = widthPx, height = height)
            }
            is Verdict.TooWide -> {
                // Full height, narrowed. The owner slides it left and right.
                val width = (heightPx * MAX_ASPECT_RATIO).toInt().coerceAtMost(widthPx)
                val x = ((widthPx - width) * safeOffset).toInt().coerceAtLeast(0)
                CropWindow(x = x, y = 0, width = width, height = heightPx)
            }
            // Nothing to crop: the whole image is the window.
            else -> CropWindow(x = 0, y = 0, width = widthPx, height = heightPx)
        }
    }

    /**
     * How much of the image a crop would discard, 0 to 1. Drives the editor's warning —
     * losing 44% of a piece is worth saying out loud.
     */
    fun croppedAwayFraction(widthPx: Int, heightPx: Int): Float {
        if (widthPx <= 0 || heightPx <= 0) return 0f
        val window = cropWindow(widthPx, heightPx, 0.5f)
        val kept = window.width.toLong() * window.height.toLong()
        val total = widthPx.toLong() * heightPx.toLong()
        return (1.0 - kept.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
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
