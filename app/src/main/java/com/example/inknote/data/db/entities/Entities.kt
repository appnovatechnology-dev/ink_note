package com.example.inknote.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#4F46E5",
    val sortOrder: Int = 0,
    val isExpanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notebooks",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId")]
)
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val title: String,
    val coverColorHex: String = "#4F46E5",
    val coverPattern: String = "SOLID",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 1,
    val audioDurationMs: Long = 0L,
    val isArchived: Boolean = false,
    val tags: String = "",
    val isLocked: Boolean = false,
    val pinCode: String? = null
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId"), Index(value = ["notebookId", "pageNumber"], unique = true)]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageNumber: Int = 1,
    val templateType: String = "BLANK",
    val templateSpacing: Float = 28f,
    val customBgPath: String? = null,
    val pdfPageNumber: Int? = null,
    val pageSize: String = "A4",
    val width: Float = 1000f,
    val height: Float = 1414f, // Standard A4 ratio
    val tags: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val toolType: String = "PEN",
    val colorArgb: Long = 0xFF1E293BL,
    val strokeWidth: Float = 3.5f,
    val alpha: Float = 1.0f,
    val pointsJson: String,
    val audioTimestampMs: Long? = null,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "shapes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class ShapeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val shapeType: String = "RECTANGLE",
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 100f,
    val endY: Float = 100f,
    val strokeColorArgb: Long = 0xFF1E293BL,
    val fillColorArgb: Long = 0x00000000L,
    val strokeWidth: Float = 3.5f,
    val rotationDegrees: Float = 0f,
    val isFilled: Boolean = false,
    val sideCount: Int = 5,
    val audioTimestampMs: Long? = null,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "text_items",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class TextItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val text: String,
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 220f,
    val height: Float = 80f,
    val fontSize: Float = 16f,
    val fontColorArgb: Long = 0xFF1E293BL,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val backgroundColorArgb: Long = 0x00000000L,
    val audioTimestampMs: Long? = null,
    val isMathFormula: Boolean = false,
    val linkNotebookId: Long? = null,
    val linkNotebookTitle: String? = null
)

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val localUri: String,
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 260f,
    val height: Float = 200f,
    val rotationDegrees: Float = 0f,
    val audioTimestampMs: Long? = null
)

@Entity(
    tableName = "audio_recordings",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val fileName: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val recordedAt: Long = System.currentTimeMillis(),
    val title: String = "Recording"
)

@Entity(
    tableName = "page_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class PageSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val notebookId: Long,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val strokesJson: String = "",
    val shapesJson: String = "",
    val textJson: String = ""
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val frontText: String,
    val backText: String,
    val category: String = "General",
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val isMastered: Boolean = false
)
