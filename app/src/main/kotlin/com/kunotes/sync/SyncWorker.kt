/**
 * The background job that runs after the app goes away (ARCHITECTURE §9.3, §4.5 `ON_STOP`):
 * commits the vault's changes and tries to push them, with a short network timeout.
 *
 * WorkManager runs it once a network is available, even if the app process was closed
 * meanwhile. Start it with [SyncWorker.enqueue].
 */
package com.kunotes.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kunotes.KuNotesApp
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val vaultId = inputData.getString(KEY_VAULT_ID) ?: return Result.failure()
        // WorkManager creates workers itself, so we fetch the shared stores from the Application.
        val container = (applicationContext as KuNotesApp).container
        val vault =
            container.settingsStore
                .current()
                .vaults
                .firstOrNull { it.id == vaultId } ?: return Result.success() // removed meanwhile
        val folder = container.vaultRegistry.appFolder(vault) ?: return Result.success()
        val credentials = container.tokenStore.load(vaultId)
        val pushed = container.syncController.pushQuickly(folder, credentials)
        // A failed push is safe (the commit stays local); try a few more times, then wait for the next sync.
        return when {
            pushed -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val KEY_VAULT_ID = "vaultId"
        private const val MAX_ATTEMPTS = 3

        /** Gives pending saves (flushed on `ON_STOP`) a moment to reach the disk first. */
        private const val START_DELAY_SECONDS = 3L

        /** Runs the job for [vaultId] once a network is available. A newer request replaces an older one. */
        fun enqueue(
            context: Context,
            vaultId: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(workDataOf(KEY_VAULT_ID to vaultId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(START_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("sync-$vaultId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
