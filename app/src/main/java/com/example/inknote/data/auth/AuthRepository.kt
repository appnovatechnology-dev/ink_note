package com.example.inknote.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.inknote.data.cloud.CloudSyncManager
import com.example.inknote.data.cloud.CloudSyncState
import com.example.inknote.data.cloud.CloudUserRecord
import com.example.inknote.data.cloud.awaitTask
import com.example.inknote.data.db.AppDatabase
import com.example.inknote.data.db.entities.UserAccountEntity
import com.example.inknote.data.repository.NoteRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val noteRepository = NoteRepository.getInstance(context)
    val syncManager = CloudSyncManager.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("inknote_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserAccountEntity?>(null)
    val currentUser: StateFlow<UserAccountEntity?> = _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    val syncState: StateFlow<CloudSyncState> = syncManager.syncState
    val lastSyncTimestamp: StateFlow<Long> = syncManager.lastSyncTimestamp
    val isFirebaseActive: StateFlow<Boolean> = syncManager.isFirebaseActive

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        repositoryScope.launch {
            checkSession()
        }
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun checkSession() = withContext(Dispatchers.IO) {
        val savedUserId = prefs.getString("active_user_id", null)
        val savedEmail = prefs.getString("active_user_email", null)

        if (isFirebaseConfigured()) {
            try {
                val fbUser = FirebaseAuth.getInstance().currentUser
                if (fbUser != null && fbUser.email != null) {
                    val userEmail = fbUser.email!!
                    val localUser = db.userAccountDao().getUserById(fbUser.uid)
                        ?: db.userAccountDao().getUserByEmail(userEmail)
                        ?: UserAccountEntity(
                            id = fbUser.uid,
                            email = userEmail,
                            displayName = fbUser.displayName ?: "User",
                            passwordHash = "",
                            avatarColorHex = "#4F46E5",
                            createdAt = System.currentTimeMillis(),
                            lastLoginAt = System.currentTimeMillis()
                        )
                    val syncedUser = localUser.copy(id = fbUser.uid)
                    db.userAccountDao().insertUser(syncedUser)
                    prefs.edit()
                        .putString("active_user_id", syncedUser.id)
                        .putString("active_user_email", userEmail)
                        .apply()
                    _currentUser.value = syncedUser
                    _isInitialized.value = true
                    repositoryScope.launch {
                        syncManager.pullFromCloud(syncedUser)
                    }
                    return@withContext
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase auth check skipped: ${e.message}")
            }
        }

        if (!savedUserId.isNullOrBlank()) {
            val user = db.userAccountDao().getUserById(savedUserId)
            if (user != null) {
                _currentUser.value = user
                _isInitialized.value = true
                repositoryScope.launch {
                    syncManager.pullFromCloud(user)
                }
                return@withContext
            }
        }

        if (!savedEmail.isNullOrBlank()) {
            val user = db.userAccountDao().getUserByEmail(savedEmail)
                ?: syncManager.getCloudUser(savedEmail)?.let { cloudUser ->
                    val entity = UserAccountEntity(
                        id = cloudUser.id,
                        email = cloudUser.email,
                        displayName = cloudUser.displayName,
                        passwordHash = cloudUser.passwordHash,
                        avatarColorHex = cloudUser.avatarColorHex,
                        createdAt = cloudUser.createdAt,
                        lastLoginAt = System.currentTimeMillis()
                    )
                    db.userAccountDao().insertUser(entity)
                    entity
                }
            if (user != null) {
                _currentUser.value = user
                _isInitialized.value = true
                repositoryScope.launch {
                    syncManager.pullFromCloud(user)
                }
                return@withContext
            }
        }
        _isInitialized.value = true
    }

    suspend fun signUp(name: String, email: String, pass: String): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = name.trim().ifBlank { "User" }
        if (!isValidEmail(trimmedEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (pass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        var firebaseUserId: String? = null

        // 1. If Firebase is active, create account in Firebase Auth
        if (isFirebaseConfigured()) {
            try {
                val auth = FirebaseAuth.getInstance()
                val result = auth.createUserWithEmailAndPassword(trimmedEmail, pass).awaitTask()
                val fbUser = result.user
                firebaseUserId = fbUser?.uid

                // Update profile display name in Firebase
                val updateReq = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                fbUser?.updateProfile(updateReq)?.awaitTask()
                Log.d("AuthRepository", "Firebase Auth user created: $trimmedEmail (uid: $firebaseUserId)")
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase Auth signUp notice: ${e.message}")
                val msg = e.message ?: ""
                if (msg.contains("The email address is already in use", ignoreCase = true) ||
                    msg.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true)) {
                    return@withContext Result.failure(IllegalArgumentException("An account with this email already exists in Firebase. Please sign in."))
                }
            }
        }

        // Check if user already exists locally or in cloud registry
        val localExisting = db.userAccountDao().getUserByEmail(trimmedEmail)
        val cloudExisting = syncManager.getCloudUser(trimmedEmail)
        if (localExisting != null || cloudExisting != null) {
            return@withContext Result.failure(IllegalArgumentException("An account with this email already exists. Please sign in instead."))
        }

        val colors = listOf("#4F46E5", "#0D9488", "#E11D48", "#D97706", "#7C3AED", "#2563EB", "#059669")
        val avatarColor = colors.random()
        val userId = firebaseUserId ?: UUID.randomUUID().toString()
        val passHash = hashPassword(pass, trimmedEmail)

        val userEntity = UserAccountEntity(
            id = userId,
            email = trimmedEmail,
            displayName = trimmedName,
            passwordHash = passHash,
            avatarColorHex = avatarColor,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )

        // 1. Save user locally in Room
        db.userAccountDao().insertUser(userEntity)

        // 2. Initialize default welcome notebook and subjects strictly for this user ID
        noteRepository.initializeDefaultDataForUser(userId)

        // 3. Save to Cloud Registry & Firebase Firestore
        syncManager.saveCloudUser(
            CloudUserRecord(
                id = userId,
                email = trimmedEmail,
                displayName = trimmedName,
                passwordHash = passHash,
                avatarColorHex = avatarColor,
                createdAt = userEntity.createdAt,
                lastLoginAt = userEntity.lastLoginAt
            )
        )

        // 4. Push this new user's initial data to cloud
        syncManager.pushToCloud(userEntity)

        // 5. Save active session
        prefs.edit()
            .putString("active_user_id", userId)
            .putString("active_user_email", trimmedEmail)
            .apply()
        _currentUser.value = userEntity

        Result.success(userEntity)
    }

    suspend fun signIn(email: String, pass: String): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your email and password."))
        }

        var firebaseLoginSuccess = false
        var firebaseUid: String? = null
        var firebaseDisplayName: String? = null

        // 1. If Firebase is active, sign in with Firebase Auth
        if (isFirebaseConfigured()) {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.signInWithEmailAndPassword(trimmedEmail, pass).awaitTask()
                val fbUser = authResult.user
                if (fbUser != null) {
                    firebaseLoginSuccess = true
                    firebaseUid = fbUser.uid
                    firebaseDisplayName = fbUser.displayName
                    Log.d("AuthRepository", "Firebase Auth signIn succeeded for $trimmedEmail (uid: $firebaseUid)")
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase Auth signIn notice: ${e.message}")
            }
        }

        val expectedHash = hashPassword(pass, trimmedEmail)

        // Check local DB first
        var userEntity = db.userAccountDao().getUserByEmail(trimmedEmail)

        if (userEntity != null) {
            if (!firebaseLoginSuccess && userEntity.passwordHash.isNotEmpty() && userEntity.passwordHash != expectedHash) {
                return@withContext Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
            }
            val finalId = firebaseUid ?: userEntity.id
            val updated = userEntity.copy(
                id = finalId,
                displayName = if (!firebaseDisplayName.isNullOrBlank()) firebaseDisplayName else userEntity.displayName,
                lastLoginAt = System.currentTimeMillis()
            )
            db.userAccountDao().insertUser(updated)
            userEntity = updated
        } else {
            // Check Cloud Registry / Firestore
            val cloudUser = syncManager.getCloudUser(trimmedEmail)

            if (cloudUser == null && !firebaseLoginSuccess) {
                return@withContext Result.failure(IllegalArgumentException("No account found with this email. Please sign up."))
            }

            if (cloudUser != null && !firebaseLoginSuccess && cloudUser.passwordHash.isNotEmpty() && cloudUser.passwordHash != expectedHash) {
                return@withContext Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
            }

            val finalId = firebaseUid ?: cloudUser?.id ?: UUID.randomUUID().toString()
            val finalName = firebaseDisplayName ?: cloudUser?.displayName ?: "User"
            val finalAvatar = cloudUser?.avatarColorHex ?: "#4F46E5"
            val finalCreatedAt = cloudUser?.createdAt ?: System.currentTimeMillis()

            userEntity = UserAccountEntity(
                id = finalId,
                email = trimmedEmail,
                displayName = finalName,
                passwordHash = cloudUser?.passwordHash ?: expectedHash,
                avatarColorHex = finalAvatar,
                createdAt = finalCreatedAt,
                lastLoginAt = System.currentTimeMillis()
            )
            db.userAccountDao().insertUser(userEntity)
        }

        // Pull cloud data for this user to ensure all their notebooks from Firestore / other devices are synced
        syncManager.pullFromCloud(userEntity)

        // Save active session
        prefs.edit()
            .putString("active_user_id", userEntity.id)
            .putString("active_user_email", trimmedEmail)
            .apply()
        _currentUser.value = userEntity

        Result.success(userEntity)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user != null) {
            // Push latest before signing out
            syncManager.pushToCloud(user)
        }
        if (isFirebaseConfigured()) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase signOut: ${e.message}")
            }
        }
        prefs.edit()
            .remove("active_user_id")
            .remove("active_user_email")
            .apply()
        _currentUser.value = null
    }

    suspend fun continueAsGuest(): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val guestId = "local_guest_user"
        val guestEmail = "guest@inknote.local"
        var guestUser = db.userAccountDao().getUserById(guestId)
        if (guestUser == null) {
            guestUser = UserAccountEntity(
                id = guestId,
                email = guestEmail,
                displayName = "Local User",
                passwordHash = "",
                avatarColorHex = "#6366F1",
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            db.userAccountDao().insertUser(guestUser)
            noteRepository.initializeDefaultDataForUser(guestId)
        }
        prefs.edit()
            .putString("active_user_id", guestId)
            .putString("active_user_email", guestEmail)
            .apply()
        _currentUser.value = guestUser
        Result.success(guestUser)
    }

    fun triggerSync() {
        val user = _currentUser.value ?: return
        repositoryScope.launch {
            syncManager.pushToCloud(user)
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun hashPassword(password: String, salt: String): String {
        val bytes = "$salt:$password:inknote_salt_2026".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AuthRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
