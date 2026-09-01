package com.example.inknote.data.db.dao

import androidx.room.*
import com.example.inknote.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity)

    @Update
    suspend fun updateUser(user: UserAccountEntity)

    @Query("SELECT * FROM user_accounts ORDER BY lastLoginAt DESC LIMIT 1")
    suspend fun getLastActiveUser(): UserAccountEntity?

    @Query("SELECT * FROM user_accounts")
    suspend fun getAllUsers(): List<UserAccountEntity>
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE userId = :userId ORDER BY sortOrder ASC, id ASC")
    fun getAllSubjects(userId: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE userId = :userId ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllSubjectsList(userId: String): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>): List<Long>

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM subjects WHERE id = :id AND userId = :userId")
    suspend fun getSubjectById(userId: String, id: Long): SubjectEntity?

    @Query("DELETE FROM subjects WHERE userId = :userId")
    suspend fun deleteSubjectsForUser(userId: String)
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE userId = :userId AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllNotebooks(userId: String): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE userId = :userId AND isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllNotebooksList(userId: String): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE userId = :userId AND subjectId = :subjectId AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getNotebooksBySubject(userId: String, subjectId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE userId = :userId AND isFavorite = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getFavoriteNotebooks(userId: String): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE id = :id AND userId = :userId")
    suspend fun getNotebookByIdForUser(userId: String, id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookFlowById(id: Long): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :id AND userId = :userId")
    fun getNotebookFlowByIdForUser(userId: String, id: Long): Flow<NotebookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebooks(notebooks: List<NotebookEntity>): List<Long>

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)

    @Query("SELECT * FROM notebooks WHERE userId = :userId AND title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNotebooks(userId: String, query: String): Flow<List<NotebookEntity>>

    @Query("DELETE FROM notebooks WHERE userId = :userId")
    suspend fun deleteNotebooksForUser(userId: String)
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

    @Query("DELETE FROM pages WHERE userId = :userId")
    suspend fun deletePagesForUser(userId: String)
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

    @Query("DELETE FROM strokes WHERE userId = :userId")
    suspend fun clearStrokesForUser(userId: String)
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

    @Query("DELETE FROM shapes WHERE userId = :userId")
    suspend fun clearShapesForUser(userId: String)
}

@Dao
interface TextItemDao {
    @Query("SELECT * FROM text_items WHERE pageId = :pageId")
    fun getTextItemsForPage(pageId: Long): Flow<List<TextItemEntity>>

    @Query("SELECT * FROM text_items WHERE pageId = :pageId")
    suspend fun getTextItemsListForPage(pageId: Long): List<TextItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextItem(item: TextItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextItems(items: List<TextItemEntity>): List<Long>

    @Update
    suspend fun updateTextItem(item: TextItemEntity)

    @Delete
    suspend fun deleteTextItem(item: TextItemEntity)

    @Query("SELECT text_items.* FROM text_items INNER JOIN pages ON text_items.pageId = pages.id WHERE pages.notebookId = :notebookId AND text_items.text LIKE '%' || :query || '%'")
    suspend fun searchInNotebook(notebookId: Long, query: String): List<TextItemEntity>

    @Query("SELECT * FROM text_items WHERE userId = :userId AND text LIKE '%' || :query || '%'")
    suspend fun searchAllText(userId: String, query: String): List<TextItemEntity>

    @Query("DELETE FROM text_items WHERE pageId = :pageId")
    suspend fun clearTextForPage(pageId: Long)

    @Query("DELETE FROM text_items WHERE userId = :userId")
    suspend fun clearTextForUser(userId: String)
}

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE pageId = :pageId")
    fun getMediaItemsForPage(pageId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE pageId = :pageId")
    suspend fun getMediaItemsListForPage(pageId: Long): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>): List<Long>

    @Update
    suspend fun updateMediaItem(item: MediaItemEntity)

    @Delete
    suspend fun deleteMediaItem(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE pageId = :pageId")
    suspend fun clearMediaForPage(pageId: Long)

    @Query("DELETE FROM media_items WHERE userId = :userId")
    suspend fun clearMediaForUser(userId: String)
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

    @Query("DELETE FROM audio_recordings WHERE userId = :userId")
    suspend fun clearAudioForUser(userId: String)
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

    @Query("DELETE FROM page_snapshots WHERE userId = :userId")
    suspend fun clearSnapshotsForUser(userId: String)
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

    @Query("DELETE FROM flashcards WHERE userId = :userId")
    suspend fun clearFlashcardsForUser(userId: String)
}
