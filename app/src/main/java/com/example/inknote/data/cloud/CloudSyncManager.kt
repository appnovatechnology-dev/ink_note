package com.example.inknote.data.cloud

import android.content.Context
import android.util.Log
import com.example.inknote.data.db.AppDatabase
import com.example.inknote.data.db.entities.*
import com.example.inknote.data.repository.NoteRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class CloudSyncState {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR
}

data class CloudUserRecord(
    val id: String,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val avatarColorHex: String,
    val createdAt: Long,
    val lastLoginAt: Long
)

data class CloudUserDataBundle(
    val user: CloudUserRecord,
    val subjects: List<SubjectEntity>,
    val notebooks: List<NotebookEntity>,
    val pages: List<PageEntity>,
    val strokes: List<StrokeEntity>,
    val shapes: List<ShapeEntity>,
    val textItems: List<TextItemEntity>,
    val lastSyncedAt: Long
)

suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { exception -> cont.resumeWithException(exception) }
    addOnCanceledListener { cont.resumeWithException(java.util.concurrent.CancellationException("Task cancelled")) }
}

class CloudSyncManager private constructor(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val noteRepository = NoteRepository.getInstance(context)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val bundleAdapter = moshi.adapter(CloudUserDataBundle::class.java)

    private val _syncState = MutableStateFlow(CloudSyncState.IDLE)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _isFirebaseActive = MutableStateFlow(false)
    val isFirebaseActive: StateFlow<Boolean> = _isFirebaseActive.asStateFlow()

    private val cloudStoreDir: File
        get() {
            val dir = File(context.filesDir, "inknote_cloud_store")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        checkFirebaseStatus()
    }

    fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkFirebaseStatus() {
        _isFirebaseActive.value = isFirebaseConfigured()
    }

    private fun getCloudUserFile(userId: String): File {
        val sanitized = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cloudStoreDir, "user_$sanitized.json")
    }

    private fun getCloudEmailFallbackFile(email: String): File {
        val sanitized = email.lowercase().trim().replace("@", "_at_").replace(".", "_dot_")
        return File(cloudStoreDir, "email_$sanitized.json")
    }

    /**
     * Checks if a cloud account exists for the given email/userId
     */
    suspend fun getCloudUser(email: String): CloudUserRecord? = withContext(Dispatchers.IO) {
        val sanitizedEmail = email.lowercase().trim()
        
        // 1. Try Firebase Firestore
        if (isFirebaseConfigured()) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                // Check by email in index or document
                val snapshot = firestore.collection("inknote_sync").document(sanitizedEmail).get().awaitTask()
                if (snapshot.exists()) {
                    val bundleJson = snapshot.getString("bundleJson")
                    if (!bundleJson.isNullOrBlank()) {
                        val bundle = bundleAdapter.fromJson(bundleJson)
                        if (bundle != null) {
                            getCloudUserFile(bundle.user.id).writeText(bundleJson)
                            return@withContext bundle.user
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CloudSyncManager", "Firestore read warning: ${e.message}")
            }
        }

        // 2. Check local fallback file
        try {
            val file = getCloudEmailFallbackFile(email)
            if (file.exists()) {
                val bundle = bundleAdapter.fromJson(file.readText())
                if (bundle != null) return@withContext bundle.user
            }
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Error reading local cloud user for $email", e)
        }
        null
    }

    /**
     * Registers or updates a cloud account
     */
    suspend fun saveCloudUser(
        user: CloudUserRecord
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getCloudUserFile(user.id)
            val emailFile = getCloudEmailFallbackFile(user.email)
            val existingBundle = if (file.exists()) {
                try { bundleAdapter.fromJson(file.readText()) } catch (e: Exception) { null }
            } else null

            val bundle = if (existingBundle != null) {
                existingBundle.copy(
                    user = user,
                    lastSyncedAt = System.currentTimeMillis()
                )
            } else {
                CloudUserDataBundle(
                    user = user,
                    subjects = emptyList(),
                    notebooks = emptyList(),
                    pages = emptyList(),
                    strokes = emptyList(),
                    shapes = emptyList(),
                    textItems = emptyList(),
                    lastSyncedAt = System.currentTimeMillis()
                )
            }

            val json = bundleAdapter.toJson(bundle)
            file.writeText(json)
            emailFile.writeText(json)

            // If Firebase is available, sync user registry doc
            if (isFirebaseConfigured()) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val data = hashMapOf(
                        "ownerId" to user.id,
                        "userId" to user.id,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "avatarColorHex" to user.avatarColorHex,
                        "lastSyncedAt" to bundle.lastSyncedAt,
                        "bundleJson" to json
                    )
                    // Primary user document scoped by UID
                    firestore.collection("inknote_users").document(user.id)
                        .set(data, SetOptions.merge())
                        .awaitTask()
                    // Secondary lookup by email
                    firestore.collection("inknote_sync").document(user.email.lowercase().trim())
                        .set(data, SetOptions.merge())
                        .awaitTask()
                    _isFirebaseActive.value = true
                } catch (e: Exception) {
                    Log.w("CloudSyncManager", "Firestore user save warning: ${e.message}")
                }
            }

            true
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Error saving cloud user", e)
            false
        }
    }

    /**
     * Pushes ONLY the current user's local notebooks, subjects, pages, strokes, shapes, and texts
     * to Firebase & Cloud Store. Strictly scoped by user.id.
     */
    suspend fun pushToCloud(user: UserAccountEntity): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.SYNCING
        try {
            val file = getCloudUserFile(user.id)
            val emailFile = getCloudEmailFallbackFile(user.email)
            val subjects = db.subjectDao().getAllSubjectsList(user.id)
            val notebooks = db.notebookDao().getAllNotebooksList(user.id)
            val allPages = mutableListOf<PageEntity>()
            val allStrokes = mutableListOf<StrokeEntity>()
            val allShapes = mutableListOf<ShapeEntity>()
            val allTexts = mutableListOf<TextItemEntity>()

            for (nb in notebooks) {
                val pages = db.pageDao().getPagesListForNotebook(nb.id)
                allPages.addAll(pages.map { it.copy(userId = user.id) })
                for (page in pages) {
                    allStrokes.addAll(db.strokeDao().getStrokesListForPage(page.id).map { it.copy(userId = user.id) })
                    allShapes.addAll(db.shapeDao().getShapesListForPage(page.id).map { it.copy(userId = user.id) })
                    allTexts.addAll(db.textItemDao().getTextItemsListForPage(page.id).map { it.copy(userId = user.id) })
                }
            }

            val cloudUser = CloudUserRecord(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                passwordHash = user.passwordHash,
                avatarColorHex = user.avatarColorHex,
                createdAt = user.createdAt,
                lastLoginAt = System.currentTimeMillis()
            )

            val bundle = CloudUserDataBundle(
                user = cloudUser,
                subjects = subjects.map { it.copy(userId = user.id) },
                notebooks = notebooks.map { it.copy(userId = user.id) },
                pages = allPages,
                strokes = allStrokes,
                shapes = allShapes,
                textItems = allTexts,
                lastSyncedAt = System.currentTimeMillis()
            )

            val json = bundleAdapter.toJson(bundle)
            file.writeText(json)
            emailFile.writeText(json)

            // Sync to Firebase Firestore
            if (isFirebaseConfigured()) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val data = hashMapOf(
                        "ownerId" to user.id,
                        "userId" to user.id,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "avatarColorHex" to user.avatarColorHex,
                        "notebooksCount" to notebooks.size,
                        "pagesCount" to allPages.size,
                        "lastSyncedAt" to bundle.lastSyncedAt,
                        "bundleJson" to json
                    )
                    // Primary document keyed by Firebase UID
                    firestore.collection("inknote_users")
                        .document(user.id)
                        .set(data, SetOptions.merge())
                        .awaitTask()

                    // Email lookup
                    firestore.collection("inknote_sync")
                        .document(user.email.lowercase().trim())
                        .set(data, SetOptions.merge())
                        .awaitTask()

                    _isFirebaseActive.value = true
                    Log.d("CloudSyncManager", "Successfully synced ${notebooks.size} notebooks for user ${user.id} to Firestore")
                } catch (e: Exception) {
                    Log.w("CloudSyncManager", "Firebase Firestore push notice: ${e.message}")
                }
            }

            _lastSyncTimestamp.value = bundle.lastSyncedAt
            _syncState.value = CloudSyncState.SYNCED
            true
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Failed to push to cloud", e)
            _syncState.value = CloudSyncState.ERROR
            false
        }
    }

    /**
     * Pulls user data from Firebase Firestore (or Cloud Store) down to local Room database for user.id.
     */
    suspend fun pullFromCloud(user: UserAccountEntity): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.SYNCING
        val sanitizedEmail = user.email.lowercase().trim()
        try {
            var bundle: CloudUserDataBundle? = null

            // 1. Try Firebase Firestore by user.id
            if (isFirebaseConfigured()) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val docByUid = firestore.collection("inknote_users").document(user.id).get().awaitTask()
                    if (docByUid.exists()) {
                        val json = docByUid.getString("bundleJson")
                        if (!json.isNullOrBlank()) {
                            bundle = bundleAdapter.fromJson(json)
                            if (bundle != null) {
                                getCloudUserFile(user.id).writeText(json)
                                _isFirebaseActive.value = true
                            }
                        }
                    }
                    if (bundle == null) {
                        // Try fallback by email
                        val docByEmail = firestore.collection("inknote_sync").document(sanitizedEmail).get().awaitTask()
                        if (docByEmail.exists()) {
                            val json = docByEmail.getString("bundleJson")
                            if (!json.isNullOrBlank()) {
                                bundle = bundleAdapter.fromJson(json)
                                if (bundle != null) {
                                    getCloudUserFile(user.id).writeText(json)
                                    _isFirebaseActive.value = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("CloudSyncManager", "Firestore pull notice: ${e.message}")
                }
            }

            // 2. Fallback to local cloud backup
            if (bundle == null) {
                val file = getCloudUserFile(user.id)
                if (file.exists()) {
                    val json = file.readText()
                    bundle = try { bundleAdapter.fromJson(json) } catch (e: Exception) { null }
                }
                if (bundle == null) {
                    val emailFile = getCloudEmailFallbackFile(user.email)
                    if (emailFile.exists()) {
                        val json = emailFile.readText()
                        bundle = try { bundleAdapter.fromJson(json) } catch (e: Exception) { null }
                    }
                }
            }

            if (bundle == null) {
                // If this is a fresh user with no existing cloud or local data, initialize default data
                val localNotebooks = db.notebookDao().getAllNotebooksList(user.id)
                if (localNotebooks.isEmpty()) {
                    noteRepository.initializeDefaultDataForUser(user.id)
                    pushToCloud(user)
                }
                _syncState.value = CloudSyncState.SYNCED
                return@withContext true
            }

            // Purge and restore this user's data cleanly
            db.userAccountDao().insertUser(
                UserAccountEntity(
                    id = user.id,
                    email = user.email,
                    displayName = user.displayName,
                    passwordHash = user.passwordHash,
                    avatarColorHex = user.avatarColorHex,
                    createdAt = user.createdAt,
                    lastLoginAt = System.currentTimeMillis()
                )
            )

            // Clear old local records for this user before restoring
            db.notebookDao().deleteNotebooksForUser(user.id)
            db.subjectDao().deleteSubjectsForUser(user.id)
            db.pageDao().deletePagesForUser(user.id)
            db.strokeDao().clearStrokesForUser(user.id)
            db.shapeDao().clearShapesForUser(user.id)
            db.textItemDao().clearTextForUser(user.id)

            // Restore subjects with user.id
            for (sub in bundle.subjects) {
                db.subjectDao().insertSubject(sub.copy(userId = user.id))
            }

            // Restore notebooks with user.id
            for (nb in bundle.notebooks) {
                db.notebookDao().insertNotebook(nb.copy(userId = user.id))
            }

            // Restore pages with user.id
            for (page in bundle.pages) {
                db.pageDao().insertPage(page.copy(userId = user.id))
            }

            // Restore strokes, shapes, and texts with user.id
            if (bundle.strokes.isNotEmpty()) {
                db.strokeDao().insertStrokes(bundle.strokes.map { it.copy(userId = user.id) })
            }
            if (bundle.shapes.isNotEmpty()) {
                db.shapeDao().insertShapes(bundle.shapes.map { it.copy(userId = user.id) })
            }
            for (item in bundle.textItems) {
                db.textItemDao().insertTextItem(item.copy(userId = user.id))
            }

            _lastSyncTimestamp.value = bundle.lastSyncedAt
            _syncState.value = CloudSyncState.SYNCED
            true
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Failed to pull from cloud", e)
            _syncState.value = CloudSyncState.ERROR
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CloudSyncManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
