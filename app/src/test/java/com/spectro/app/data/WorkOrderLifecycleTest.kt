package com.spectro.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderLifecycleTest {
    @Test
    fun deliveredAndCompletedOrdersAreFinishedAndArchived() {
        assertTrue(WorkStatus.DELIVERED.isFinishedOrder())
        assertTrue(WorkStatus.DELIVERED.isArchivedOrder())
        assertTrue(WorkStatus.COMPLETED.isFinishedOrder())
        assertTrue(WorkStatus.COMPLETED.isArchivedOrder())
        assertTrue(WorkStatus.CLOSED.isFinishedOrder())
        assertTrue(WorkStatus.CLOSED.isArchivedOrder())
    }

    @Test
    fun cancelledIsFinishedButNotArchivedProductionHistory() {
        assertTrue(WorkStatus.CANCELLED.isFinishedOrder())
        assertFalse(WorkStatus.CANCELLED.isArchivedOrder())
    }

    @Test
    fun activeProductionStatusesRemainActive() {
        assertFalse(WorkStatus.IN_PROCESS.isFinishedOrder())
        assertFalse(WorkStatus.IN_PRODUCTION.isFinishedOrder())
        assertFalse(WorkStatus.WAITING_CLIENT.isFinishedOrder())
        assertFalse(WorkStatus.BLOCKED.isFinishedOrder())
    }
}
