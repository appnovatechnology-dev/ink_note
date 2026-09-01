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
@Config(sdk = [36])
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
}
