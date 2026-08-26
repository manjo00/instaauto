package com.autoinsta.data.db

import androidx.room.TypeConverter
import com.autoinsta.domain.MediaFit
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType

/**
 * Converts Kotlin enums to/from their String name for SQLite storage.
 * Registered on AppDatabase via @TypeConverters.
 */
class Converters {

    @TypeConverter fun postTypeToString(v: PostType): String = v.name
    @TypeConverter fun stringToPostType(v: String): PostType = PostType.valueOf(v)

    @TypeConverter fun postStatusToString(v: PostStatus): String = v.name
    @TypeConverter fun stringToPostStatus(v: String): PostStatus = PostStatus.valueOf(v)

    @TypeConverter fun mediaTypeToString(v: MediaType): String = v.name
    @TypeConverter fun stringToMediaType(v: String): MediaType = MediaType.valueOf(v)

    @TypeConverter fun missedPolicyToString(v: MissedPostPolicy): String = v.name
    @TypeConverter fun stringToMissedPolicy(v: String): MissedPostPolicy = MissedPostPolicy.valueOf(v)

    @TypeConverter fun fitModeToString(v: MediaFit.Mode): String = v.name
    @TypeConverter fun stringToFitMode(v: String): MediaFit.Mode = MediaFit.Mode.valueOf(v)
}
