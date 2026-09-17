package com.example.inknote.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.inknote.data.db.dao.*
import com.example.inknote.data.db.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
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
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate default subjects and welcome notebook
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            val sub1Id = database.subjectDao().insertSubject(
                SubjectEntity(name = "Personal & Ideas", colorHex = "#6366F1", sortOrder = 0)
            )
            val sub2Id = database.subjectDao().insertSubject(
                SubjectEntity(name = "Work & Projects", colorHex = "#EC4899", sortOrder = 1)
            )
            val sub3Id = database.subjectDao().insertSubject(
                SubjectEntity(name = "Math & Sketches", colorHex = "#10B981", sortOrder = 2)
            )

            val nbId = database.notebookDao().insertNotebook(
                NotebookEntity(
                    subjectId = sub1Id,
                    title = "Welcome to InkNote ✍️",
                    coverColorHex = "#6366F1",
                    isFavorite = true,
                    pageCount = 2
                )
            )

            // Page 1: Introduction & Features
            val page1Id = database.pageDao().insertPage(
                PageEntity(
                    notebookId = nbId,
                    pageNumber = 1,
                    templateType = "BLANK"
                )
            )

            // Page 2: Blank page for quick notes
            val page2Id = database.pageDao().insertPage(
                PageEntity(
                    notebookId = nbId,
                    pageNumber = 2,
                    templateType = "BLANK"
                )
            )

            // Sample text box on page 1
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    pageId = page1Id,
                    text = "Welcome to InkNote!\n\n• Smooth stylus & finger vector ink\n• Geometric tools: Ruler, Protractor, Compass & Shape snap\n• Synced Audio Recording & Scrubbing\n• PDF & Image Annotations\n• 100% Offline & Private",
                    x = 80f,
                    y = 120f,
                    width = 650f,
                    height = 220f,
                    fontSize = 18f,
                    fontColorArgb = 0xFF1E293BL,
                    isBold = false,
                    backgroundColorArgb = 0x156366F1L
                )
            )

            // Sample welcome geometric shape on page 1
            database.shapeDao().insertShape(
                ShapeEntity(
                    pageId = page1Id,
                    shapeType = "RECTANGLE",
                    startX = 60f,
                    startY = 100f,
                    endX = 760f,
                    endY = 360f,
                    strokeColorArgb = 0xFF6366F1L,
                    fillColorArgb = 0x086366F1L,
                    strokeWidth = 3f,
                    isFilled = true
                )
            )
        }
    }
}
