package com.autoinsta.domain.model

enum class PostType {
    /** Single image or short video posted as a standard feed post. */
    SINGLE_IMAGE,

    /** Vertical video published as an Instagram Reel. */
    REEL,

    /** 2–10 images/videos published as a carousel (multi-post). */
    CAROUSEL,
}
