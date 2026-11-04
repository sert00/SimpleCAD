package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class CadRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private val sampleComponent = CadComponent(
        name = "Scatola Base",
        primitiveType = ShapePrimitiveType.BOX,
        widthMm = 100f,
        depthMm = 80f,
        heightMm = 45f
    )

    @Test
    fun createAndGetProject_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CadRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                name = "Scatola Derivazione",
                templateType = "JUNCTION_BOX_TPU",
                description = "Con guarnizione TPU",
                components = listOf(sampleComponent),
                explodeFactor = 0.25f,
                multiObject3mf = true
            )
        }
        assertTrue(createResult.isSuccess)
        val projectId = createResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProjects(uid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(emitted.any { it.id == projectId && it.name == "Scatola Derivazione" })
    }

    @Test
    fun getProjectById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = CadRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(
                name = "Progetto Privato Alice",
                templateType = "STD_BOX_LID",
                description = "Privato",
                components = listOf(sampleComponent),
                explodeFactor = 0f,
                multiObject3mf = true
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CadRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getProjectById(projectId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CadRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserProjects("unauth_uid").first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val firestoreEx = caught?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_cad@test.com"
        const val BOB_EMAIL = "bob_cad@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
