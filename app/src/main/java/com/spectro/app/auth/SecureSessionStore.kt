package com.spectro.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("spectro_cloud_session", Context.MODE_PRIVATE)
    private val alias = "spectro_cloud_session_v1"

    fun save(session: AuthSession) {
        val raw = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtEpochSeconds)
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("access", session.cachedAccess?.let { access ->
                JSONObject()
                    .put("user_id", access.userId)
                    .put("email", access.email)
                    .put("display_name", access.displayName)
                    .put("organization_id", access.organizationId)
                    .put("organization_name", access.organizationName)
                    .put("role_name", access.roleName)
                    .put("permissions", org.json.JSONArray(access.permissions.toList()))
            } ?: JSONObject.NULL)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        preferences.edit()
            .putString("payload", Base64.encodeToString(cipher.doFinal(raw), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): AuthSession? = runCatching {
        val payload = preferences.getString("payload", null) ?: return null
        val iv = preferences.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val json = JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8))
        val access = json.optJSONObject("access")?.let { cached ->
            val permissionsJson = cached.optJSONArray("permissions")
            UserAccess(
                userId = cached.optString("user_id"),
                email = cached.optString("email"),
                displayName = cached.optString("display_name"),
                organizationId = cached.optString("organization_id"),
                organizationName = cached.optString("organization_name"),
                roleName = cached.optString("role_name"),
                permissions = buildSet {
                    if (permissionsJson != null) for (index in 0 until permissionsJson.length()) {
                        permissionsJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            )
        }
        AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = json.getLong("expires_at"),
            userId = json.getString("user_id"),
            email = json.optString("email"),
            cachedAccess = access
        )
    }.getOrElse {
        clear()
        null
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
