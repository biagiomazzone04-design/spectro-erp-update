package com.spectro.app.sync

import android.content.Context
import com.spectro.app.auth.SupabaseAuthRepository
import com.spectro.app.auth.UserAccess
import com.spectro.app.data.LocalSyncState
import com.spectro.app.data.SpectroRepository
import com.spectro.app.data.SpectroSnapshot
import com.spectro.app.util.BackupManager
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class RemoteWorkspace(
    val exists: Boolean = false,
    val revision: Long = 0,
    val storagePath: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0,
    val recordCount: Int = 0,
    val updatedAt: String = "",
    val updatedBy: String = "",
    val deviceId: String = "",
    val formatVersion: Int = 1
)

data class SyncInspection(
    val local: LocalSyncState,
    val remote: RemoteWorkspace
) {
    val hasConflict: Boolean
        get() = remote.exists && local.baseRevision > 0 && remote.revision != local.baseRevision
}

data class SyncTransferResult(
    val inspection: SyncInspection,
    val recoveryBackup: File
)

class CloudSyncConflictException(message: String) : IllegalStateException(message)

class CloudSyncRepository(
    context: Context,
    private val auth: SupabaseAuthRepository,
    private val repository: SpectroRepository
) {
    private val appContext = context.applicationContext

    suspend fun inspect(access: UserAccess): SyncInspection {
        val local = localStateFor(access.organizationId)
        val remote = parseRemote(auth.authenticatedRpc(
            "get_workspace_sync_state",
            JSONObject().put("p_organization_id", access.organizationId)
        ))
        val updatedLocal = local.copy(
            lastSeenRemoteRevision = remote.revision,
            lastError = ""
        )
        repository.saveLocalSyncState(updatedLocal)
        return SyncInspection(updatedLocal, remote)
    }

    suspend fun publish(access: UserAccess): SyncTransferResult {
        val local = localStateFor(access.organizationId)
        val snapshot = repository.snapshot()
        val recovery = BackupManager.createRecoveryBackup(appContext, snapshot, "prima_pubblicazione_cloud")
        val transfer = BackupManager.createCloudTransferBackup(appContext, snapshot)
        var uploadedPath: String? = null
        try {
            require(transfer.length() <= MAX_WORKSPACE_BYTES) {
                "Il workspace supera 50 MB. Riduci gli allegati o esporta un backup manuale prima della sincronizzazione."
            }
            val digest = transfer.sha256()
            val path = "${access.organizationId}/workspaces/${UUID.randomUUID()}.spectrobackup"
            auth.uploadStorageObject(BUCKET, path, transfer)
            uploadedPath = path

            val response = try {
                auth.authenticatedRpc(
                    "publish_workspace_snapshot",
                    JSONObject()
                        .put("p_organization_id", access.organizationId)
                        .put("p_expected_revision", local.baseRevision)
                        .put("p_storage_path", path)
                        .put("p_sha256", digest)
                        .put("p_size_bytes", transfer.length())
                        .put("p_record_count", snapshot.recordCount)
                        .put("p_device_id", local.deviceId)
                        .put("p_records", BackupManager.toCloudRecords(snapshot))
                )
            } catch (t: Throwable) {
                if (t.message.orEmpty().contains("SYNC_CONFLICT")) {
                    throw CloudSyncConflictException("Il cloud contiene una revisione più recente. Scaricala prima di pubblicare: nessun dato è stato sovrascritto.")
                }
                throw t
            }

            val remote = parseRemote(response)
            val saved = local.copy(
                baseRevision = remote.revision,
                lastSeenRemoteRevision = remote.revision,
                lastSyncedAt = Instant.now().toString(),
                lastError = "",
                recoveryBackupPath = recovery.absolutePath
            )
            repository.saveLocalSyncState(saved)
            uploadedPath = null // il file è ora referenziato dalla revisione pubblicata
            return SyncTransferResult(SyncInspection(saved, remote), recovery)
        } catch (t: Throwable) {
            repository.saveLocalSyncState(local.copy(
                lastError = t.message ?: "Pubblicazione cloud non riuscita.",
                recoveryBackupPath = recovery.absolutePath
            ))
            uploadedPath?.let { path -> runCatching { auth.deleteStorageObject(BUCKET, path) } }
            throw t
        } finally {
            transfer.delete()
        }
    }

    suspend fun download(access: UserAccess): SyncTransferResult {
        val inspection = inspect(access)
        val remote = inspection.remote
        require(remote.exists && remote.storagePath.isNotBlank()) { "Non esiste ancora un workspace cloud da scaricare." }
        val transfer = File(appContext.cacheDir, "cloud-sync/download_${UUID.randomUUID()}.spectrobackup")
        try {
            auth.downloadStorageObject(BUCKET, remote.storagePath, transfer)
            require(transfer.length() == remote.sizeBytes) { "Download incompleto: dimensione del backup cloud non valida." }
            require(transfer.sha256().equals(remote.sha256, ignoreCase = true)) { "Controllo integrità non superato: il backup cloud non verrà applicato." }
            val incoming = BackupManager.readBackupFile(appContext, transfer)
            require(incoming.recordCount == remote.recordCount) { "Il contenuto cloud non corrisponde al riepilogo pubblicato." }

            val recovery = BackupManager.createRecoveryBackup(appContext, repository.snapshot(), "prima_download_cloud")
            repository.replaceAll(incoming)
            val saved = inspection.local.copy(
                baseRevision = remote.revision,
                lastSeenRemoteRevision = remote.revision,
                lastSyncedAt = Instant.now().toString(),
                lastError = "",
                recoveryBackupPath = recovery.absolutePath
            )
            repository.saveLocalSyncState(saved)
            return SyncTransferResult(SyncInspection(saved, remote), recovery)
        } catch (t: Throwable) {
            repository.saveLocalSyncState(inspection.local.copy(lastError = t.message ?: "Download cloud non riuscito."))
            throw t
        } finally {
            transfer.delete()
        }
    }

    private fun localStateFor(organizationId: String): LocalSyncState {
        val current = repository.localSyncState()
        if (current.organizationId == organizationId) return current
        return current.copy(
            organizationId = organizationId,
            baseRevision = 0,
            lastSeenRemoteRevision = 0,
            lastSyncedAt = "",
            lastError = "",
            recoveryBackupPath = ""
        ).also(repository::saveLocalSyncState)
    }

    private fun parseRemote(json: JSONObject): RemoteWorkspace {
        val exists = json.optBoolean("exists", json.optLong("revision", 0) > 0)
        return RemoteWorkspace(
            exists = exists,
            revision = json.optLong("revision", 0),
            storagePath = json.optString("storage_path"),
            sha256 = json.optString("sha256"),
            sizeBytes = json.optLong("size_bytes", 0),
            recordCount = json.optInt("record_count", 0),
            updatedAt = json.optString("updated_at"),
            updatedBy = json.optString("updated_by"),
            deviceId = json.optString("device_id"),
            formatVersion = json.optInt("format_version", 1)
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val BUCKET = "spectro-attachments"
        const val MAX_WORKSPACE_BYTES = 50L * 1024L * 1024L
    }
}
