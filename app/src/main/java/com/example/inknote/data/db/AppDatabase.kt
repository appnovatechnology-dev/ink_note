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

        fun getInstance(context: Context): AppDatabase = getDatabase(context)

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

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("UPDATE notebooks SET title = 'Welcome to Inkstead ✍️' WHERE title LIKE '%Welcome to InkNote%'")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error updating welcome title in onOpen", e)
                }
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        updateWelcomeContentIfOld(database)
                    }
                }
            }
        }

        suspend fun updateWelcomeContentIfOld(database: AppDatabase) {
            try {
                val notebooks = database.notebookDao().getAllNotebooksList()
                val welcomeNotebook = notebooks.firstOrNull { 
                    it.title.contains("Welcome", ignoreCase = true) 
                } ?: return

                if (welcomeNotebook.title != "Welcome to Inkstead ✍️") {
                    database.notebookDao().updateNotebook(
                        welcomeNotebook.copy(title = "Welcome to Inkstead ✍️")
                    )
                }

                val pages = database.pageDao().getPagesListForNotebook(welcomeNotebook.id)
                val page1 = pages.firstOrNull { it.pageNumber == 1 } ?: return
                val textItems = database.textItemDao().getTextItemsListForPage(page1.id)
                
                val hasOldInkNote = textItems.any { it.text.contains("InkNote", ignoreCase = true) }
                if (hasOldInkNote || textItems.isEmpty()) {
                    for (item in textItems) {
                        database.textItemDao().deleteTextItem(item)
                    }
                    val shapes = database.shapeDao().getShapesListForPage(page1.id)
                    for (shape in shapes) {
                        database.shapeDao().deleteShape(shape)
                    }
                    insertWelcomeContentForPage(database, page1.id)
                }

                val page2 = pages.firstOrNull { it.pageNumber == 2 }
                if (page2 != null) {
                    val page2Texts = database.textItemDao().getTextItemsListForPage(page2.id)
                    if (page2Texts.isEmpty()) {
                        insertPage2Content(database, page2.id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error migrating welcome note content", e)
            }
        }

        private suspend fun insertWelcomeContentForPage(database: AppDatabase, page1Id: Long) {
            // Header card
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    pageId = page1Id,
                    text = "Welcome to Inkstead! ✒️\n\nYour precision digital notebook for handwriting, technical drawing, and synchronized audio notes.",
                    x = 50f,
                    y = 60f,
                    width = 700f,
                    height = 100f,
                    fontSize = 17f,
                    fontColorArgb = 0xFF0F172AL,
                    isBold = false,
                    backgroundColorArgb = 0x184F46E5L
                )
            )

            // Features & instructions guide
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    pageId = page1Id,
                    text = "✨ Quick Start Guide:\n\n• 🖊️ Precision Stylus Inking: Ultra-low latency handwriting with bezier smoothing. Rest your hand freely by enabling 'Stylus Only' palm rejection in App Settings.\n\n• 📐 Interactive Geometry & Tools: Select the Ruler from the toolbar to draw measured lines and snap angles. Freehand shapes snap automatically with Magic Ink. Open the Inkstead scientific calculator from the top bar for quick offline math.\n\n• 🎙️ Synchronized Audio: Record lectures or meetings while taking notes. Tap any written note during playback to jump audio straight to that moment.\n\n• 📄 Multi-page PDF Markup: Import PDFs from the home screen for vector annotation, and export entire notebooks or custom page ranges (e.g. 1, 3-5).\n\n• 💡 Smart Gemini AI: Tap the AI button on the toolbar for Handwriting OCR, instant Math Formula solving, Flashcards, and Note Summaries.\n\n• 🔒 Offline-First & Private: All core note-taking works without an internet connection (optional AI features require one). All notebooks, drawings, and voice recordings remain safely on this device.",
                    x = 50f,
                    y = 180f,
                    width = 700f,
                    height = 420f,
                    fontSize = 14f,
                    fontColorArgb = 0xFF1E293BL,
                    isBold = false,
                    backgroundColorArgb = 0x0C1E293BL
                )
            )

            // Prompt to try page 2
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    pageId = page1Id,
                    text = "👉 Tip: Use the pen or highlighter above to test your stylus, or swipe/tap to Page 2 to start writing on lined paper!",
                    x = 50f,
                    y = 620f,
                    width = 700f,
                    height = 70f,
                    fontSize = 13.5f,
                    fontColorArgb = 0xFF047857L,
                    isBold = false,
                    backgroundColorArgb = 0x1A10B981L
                )
            )
        }

        private suspend fun insertPage2Content(database: AppDatabase, page2Id: Long) {
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    pageId = page2Id,
                    text = "📝 Practice & Notes\n\nPick a pen or highlighter from the toolbar above and start writing or sketching your first note here!",
                    x = 50f,
                    y = 60f,
                    width = 700f,
                    height = 80f,
                    fontSize = 15f,
                    fontColorArgb = 0xFF475569L,
                    isBold = false,
                    backgroundColorArgb = 0x106366F1L
                )
            )
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
                    title = "Welcome to Inkstead ✍️",
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
                    templateType = "RULED"
                )
            )

            insertWelcomeContentForPage(database, page1Id)
            insertPage2Content(database, page2Id)
        }
    }
}
