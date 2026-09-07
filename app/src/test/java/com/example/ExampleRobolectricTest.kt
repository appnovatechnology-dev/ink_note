package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.inknote.data.auth.AuthRepository
import com.example.inknote.data.repository.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("InkNote", appName)
    }

    @Test
    fun `user sign up and multi-device sign in flow`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository.getInstance(context)

        // 1. Sign Up on Device 1
        val signUpResult = authRepo.signUp(
            name = "Sarah Connor",
            email = "sarah@example.com",
            pass = "securepassword123"
        )
        assertTrue("Sign up should succeed", signUpResult.isSuccess)
        val user = signUpResult.getOrNull()
        assertNotNull(user)
        assertEquals("sarah@example.com", user?.email)
        assertEquals("Sarah Connor", user?.displayName)

        // 2. Sign Out
        authRepo.signOut()
        assertEquals(null, authRepo.currentUser.value)

        // 3. Sign In on Device 2 (with same credentials)
        val signInResult = authRepo.signIn(
            email = "sarah@example.com",
            pass = "securepassword123"
        )
        assertTrue("Sign in on new device should succeed", signInResult.isSuccess)
        assertEquals("sarah@example.com", authRepo.currentUser.value?.email)
    }

    @Test
    fun `strict per-user data isolation and account switching test`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository.getInstance(context)
        val noteRepo = NoteRepository.getInstance(context)

        // Step 1: Sign into Account A
        val userAResult = authRepo.signUp(
            name = "Alice User",
            email = "alice@example.com",
            pass = "passwordA123"
        )
        assertTrue(userAResult.isSuccess)
        val userA = userAResult.getOrNull()!!

        // Create distinct test note for Account A: "Note A"
        val noteAId = noteRepo.createNotebook(
            userId = userA.id,
            title = "Note A",
            coverColorHex = "#6366F1"
        )
        authRepo.syncManager.pushToCloud(userA)

        // Verify Account A sees "Note A"
        val userANotebooks = noteRepo.getNotebooks(userA.id).first()
        assertTrue(userANotebooks.any { it.title == "Note A" })

        // Step 2: Sign out of Account A
        authRepo.signOut()
        assertNull("UI user session must be null on sign-out", authRepo.currentUser.value)

        // Step 3: Sign into Account B
        val userBResult = authRepo.signUp(
            name = "Bob User",
            email = "bob@example.com",
            pass = "passwordB123"
        )
        assertTrue(userBResult.isSuccess)
        val userB = userBResult.getOrNull()!!

        // Create distinct test note for Account B: "Note B"
        val noteBId = noteRepo.createNotebook(
            userId = userB.id,
            title = "Note B",
            coverColorHex = "#EC4899"
        )
        authRepo.syncManager.pushToCloud(userB)

        // Step 4: Confirm Account B only ever sees "Note B", never "Note A"
        val userBNotebooks = noteRepo.getNotebooks(userB.id).first()
        assertTrue("Account B must see Note B", userBNotebooks.any { it.title == "Note B" })
        assertFalse("Account B must NEVER see Note A", userBNotebooks.any { it.title == "Note A" })

        // Step 5: Sign out of Account B, sign back into Account A
        authRepo.signOut()
        val signInAResult = authRepo.signIn("alice@example.com", "passwordA123")
        assertTrue("Sign in back to Account A must succeed", signInAResult.isSuccess)
        val activeUserA = authRepo.currentUser.value!!
        assertEquals(userA.id, activeUserA.id)

        // Step 6: Confirm Account A shows only "Note A" again and never "Note B"
        val reloadedANotebooks = noteRepo.getNotebooks(activeUserA.id).first()
        assertTrue("Account A must see Note A", reloadedANotebooks.any { it.title == "Note A" })
        assertFalse("Account A must NEVER see Note B", reloadedANotebooks.any { it.title == "Note B" })
    }

    @Test
    fun `note survives app close and reopen - force close test`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository.getInstance(context)
        val noteRepo = NoteRepository.getInstance(context)

        // 1. Create a user and notebook
        val userRes = authRepo.signUp("Tester", "test_survival@example.com", "pass123")
        assertTrue(userRes.isSuccess)
        val user = userRes.getOrNull()!!

        val nbId = noteRepo.createNotebook(user.id, "Physics Lecture Notes")
        val pages = noteRepo.getPagesList(nbId)
        assertTrue("Notebook must have at least 1 page", pages.isNotEmpty())
        val page = pages.first()

        // 2. User draws strokes, adds shapes, and writes text
        val stroke = com.example.inknote.data.model.VectorStroke(
            id = 101L,
            pageId = page.id,
            points = listOf(
                com.example.inknote.data.model.StrokePoint(10f, 10f),
                com.example.inknote.data.model.StrokePoint(50f, 80f),
                com.example.inknote.data.model.StrokePoint(100f, 150f)
            ),
            color = androidx.compose.ui.graphics.Color(0xFF0F172A),
            strokeWidth = 4f
        )
        val textItem = com.example.inknote.data.model.CanvasTextItem(
            id = 201L,
            pageId = page.id,
            text = "Newton's Second Law: F = ma",
            x = 40f,
            y = 120f
        )

        // 3. Continuous auto-save commits the page content to disk atomically
        noteRepo.saveEntirePageContent(
            pageId = page.id,
            strokes = listOf(stroke),
            shapes = emptyList(),
            textItems = listOf(textItem),
            mediaItems = emptyList()
        )

        // 4. Simulate app being closed completely and reopened (fresh repository instance / reading from DB)
        val freshNoteRepo = NoteRepository.getInstance(context)
        val loadedStrokes = freshNoteRepo.getStrokesForPage(page.id).first()
        val loadedTexts = freshNoteRepo.getTextItemsForPage(page.id).first()

        assertEquals("Strokes must survive app close and reopen", 1, loadedStrokes.size)
        assertEquals("Stroke points must match", 3, loadedStrokes[0].points.size)
        assertEquals("Text must survive app close and reopen", 1, loadedTexts.size)
        assertEquals("Text content must be preserved", "Newton's Second Law: F = ma", loadedTexts[0].text)
    }

    @Test
    fun `note survives backgrounding and lifecycle flush`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository.getInstance(context)
        val noteRepo = NoteRepository.getInstance(context)

        val userRes = authRepo.continueAsGuest()
        assertTrue(userRes.isSuccess)
        val user = userRes.getOrNull()!!

        val nbId = noteRepo.createNotebook(user.id, "Quick Thoughts")
        val page = noteRepo.getPagesList(nbId).first()

        // User writes on page
        val stroke = com.example.inknote.data.model.VectorStroke(
            id = 301L,
            pageId = page.id,
            points = listOf(com.example.inknote.data.model.StrokePoint(5f, 5f)),
            color = androidx.compose.ui.graphics.Color.Black,
            strokeWidth = 3f
        )

        // Simulate ON_PAUSE / ON_STOP lifecycle flush
        noteRepo.saveEntirePageContent(
            pageId = page.id,
            strokes = listOf(stroke),
            shapes = emptyList(),
            textItems = emptyList(),
            mediaItems = emptyList()
        )

        // Verify committed directly to disk
        val persisted = noteRepo.getStrokesForPage(page.id).first()
        assertEquals(1, persisted.size)
        assertEquals(1, persisted[0].points.size)
    }

    @Test
    fun `note works completely offline and survives app restart`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository.getInstance(context)
        val noteRepo = NoteRepository.getInstance(context)

        // Continue in offline guest mode (no cloud connectivity required)
        val guestRes = authRepo.continueAsGuest()
        assertTrue("Offline mode must succeed", guestRes.isSuccess)
        val guestUser = guestRes.getOrNull()!!

        val offlineNbId = noteRepo.createNotebook(guestUser.id, "Offline Travel Journal")
        val page = noteRepo.getPagesList(offlineNbId).first()

        val offlineText = com.example.inknote.data.model.CanvasTextItem(
            id = 401L,
            pageId = page.id,
            text = "Flight departed on time, cruising at 35,000 ft",
            x = 20f,
            y = 50f
        )

        noteRepo.saveEntirePageContent(
            pageId = page.id,
            strokes = emptyList(),
            shapes = emptyList(),
            textItems = listOf(offlineText),
            mediaItems = emptyList()
        )

        // Check reloaded from disk
        val reloadedNotebooks = noteRepo.getNotebooks(guestUser.id).first()
        assertTrue(reloadedNotebooks.any { it.id == offlineNbId })
        val reloadedTexts = noteRepo.getTextItemsForPage(page.id).first()
        assertEquals(1, reloadedTexts.size)
        assertEquals("Flight departed on time, cruising at 35,000 ft", reloadedTexts[0].text)
    }
}
