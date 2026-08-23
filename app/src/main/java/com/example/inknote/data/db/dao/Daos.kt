package com.example.inknote.data.db.dao

import androidx.room.*
import com.example.inknote.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): SubjectEntity?
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE subjectId = :subjectId AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getNotebooksBySubject(subjectId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE isFavorite = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getFavoriteNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookFlowById(id: Long): Flow<NotebookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)

    @Query("SELECT * FROM notebooks WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNotebooks(query: String): Flow<List<NotebookEntity>>
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageNumber ASC")
    fun getPagesForNotebook(notebookId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageNumber ASC")
    suspend fun getPagesListForNotebook(notebookId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE notebookId = :notebookId AND pageNumber = :pageNumber")
    suspend fun deletePageByNumber(notebookId: Long, pageNumber: Int)
}

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY orderIndex ASC, id ASC")
    fun getStrokesForPage(pageId: Long): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY orderIndex ASC, id ASC")
    suspend fun getStrokesListForPage(pageId: Long): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: StrokeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<StrokeEntity>): List<Long>

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun clearStrokesForPage(pageId: Long)

    @Delete
    suspend fun deleteStroke(stroke: StrokeEntity)

    @Query("DELETE FROM strokes WHERE id IN (:strokeIds)")
    suspend fun deleteStrokesByIds(strokeIds: List<Long>)
}

@Dao
interface ShapeDao {
    @Query("SELECT * FROM shapes WHERE pageId = :pageId ORDER BY orderIndex ASC, id ASC")
    fun getShapesForPage(pageId: Long): Flow<List<ShapeEntity>>

    @Query("SELECT * FROM shapes WHERE pageId = :pageId ORDER BY orderIndex ASC, id ASC")
    suspend fun getShapesListForPage(pageId: Long): List<ShapeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShape(shape: ShapeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShapes(shapes: List<ShapeEntity>): List<Long>

    @Update
    suspend fun updateShape(shape: ShapeEntity)

    @Delete
    suspend fun deleteShape(shape: ShapeEntity)

    @Query("DELETE FROM shapes WHERE id IN (:shapeIds)")
    suspend fun deleteShapesByIds(shapeIds: List<Long>)

    @Query("DELETE FROM shapes WHERE pageId = :pageId")
    suspend fun clearShapesForPage(pageId: Long)
}

@Dao
interface TextItemDao {
    @Query("SELECT * FROM text_items WHERE pageId = :pageId")
    fun getTextItemsForPage(pageId: Long): Flow<List<TextItemEntity>>

    @Query("SELECT * FROM text_items WHERE pageId = :pageId")
    suspend fun getTextItemsListForPage(pageId: Long): List<TextItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextItem(item: TextItemEntity): Long

    @Update
    suspend fun updateTextItem(item: TextItemEntity)

    @Delete
    suspend fun deleteTextItem(item: TextItemEntity)

    @Query("SELECT text_items.* FROM text_items INNER JOIN pages ON text_items.pageId = pages.id WHERE pages.notebookId = :notebookId AND text_items.text LIKE '%' || :query || '%'")
    suspend fun searchInNotebook(notebookId: Long, query: String): List<TextItemEntity>

    @Query("SELECT * FROM text_items WHERE text LIKE '%' || :query || '%'")
    suspend fun searchAllText(query: String): List<TextItemEntity>

    @Query("DELETE FROM text_items WHERE pageId = :pageId")
    suspend fun clearTextForPage(pageId: Long)
}

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE pageId = :pageId")
    fun getMediaItemsForPage(pageId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE pageId = :pageId")
    suspend fun getMediaItemsListForPage(pageId: Long): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity): Long

    @Update
    suspend fun updateMediaItem(item: MediaItemEntity)

    @Delete
    suspend fun deleteMediaItem(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE pageId = :pageId")
    suspend fun clearMediaForPage(pageId: Long)
}

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_recordings WHERE notebookId = :notebookId ORDER BY recordedAt DESC")
    fun getRecordingsForNotebook(notebookId: Long): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM audio_recordings WHERE notebookId = :notebookId ORDER BY recordedAt DESC")
    suspend fun getRecordingsListForNotebook(notebookId: Long): List<AudioRecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: AudioRecordingEntity): Long

    @Delete
    suspend fun deleteRecording(recording: AudioRecordingEntity)
}

@Dao
interface PageSnapshotDao {
    @Query("SELECT * FROM page_snapshots WHERE pageId = :pageId ORDER BY timestamp DESC")
    fun getSnapshotsForPage(pageId: Long): Flow<List<PageSnapshotEntity>>

    @Query("SELECT * FROM page_snapshots WHERE pageId = :pageId ORDER BY timestamp DESC")
    suspend fun getSnapshotsListForPage(pageId: Long): List<PageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PageSnapshotEntity): Long

    @Delete
    suspend fun deleteSnapshot(snapshot: PageSnapshotEntity)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE notebookId = :notebookId ORDER BY lastReviewedAt ASC, id ASC")
    fun getFlashcardsForNotebook(notebookId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE notebookId = :notebookId")
    suspend fun getFlashcardsListForNotebook(notebookId: Long): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>): List<Long>

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE notebookId = :notebookId")
    suspend fun clearFlashcardsForNotebook(notebookId: Long)
}
