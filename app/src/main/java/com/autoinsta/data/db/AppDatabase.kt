package com.autoinsta.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autoinsta.data.db.dao.AccountDao
import com.autoinsta.data.db.dao.HashtagPresetDao
import com.autoinsta.data.db.dao.MediaItemDao
import com.autoinsta.data.db.dao.PostHistoryDao
import com.autoinsta.data.db.dao.ScheduledPostDao
import com.autoinsta.data.db.entities.AccountEntity
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.PostHistoryEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity

@Database(
    entities = [
        ScheduledPostEntity::class,
        MediaItemEntity::class,
        HashtagPresetEntity::class,
        PostHistoryEntity::class,
        AccountEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduledPostDao(): ScheduledPostDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun hashtagPresetDao(): HashtagPresetDao
    abstract fun postHistoryDao(): PostHistoryDao
    abstract fun accountDao(): AccountDao

    companion object {
        private const val DB_NAME = "autoinsta.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    // No destructive fallback: a schema change must migrate the user's
                    // queue, not delete it. See Migrations.kt.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
