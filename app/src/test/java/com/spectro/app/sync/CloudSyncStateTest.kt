package com.spectro.app.sync

import com.spectro.app.data.LocalSyncState
import com.spectro.app.data.SpectroSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncStateTest {
    @Test
    fun snapshotCountAlwaysIncludesSettings() {
        assertEquals(1, SpectroSnapshot().recordCount)
    }

    @Test
    fun differentRemoteRevisionIsAnExplicitConflict() {
        val inspection = SyncInspection(
            local = LocalSyncState(baseRevision = 4),
            remote = RemoteWorkspace(exists = true, revision = 5)
        )
        assertTrue(inspection.hasConflict)
    }

    @Test
    fun matchingRevisionCanBePublished() {
        val inspection = SyncInspection(
            local = LocalSyncState(baseRevision = 5),
            remote = RemoteWorkspace(exists = true, revision = 5)
        )
        assertFalse(inspection.hasConflict)
    }

    @Test
    fun pendingChangesAreExplicitAndPersistable() {
        val state = LocalSyncState(baseRevision = 20, pendingChanges = true)
        assertTrue(state.pendingChanges)
        assertEquals(20, state.baseRevision)
    }

    @Test
    fun cloudArchiveIsSplitBelowTheSupabaseObjectLimit() {
        val chunk = 45L * 1024L * 1024L
        assertEquals(0, expectedCloudChunkCount(0, chunk))
        assertEquals(1, expectedCloudChunkCount(chunk, chunk))
        assertEquals(2, expectedCloudChunkCount(chunk + 1, chunk))
        assertEquals(3, expectedCloudChunkCount(100L * 1024L * 1024L, chunk))
    }

    @Test
    fun existingAttachmentsForceOnlyOneMirrorUpgrade() {
        assertTrue(requiresAttachmentMirrorUpgrade(currentVersion = 0, hasLocalAttachments = true))
        assertFalse(requiresAttachmentMirrorUpgrade(currentVersion = 0, hasLocalAttachments = false))
        assertTrue(requiresAttachmentMirrorUpgrade(currentVersion = 1, hasLocalAttachments = true))
        assertFalse(requiresAttachmentMirrorUpgrade(currentVersion = 2, hasLocalAttachments = true))
    }

    @Test
    fun attachmentsUseOneStablePathPerBinaryDigest() {
        val digest = "ab".repeat(32)
        val first = contentAddressedAttachmentPath("org-1", digest)
        val second = contentAddressedAttachmentPath("org-1", digest)
        assertEquals("org-1/attachments/by-sha256/$digest", first)
        assertEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAttachmentDigestCannotBecomeAStoragePath() {
        contentAddressedAttachmentPath("org-1", "not-a-sha256")
    }

    @Test
    fun interruptedCommitFromSameDeviceCanRebaseSafely() {
        val local = LocalSyncState(baseRevision = 20, deviceId = "phone-a", pendingChanges = true)
        assertTrue(canRebaseOwnCommittedRevision(local, RemoteWorkspace(exists = true, revision = 21, deviceId = "phone-a")))
        assertFalse(canRebaseOwnCommittedRevision(local, RemoteWorkspace(exists = true, revision = 21, deviceId = "phone-b")))
        assertFalse(canRebaseOwnCommittedRevision(local, RemoteWorkspace(exists = true, revision = 20, deviceId = "phone-a")))
    }

    @Test
    fun changedSnapshotFingerprintAlwaysRestoresPendingState() {
        assertTrue(shouldMarkSnapshotPending("", "first-snapshot"))
        assertTrue(shouldMarkSnapshotPending("snapshot-a", "snapshot-b"))
        assertFalse(shouldMarkSnapshotPending("snapshot-a", "snapshot-a"))
        assertFalse(shouldMarkSnapshotPending("snapshot-a", ""))
    }
}
