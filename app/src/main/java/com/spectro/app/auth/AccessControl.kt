package com.spectro.app.auth

enum class AppPermission(val key: String) {
    DASHBOARD_VIEW("dashboard.view"),
    CLIENTS_VIEW("clients.view"),
    CLIENTS_MANAGE("clients.manage"),
    ORDERS_VIEW("orders.view"),
    ORDERS_MANAGE("orders.manage"),
    PRODUCTION_VIEW("production.view"),
    PRODUCTION_MANAGE("production.manage"),
    INVENTORY_VIEW("inventory.view"),
    INVENTORY_MANAGE("inventory.manage"),
    ISSUES_VIEW("issues.view"),
    ISSUES_MANAGE("issues.manage"),
    COTTON_VIEW("cotton.view"),
    COTTON_MANAGE("cotton.manage"),
    ZENTHA_VIEW("zentha.view"),
    ZENTHA_MANAGE("zentha.manage"),
    FINANCE_VIEW("finance.view"),
    FINANCE_MANAGE("finance.manage"),
    TASKS_VIEW("tasks.view"),
    TASKS_MANAGE("tasks.manage"),
    REPORTS_EXPORT("reports.export"),
    SYNC_USE("sync.use"),
    BACKUP_MANAGE("backup.manage"),
    SETTINGS_MANAGE("settings.manage"),
    USERS_MANAGE("users.manage"),
    AUDIT_VIEW("audit.view")
}

data class UserAccess(
    val userId: String,
    val email: String,
    val displayName: String,
    val organizationId: String,
    val organizationName: String,
    val roleName: String,
    val permissions: Set<String>
) {
    fun can(permission: AppPermission): Boolean = permission.key in permissions
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class ConfigurationRequired(val projectUrl: String) : AuthState
    data class AwaitingAccess(val email: String, val message: String) : AuthState
    data class SignedIn(val access: UserAccess) : AuthState
}

internal data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String,
    val cachedAccess: UserAccess? = null
)
