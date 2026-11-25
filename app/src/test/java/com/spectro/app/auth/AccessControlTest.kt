package com.spectro.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessControlTest {
    @Test
    fun permissionKeysAreStableAndUnique() {
        val keys = AppPermission.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue("orders.view" in keys)
        assertTrue("users.manage" in keys)
        assertTrue("sync.use" in keys)
    }

    @Test
    fun userAccessUsesExactServerPermissions() {
        val access = UserAccess(
            userId = "user-1",
            email = "produzione@spectro.test",
            displayName = "Produzione",
            organizationId = "org-1",
            organizationName = "Spectro",
            roleName = "Produzione",
            permissions = setOf("orders.view", "production.manage")
        )

        assertTrue(access.can(AppPermission.ORDERS_VIEW))
        assertTrue(access.can(AppPermission.PRODUCTION_MANAGE))
        assertFalse(access.can(AppPermission.FINANCE_VIEW))
        assertFalse(access.can(AppPermission.USERS_MANAGE))
    }
}
