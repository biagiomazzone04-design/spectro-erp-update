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
}
