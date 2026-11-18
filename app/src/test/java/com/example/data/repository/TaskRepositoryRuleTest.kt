package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChecklistItem
import com.example.data.model.HouseholdTask
import com.example.data.model.SharedChecklistItem
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertFailsWith

class TaskRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertTask_authenticatedOwner_createsAndObservesTask() = runBlocking<Unit> {
        signInTestUser(ALICE_EMAIL)
        val repo = TaskRepository(firestore, auth)

        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertTask(HouseholdTask(title = "Restock Milk Crates", category = "Restock")).getOrThrow()
        }
        assertThat(taskId).isNotEmpty()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTasks().first { list -> list.any { it.id == taskId } }
        }
        assertThat(observed.first { it.id == taskId }.title).isEqualTo("Restock Milk Crates")
    }

    @Test
    fun renameChecklistCategory_updatesItemsAndCategories() = runBlocking<Unit> {
        signInTestUser(ALICE_EMAIL)
        val repo = TaskRepository(firestore, auth)

        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertChecklistItem(
                ChecklistItem(title = "Sparkling Water", quantity = "24", category = "Beverages")
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.renameChecklistCategory("Beverages", "Cold Drinks", isDefaultCategory = true).getOrThrow()
        }

        val updatedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChecklistItems().first { list ->
                list.any { it.id == itemId && it.category == "Cold Drinks" }
            }
        }
        assertThat(updatedItems.first { it.id == itemId }.category).isEqualTo("Cold Drinks")
    }

    @Test
    fun sharedChecklist_collaboratorCanJoinAddItemAndRenameCategory() = runBlocking<Unit> {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = TaskRepository(firestore, auth)
        val shareCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSharedChecklist("Shop Opening List").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = TaskRepository(firestore, auth)
        val joined = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinSharedChecklist("https://gen-lang-client-0080616262.web.app/?checklist=$shareCode").getOrThrow()
        }
        assertThat(joined.shareCode).isEqualTo(shareCode)

        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.upsertSharedItem(
                shareCode,
                SharedChecklistItem(title = "Receipt Paper Rolls", quantity = "5 boxes", category = "Supplies")
            ).getOrThrow()
        }
        assertThat(itemId).isNotEmpty()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.renameSharedCategory(shareCode, "Supplies", "POS Supplies").getOrThrow()
        }

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSharedChecklistItems(shareCode).first { list ->
                list.any { it.id == itemId && it.category == "POS Supplies" }
            }
        }
        assertThat(items).hasSize(1)
    }

    @Test
    fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking<Unit> {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TaskRepository(firestore, auth)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertTask(HouseholdTask(title = "Alice Cash Count")).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobErr = assertFailsWith<Exception> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("tasks").document(taskId)
                    .get()
                    .let { com.google.android.gms.tasks.Tasks.await(it) }
            }
        }
        val fsErr = checkNotNull(bobErr.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $bobErr" }
        assertThat(fsErr.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun observeTasks_unauthenticatedUser_fails() = runBlocking<Unit> {
        auth.signOut()
        val repo = TaskRepository(firestore, auth)
        assertFailsWith<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks().first()
            }
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_owner@shopkeep.test"
        const val BOB_EMAIL = "bob_staff@shopkeep.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
