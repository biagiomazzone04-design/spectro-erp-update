package com.spectro.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spectro.app.auth.AppPermission
import com.spectro.app.auth.AuthState
import com.spectro.app.auth.SupabaseAuthRepository
import com.spectro.app.data.SpectroRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Serializza i trasferimenti avviati dalla UI e dal lavoro persistente. */
object CloudSyncCoordinator {
    val transferMutex = Mutex()
}

/**
 * Garantisce che una modifica già salvata sul dispositivo venga pubblicata anche
 * se l'app passa in background o il processo viene chiuso subito dopo il salvataggio.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = CloudSyncCoordinator.transferMutex.withLock {
        val repository = SpectroRepository(applicationContext)
        val auth = SupabaseAuthRepository(applicationContext)
        val cloud = CloudSyncRepository(applicationContext, auth, repository)
        try {
            cloud.prepareAttachmentMirrorUpgrade()
            cloud.prepareRecordMirrorUpgrade()
            val signedIn = auth.restore() as? AuthState.SignedIn ?: return@withLock Result.success()
            if (!signedIn.access.can(AppPermission.SYNC_USE)) return@withLock Result.success()
            cloud.importPendingWebZentha(signedIn.access)
            cloud.prepareSnapshotIntegritySync()
            if (!repository.localSyncState().pendingChanges) return@withLock Result.success()

            repeat(3) {
                if (!repository.localSyncState().pendingChanges) return@withLock Result.success()
                cloud.publish(signedIn.access)
            }
            if (repository.localSyncState().pendingChanges) Result.retry() else Result.success()
        } catch (_: CloudSyncConflictException) {
            // Un conflitto richiede una scelta esplicita dell'utente: i dati locali
            // restano marcati come pendenti e non vengono sovrascritti.
            Result.failure()
        } catch (_: Throwable) {
            if (runAttemptCount < 6) Result.retry() else Result.failure()
        }
    }
}

object CloudSyncWork {
    private const val UNIQUE_NAME = "spectro-automatic-cloud-sync"
    private const val PERIODIC_NAME = "spectro-periodic-cloud-pull"

    fun enqueue(context: Context, delayMillis: Long = 4_000) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
