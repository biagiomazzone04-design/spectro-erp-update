package com.spectro.app.sync

import android.content.Context
import com.spectro.app.auth.SupabaseAuthRepository
import com.spectro.app.auth.UserAccess
import com.spectro.app.data.LocalSyncState
import com.spectro.app.data.SpectroRepository
import com.spectro.app.data.SpectroSnapshot
import com.spectro.app.util.BackupManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

internal fun expectedCloudChunkCount(sizeBytes: Long, chunkBytes: Long): Int {
    require(sizeBytes >= 0 && chunkBytes > 0)
    if (sizeBytes == 0L) return 0
    return (sizeBytes / chunkBytes + if (sizeBytes % chunkBytes == 0L) 0 else 1).toInt()
}

internal fun requiresAttachmentMirrorUpgrade(currentVersion: Int, hasLocalAttachments: Boolean): Boolean =
    currentVersion < 2 && hasLocalAttachments

internal fun contentAddressedAttachmentPath(organizationId: String, sha256: String): String {
    require(organizationId.isNotBlank() && !organizationId.contains('/'))
    require(sha256.matches(Regex("[0-9a-f]{64}")))
    return "$organizationId/attachments/by-sha256/$sha256"
}

internal fun canRebaseOwnCommittedRevision(local: LocalSyncState, remote: RemoteWorkspace): Boolean =
    remote.exists && remote.revision > local.baseRevision &&
        remote.deviceId.isNotBlank() && remote.deviceId == local.deviceId

internal fun shouldMarkSnapshotPending(lastPublishedFingerprint: String, currentFingerprint: String): Boolean =
    currentFingerprint.isNotBlank() && currentFingerprint != lastPublishedFingerprint

class CloudSyncRepository(
    context: Context,
    private val auth: SupabaseAuthRepository,
    private val repository: SpectroRepository
) {
    private val appContext = context.applicationContext
    private val syncPreferences = appContext.getSharedPreferences("spectro_cloud_sync", Context.MODE_PRIVATE)

    /**
     * Le installazioni aggiornate da una versione precedente potevano risultare
     * già allineate pur avendo nel mirror solo il conteggio degli allegati. Marca
     * una sola ripubblicazione completa quando trova file locali reali.
     */
    fun prepareAttachmentMirrorUpgrade(): Boolean {
        val currentVersion = syncPreferences.getInt(ATTACHMENT_MIRROR_VERSION_KEY, 0)
        val hasLocalAttachments = BackupManager.attachmentPaths(repository.snapshot()).any { File(it).isFile }
        if (!requiresAttachmentMirrorUpgrade(currentVersion, hasLocalAttachments)) return false
        val state = repository.localSyncState()
        if (!state.pendingChanges) repository.saveLocalSyncState(state.copy(pendingChanges = true))
        return true
    }

    /** Forza una sola pubblicazione completa quando cambia la struttura del
     * mirror consultabile dal web, anche se il workspace era già allineato. */
    fun prepareRecordMirrorUpgrade(): Boolean {
        if (syncPreferences.getInt(RECORD_MIRROR_VERSION_KEY, 0) >= RECORD_MIRROR_VERSION) return false
        val state = repository.localSyncState()
        if (!state.pendingChanges) repository.saveLocalSyncState(state.copy(pendingChanges = true))
        return true
    }

    /**
     * Non si affida soltanto al flag pendingChanges: confronta il contenuto ERP
     * effettivo con l'ultima revisione pubblicata. In questo modo anche un ordine
     * Zentha salvato mentre Android viene chiuso o interrotto viene rilevato dal
     * worker periodico e trasferito al portale senza interventi manuali.
     */
    fun prepareSnapshotIntegritySync(): Boolean {
        val fingerprint = snapshotFingerprint(repository.snapshot())
        if (!shouldMarkSnapshotPending(syncPreferences.getString(LAST_PUBLISHED_FINGERPRINT_KEY, "").orEmpty(), fingerprint)) return false
        val state = repository.localSyncState()
        if (!state.pendingChanges) repository.saveLocalSyncState(state.copy(pendingChanges = true))
        return true
    }

    suspend fun importPendingWebZentha(access: UserAccess): Int = try {
        val response = try {
            auth.authenticatedRpc(
                "get_web_pending_records",
                JSONObject().put("p_organization_id", access.organizationId)
            )
        } catch (t: Throwable) {
            if (!t.message.orEmpty().contains("get_web_pending_records", ignoreCase = true)) throw t
            auth.authenticatedRpc(
                "get_web_pending_zentha_records",
                JSONObject().put("p_organization_id", access.organizationId)
            )
        }
        repository.importPendingWebZentha(response.optJSONObject("result") ?: response)
    } catch (t: Throwable) {
        // Compatibilità con progetti che non hanno ancora applicato le migrazioni 007/008.
        if (t.message.orEmpty().contains("get_web_pending_zentha_records", ignoreCase = true)) 0 else throw t
    }

    suspend fun inspect(access: UserAccess): SyncInspection {
        importPendingWebZentha(access)
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
        importPendingWebZentha(access)
        var local = localStateFor(access.organizationId)
        val currentRemote = parseRemote(
            auth.authenticatedRpc(
                "get_workspace_sync_state",
                JSONObject().put("p_organization_id", access.organizationId)
            )
        )
        if (currentRemote.revision != local.baseRevision) {
            if (!canRebaseOwnCommittedRevision(local, currentRemote)) {
                throw CloudSyncConflictException(
                    "Il cloud contiene una revisione di un altro dispositivo. Verifica lo stato cloud prima di pubblicare: nessun dato è stato sovrascritto."
                )
            }
            // The server committed a previous upload from this installation,
            // but Android was interrupted before persisting its revision. Move
            // the optimistic base forward and publish the current local state.
            local = local.copy(
                baseRevision = currentRemote.revision,
                lastSeenRemoteRevision = currentRemote.revision,
                lastError = "",
                pendingChanges = true
            ).also(repository::saveLocalSyncState)
        }
        // Claim the currently pending batch before taking its snapshot. Any edit made while
        // the upload is running sets this flag again and is sent in the following revision.
        repository.saveLocalSyncState(local.copy(pendingChanges = false))
        val snapshot = repository.snapshot()
        val recovery = BackupManager.createRecoveryBackup(appContext, snapshot, "prima_pubblicazione_cloud")
        var transfer: File? = null
        val temporaryFiles = mutableListOf<File>()
        val uploadedPaths = mutableListOf<String>()
        try {
            val cloudAttachments = linkedMapOf<String, String>()
            BackupManager.attachmentPaths(snapshot).forEach { localPath ->
                val source = File(localPath)
                if (!source.isFile) return@forEach
                val remotePath = contentAddressedAttachmentPath(access.organizationId, source.sha256())
                // An identical binary always has the same path. Upsert makes
                // retries idempotent and never creates a second stored copy.
                auth.uploadStorageObject(BUCKET, remotePath, source, upsert = true)
                cloudAttachments[localPath] = remotePath
            }

            val cloudTransfer = BackupManager.createCloudTransferBackup(appContext, snapshot, cloudAttachments)
            transfer = cloudTransfer
            require(cloudTransfer.length() <= MAX_WORKSPACE_BYTES) {
                "Il workspace supera 1 GB. Esporta un backup manuale e contatta l’assistenza prima della sincronizzazione."
            }
            val digest = cloudTransfer.sha256()
            val transferId = UUID.randomUUID().toString()
            val chunks = splitIntoChunks(cloudTransfer, transferId).also(temporaryFiles::addAll)
            val chunkEntries = JSONArray()
            chunks.forEachIndexed { index, chunk ->
                val path = "${access.organizationId}/workspaces/$transferId-${(index + 1).toString().padStart(4, '0')}.spectrochunk"
                uploadedPaths += path
                auth.uploadStorageObject(BUCKET, path, chunk)
                chunkEntries.put(
                    JSONObject()
                        .put("path", path)
                        .put("size_bytes", chunk.length())
                        .put("sha256", chunk.sha256())
                )
            }

            val manifest = File(appContext.cacheDir, "cloud-sync/$transferId.spectrobackup").apply {
                parentFile?.mkdirs()
                writeText(
                    JSONObject()
                        .put("format_version", CHUNKED_FORMAT_VERSION)
                        .put("size_bytes", cloudTransfer.length())
                        .put("sha256", digest)
                        .put("chunks", chunkEntries)
                        .toString(),
                    Charsets.UTF_8
                )
            }.also(temporaryFiles::add)
            val manifestPath = "${access.organizationId}/workspaces/$transferId.spectrobackup"
            val chunkPaths = uploadedPaths.toList()
            uploadedPaths += manifestPath
            auth.uploadStorageObject(BUCKET, manifestPath, manifest)

            val response = try {
                val commonBody = JSONObject()
                        .put("p_organization_id", access.organizationId)
                        .put("p_expected_revision", local.baseRevision)
                        .put("p_manifest_path", manifestPath)
                        .put("p_manifest_sha256", manifest.sha256())
                        .put("p_manifest_size_bytes", manifest.length())
                        .put("p_archive_sha256", digest)
                        .put("p_archive_size_bytes", cloudTransfer.length())
                        .put("p_chunk_paths", JSONArray(chunkPaths))
                        .put("p_record_count", snapshot.recordCount)
                        .put("p_device_id", local.deviceId)
                        .put("p_records", BackupManager.toCloudRecords(snapshot, cloudAttachments))
                try {
                    auth.authenticatedRpc(
                        "publish_workspace_snapshot_v3",
                        JSONObject(commonBody.toString())
                            .put("p_attachment_paths", JSONArray(cloudAttachments.values.distinct()))
                    )
                } catch (t: Throwable) {
                    if (t.message.orEmpty().contains("SYNC_CONFLICT")) throw t
                    if (!t.message.orEmpty().contains("publish_workspace_snapshot_v3", ignoreCase = true)) throw t
                    // The compact format is already safe without migration 011;
                    // only automatic removal of old objects waits for v3.
                    auth.authenticatedRpc("publish_workspace_snapshot_v2", commonBody)
                }
            } catch (t: Throwable) {
                if (t.message.orEmpty().contains("SYNC_CONFLICT")) {
                    throw CloudSyncConflictException("Il cloud contiene una revisione più recente. Scaricala prima di pubblicare: nessun dato è stato sovrascritto.")
                }
                if (t.message.orEmpty().contains("publish_workspace_snapshot_v2", ignoreCase = true)) {
                    throw IllegalStateException("Manca l’aggiornamento cloud 004 su Supabase. Esegui la migrazione SQL indicata insieme a questa versione dell’app.")
                }
                throw t
            }

            val remote = parseRemote(response)
            val cleanupPaths = response.optJSONArray("cleanup_paths")?.let { paths ->
                List(paths.length()) { paths.optString(it) }.filter {
                    it.startsWith("${access.organizationId}/workspaces/") ||
                        it.startsWith("${access.organizationId}/attachments/")
                }
            }.orEmpty()
            // Cleanup is best-effort and happens only after the new revision
            // was atomically accepted. A cleanup failure never invalidates it.
            runCatching { auth.deleteStorageObjects(BUCKET, cleanupPaths) }
            runCatching {
                try {
                    auth.authenticatedRpc("ack_web_changes", JSONObject().put("p_organization_id", access.organizationId))
                } catch (t: Throwable) {
                    if (!t.message.orEmpty().contains("ack_web_changes", ignoreCase = true)) throw t
                    auth.authenticatedRpc("ack_web_zentha_changes", JSONObject().put("p_organization_id", access.organizationId))
                }
            }
            val latestLocal = repository.localSyncState()
            val saved = latestLocal.copy(
                baseRevision = remote.revision,
                lastSeenRemoteRevision = remote.revision,
                lastSyncedAt = Instant.now().toString(),
                lastError = "",
                recoveryBackupPath = recovery.absolutePath,
                pendingChanges = latestLocal.pendingChanges
            )
            repository.saveLocalSyncState(saved)
            syncPreferences.edit().putInt(ATTACHMENT_MIRROR_VERSION_KEY, ATTACHMENT_MIRROR_VERSION).apply()
            syncPreferences.edit().putInt(RECORD_MIRROR_VERSION_KEY, RECORD_MIRROR_VERSION).apply()
            syncPreferences.edit().putString(LAST_PUBLISHED_FINGERPRINT_KEY, snapshotFingerprint(snapshot)).apply()
            uploadedPaths.clear() // manifest e blocchi sono ora referenziati dalla revisione pubblicata
            return SyncTransferResult(SyncInspection(saved, remote), recovery)
        } catch (t: Throwable) {
            val latestLocal = repository.localSyncState()
            repository.saveLocalSyncState(latestLocal.copy(
                lastError = t.message ?: "Pubblicazione cloud non riuscita.",
                recoveryBackupPath = recovery.absolutePath,
                pendingChanges = true
            ))
            uploadedPaths.asReversed().forEach { path -> runCatching { auth.deleteStorageObject(BUCKET, path) } }
            throw t
        } finally {
            transfer?.delete()
            temporaryFiles.forEach(File::delete)
        }
    }

    suspend fun download(access: UserAccess): SyncTransferResult {
        val inspection = inspect(access)
        val remote = inspection.remote
        require(remote.exists && remote.storagePath.isNotBlank()) { "Non esiste ancora un workspace cloud da scaricare." }
        val transfer = File(appContext.cacheDir, "cloud-sync/download_${UUID.randomUUID()}.spectrobackup")
        try {
            if (remote.formatVersion >= CHUNKED_FORMAT_VERSION) {
                downloadChunkedWorkspace(access, remote, transfer)
            } else {
                auth.downloadStorageObject(BUCKET, remote.storagePath, transfer)
            }
            require(transfer.length() == remote.sizeBytes) { "Download incompleto: dimensione del backup cloud non valida." }
            require(transfer.sha256().equals(remote.sha256, ignoreCase = true)) { "Controllo integrità non superato: il backup cloud non verrà applicato." }
            val incoming = materializeCloudAttachments(access, BackupManager.readBackupFile(appContext, transfer))
            require(incoming.recordCount == remote.recordCount) { "Il contenuto cloud non corrisponde al riepilogo pubblicato." }

            val recovery = BackupManager.createRecoveryBackup(appContext, repository.snapshot(), "prima_download_cloud")
            repository.replaceAll(incoming)
            val saved = inspection.local.copy(
                baseRevision = remote.revision,
                lastSeenRemoteRevision = remote.revision,
                lastSyncedAt = Instant.now().toString(),
                lastError = "",
                recoveryBackupPath = recovery.absolutePath,
                pendingChanges = false
            )
            repository.saveLocalSyncState(saved)
            syncPreferences.edit().putString(LAST_PUBLISHED_FINGERPRINT_KEY, snapshotFingerprint(incoming)).apply()
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
            recoveryBackupPath = "",
            pendingChanges = false
        ).also(repository::saveLocalSyncState)
    }

    private fun splitIntoChunks(source: File, transferId: String): List<File> {
        val count = expectedCloudChunkCount(source.length(), CHUNK_BYTES)
        require(count in 1..MAX_CHUNKS) { "Il workspace richiede troppi blocchi cloud ($count)." }
        val chunks = ArrayList<File>(count)
        source.inputStream().use { input ->
            repeat(count) { index ->
                val chunk = File(appContext.cacheDir, "cloud-sync/$transferId-${index + 1}.part").apply {
                    parentFile?.mkdirs()
                }
                FileOutputStream(chunk).use { output ->
                    var remaining = minOf(CHUNK_BYTES, source.length() - index * CHUNK_BYTES)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        check(read > 0) { "Lettura del backup interrotta durante la suddivisione." }
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                chunks += chunk
            }
        }
        return chunks
    }

    private suspend fun downloadChunkedWorkspace(access: UserAccess, remote: RemoteWorkspace, target: File) {
        val manifestFile = File(appContext.cacheDir, "cloud-sync/manifest_${UUID.randomUUID()}.json")
        val partFile = File(appContext.cacheDir, "cloud-sync/part_${UUID.randomUUID()}.bin")
        try {
            auth.downloadStorageObject(BUCKET, remote.storagePath, manifestFile)
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optInt("format_version") == CHUNKED_FORMAT_VERSION) { "Manifest cloud non supportato." }
            require(manifest.optLong("size_bytes") == remote.sizeBytes && manifest.optString("sha256").equals(remote.sha256, true)) {
                "Il manifest cloud non corrisponde alla revisione pubblicata."
            }
            val chunks = manifest.optJSONArray("chunks") ?: JSONArray()
            require(chunks.length() in 1..MAX_CHUNKS) { "Elenco dei blocchi cloud non valido." }
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                for (index in 0 until chunks.length()) {
                    val entry = chunks.optJSONObject(index) ?: throw IllegalStateException("Blocco cloud non valido.")
                    val path = entry.optString("path")
                    require(path.matches(workspaceChunkPath(access.organizationId))) { "Percorso di un blocco cloud non valido." }
                    auth.downloadStorageObject(BUCKET, path, partFile)
                    require(partFile.length() == entry.optLong("size_bytes")) { "Download incompleto del blocco ${index + 1}." }
                    require(partFile.sha256().equals(entry.optString("sha256"), true)) { "Integrità non valida per il blocco ${index + 1}." }
                    partFile.inputStream().use { it.copyTo(output) }
                    partFile.delete()
                }
            }
        } finally {
            manifestFile.delete()
            partFile.delete()
        }
    }

    private fun workspaceChunkPath(organizationId: String) =
        Regex("^${Regex.escape(organizationId)}/workspaces/[0-9a-f-]+-[0-9]{4}\\.spectrochunk$")

    private suspend fun materializeCloudAttachments(access: UserAccess, snapshot: SpectroSnapshot): SpectroSnapshot {
        val prefix = "${access.organizationId}/attachments/"
        val localByRemote = linkedMapOf<String, String>()
        BackupManager.attachmentPaths(snapshot).filter { it.startsWith(prefix) }.distinct().forEach { remotePath ->
            require(remotePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                "Percorso allegato cloud non valido."
            }
            val remoteDigest = remotePath.substringAfterLast('/').takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val pathDigest = MessageDigest.getInstance("SHA-256")
                .digest(remotePath.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val target = File(appContext.filesDir, "attachments/cloud_$pathDigest")
            if (!target.isFile || (remoteDigest != null && !target.sha256().equals(remoteDigest, true))) {
                val temporary = File(appContext.cacheDir, "cloud-sync/attachment_${UUID.randomUUID()}.tmp")
                try {
                    auth.downloadStorageObject(BUCKET, remotePath, temporary)
                    if (remoteDigest != null) require(temporary.sha256().equals(remoteDigest, true)) {
                        "Integrità non valida per un allegato cloud."
                    }
                    target.parentFile?.mkdirs()
                    if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                } finally {
                    temporary.delete()
                }
            }
            localByRemote[remotePath] = target.absolutePath
        }
        return BackupManager.remapAttachments(snapshot) { localByRemote[it] ?: it }
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

    private fun snapshotFingerprint(snapshot: SpectroSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(BackupManager.toCloudRecords(snapshot).toString().toByteArray(Charsets.UTF_8))
        BackupManager.attachmentPaths(snapshot).sorted().forEach { path ->
            val file = File(path)
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(if (file.isFile) "|${file.length()}|${file.lastModified()}".toByteArray() else "|missing".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val BUCKET = "spectro-attachments"
        const val CHUNKED_FORMAT_VERSION = 2
        const val CHUNK_BYTES = 45L * 1024L * 1024L
        const val MAX_CHUNKS = 24
        const val MAX_WORKSPACE_BYTES = 1024L * 1024L * 1024L
        const val ATTACHMENT_MIRROR_VERSION_KEY = "attachment_mirror_version"
        const val ATTACHMENT_MIRROR_VERSION = 2
        const val RECORD_MIRROR_VERSION_KEY = "record_mirror_version"
        const val RECORD_MIRROR_VERSION = 7
        const val LAST_PUBLISHED_FINGERPRINT_KEY = "last_published_snapshot_fingerprint"
    }
}
