package com.autoinsta.domain.model

enum class PostStatus {
    /** Waiting for its scheduled time. */
    SCHEDULED,

    /** PostWorker is actively uploading / calling the Graph API. */
    POSTING,

    /** Successfully published on Instagram. */
    POSTED,

    /** Publishing failed (see PostHistoryEntity.errorMessage for details). */
    FAILED,

    /** User deleted / cancelled the scheduled post before it fired. */
    CANCELLED,
}
