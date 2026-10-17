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
        assertFalse(requiresAttachmentMirrorUpgrade(currentVersion = 1, hasLocalAttachments = true))
    }
}
