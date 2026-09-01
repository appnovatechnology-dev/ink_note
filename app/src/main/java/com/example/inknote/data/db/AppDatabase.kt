package com.example.inknote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.inknote.data.db.dao.*
import com.example.inknote.data.db.entities.*

@Database(
    entities = [
        UserAccountEntity::class,
        SubjectEntity::class,
        NotebookEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        ShapeEntity::class,
        TextItemEntity::class,
        MediaItemEntity::class,
        AudioRecordingEntity::class,
        PageSnapshotEntity::class,
        FlashcardEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userAccountDao(): UserAccountDao
    abstract fun subjectDao(): SubjectDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao
    abstract fun strokeDao(): StrokeDao
    abstract fun shapeDao(): ShapeDao
    abstract fun textItemDao(): TextItemDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun audioDao(): AudioDao
    abstract fun pageSnapshotDao(): PageSnapshotDao
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inknote_database.db"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
