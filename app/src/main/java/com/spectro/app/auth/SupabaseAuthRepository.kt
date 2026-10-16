package com.spectro.app.auth

import android.content.Context
import com.spectro.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.time.Instant

class SupabaseAuthRepository(context: Context) {
    private val projectUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val sessionStore = SecureSessionStore(context)
    private var currentSession: AuthSession? = null

    fun isConfigured(): Boolean = projectUrl.startsWith("https://") && anonKey.isNotBlank()
    fun configuredProjectUrl(): String = projectUrl

    suspend fun restore(): AuthState = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext AuthState.ConfigurationRequired(projectUrl)
        val stored = sessionStore.load() ?: return@withContext AuthState.SignedOut
        runCatching {
            val active = if (stored.expiresAtEpochSeconds <= Instant.now().epochSecond + 90) {
                refresh(stored.refreshToken)
            } else stored
            persist(active)
            loadAccess(active)
        }.getOrElse {
            sessionStore.clear()
            currentSession = null
            AuthState.SignedOut
        }
    }

    suspend fun signIn(email: String, password: String): AuthState = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && password.isNotBlank()) { "Inserisci email e password." }
        val response = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        val session = response.toSession()
        persist(session)
        loadAccess(session)
    }

    suspend fun signUp(email: String, password: String, displayName: String): String = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && password.length >= 8) { "Usa un’email valida e una password di almeno 8 caratteri." }
        request(
            path = "/auth/v1/signup",
            method = "POST",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("display_name", displayName.trim()))
        )
        "Richiesta inviata. Apri solo l’email più recente e conferma l’indirizzo; poi un amministratore dovrà assegnarti un ruolo."
    }

    suspend fun resendSignUpConfirmation(email: String): String = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && '@' in email) { "Inserisci prima l’indirizzo email usato per registrarti." }
        request(
            path = "/auth/v1/resend",
            method = "POST",
            body = JSONObject()
                .put("type", "signup")
                .put("email", email.trim())
        )
        "Nuovo link inviato. Apri solo l’email più recente: i link precedenti diventano obsoleti."
    }

    suspend fun retryAccess(): AuthState = withContext(Dispatchers.IO) {
        val session = validSession() ?: return@withContext AuthState.SignedOut
        loadAccess(session)
    }

    suspend fun bootstrapOrganization(name: String = "Spectro"): AuthState = withContext(Dispatchers.IO) {
        val session = validSession() ?: return@withContext AuthState.SignedOut
        request(
            path = "/rest/v1/rpc/bootstrap_spectro_organization",
            method = "POST",
            body = JSONObject().put("p_name", name.trim().ifBlank { "Spectro" }),
            accessToken = session.accessToken
        )
        loadAccess(session)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = currentSession ?: sessionStore.load()
        runCatching {
            if (session != null) request("/auth/v1/logout", "POST", JSONObject(), session.accessToken)
        }
        currentSession = null
        sessionStore.clear()
    }

    suspend fun authenticatedRpc(functionName: String, body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        require(functionName.matches(Regex("[a-z0-9_]+"))) { "Nome RPC non valido." }
        val session = validSession() ?: throw IllegalStateException("Sessione scaduta. Accedi di nuovo.")
        request("/rest/v1/rpc/$functionName", "POST", body, session.accessToken)
    }

    suspend fun uploadStorageObject(bucket: String, path: String, source: File) = withContext(Dispatchers.IO) {
        require(source.isFile) { "File di sincronizzazione non trovato." }
        val session = validSession() ?: throw IllegalStateException("Sessione scaduta. Accedi di nuovo.")
        val connection = storageConnection(bucket, path, "POST", session.accessToken).apply {
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("x-upsert", "false")
            setFixedLengthStreamingMode(source.length())
            doOutput = true
        }
        try {
            connection.outputStream.use { output -> source.inputStream().use { it.copyTo(output) } }
            ensureSuccess(connection)
        } finally { connection.disconnect() }
    }

    suspend fun downloadStorageObject(bucket: String, path: String, target: File) = withContext(Dispatchers.IO) {
        val session = validSession() ?: throw IllegalStateException("Sessione scaduta. Accedi di nuovo.")
        val connection = storageConnection(bucket, path, "GET", session.accessToken)
        try {
            ensureSuccess(connection, consumeSuccessBody = false)
            target.parentFile?.mkdirs()
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally { connection.disconnect() }
    }

    suspend fun deleteStorageObject(bucket: String, path: String) = withContext(Dispatchers.IO) {
        val session = validSession() ?: return@withContext
        val connection = storageConnection(bucket, path, "DELETE", session.accessToken)
        try { ensureSuccess(connection) } finally { connection.disconnect() }
    }

    private fun validSession(): AuthSession? {
        val stored = currentSession ?: sessionStore.load() ?: return null
        return if (stored.expiresAtEpochSeconds <= Instant.now().epochSecond + 90) {
            refresh(stored.refreshToken).also(::persist)
        } else stored.also { currentSession = it }
    }

    private fun refresh(refreshToken: String): AuthSession = request(
        path = "/auth/v1/token?grant_type=refresh_token",
        method = "POST",
        body = JSONObject().put("refresh_token", refreshToken)
    ).toSession()

    private fun persist(session: AuthSession) {
        currentSession = session
        sessionStore.save(session)
    }

    private fun loadAccess(session: AuthSession): AuthState {
        val raw = request(
            path = "/rest/v1/rpc/my_access_context",
            method = "POST",
            body = JSONObject(),
            accessToken = session.accessToken
        )
        val context = raw.optJSONObject("result") ?: raw
        val organizationId = context.optString("organization_id")
        if (organizationId.isBlank() || organizationId == "null") {
            return AuthState.AwaitingAccess(
                email = session.email,
                message = "Account valido, ma non ancora associato a un’organizzazione. Chiedi a un amministratore di assegnarti un ruolo."
            )
        }
        val permissions = context.optJSONArray("permissions").toStringSet()
        return AuthState.SignedIn(
            UserAccess(
                userId = session.userId,
                email = session.email,
                displayName = context.optString("display_name").ifBlank { session.email.substringBefore('@') },
                organizationId = organizationId,
                organizationName = context.optString("organization_name").ifBlank { "Spectro" },
                roleName = context.optString("role_name").ifBlank { "Utente" },
                permissions = permissions
            )
        )
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        accessToken: String? = null
    ): JSONObject {
        val connection = (URL(projectUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (accessToken != null) setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(errorMessage(status, text))
            }
            when {
                text.isBlank() -> JSONObject()
                text.trimStart().startsWith("[") -> JSONObject().put("result", JSONArray(text).optJSONObject(0) ?: JSONObject())
                else -> JSONObject(text)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun storageConnection(bucket: String, path: String, method: String, accessToken: String): HttpURLConnection {
        require(bucket.matches(Regex("[a-z0-9_-]+"))) { "Bucket non valido." }
        require(path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Percorso cloud non valido." }
        return (URL("$projectUrl/storage/v1/object/$bucket/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun ensureSuccess(connection: HttpURLConnection, consumeSuccessBody: Boolean = true) {
        val status = connection.responseCode
        if (status in 200..299) {
            if (consumeSuccessBody) connection.inputStream?.close()
            return
        }
        val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IllegalStateException(errorMessage(status, text))
    }

    private fun errorMessage(status: Int, text: String): String {
        val error = runCatching { JSONObject(text) }.getOrNull()
        val raw = error?.optString("msg")?.ifBlank { null }
            ?: error?.optString("message")?.ifBlank { null }
            ?: error?.optString("error_description")?.ifBlank { null }
            ?: ""
        return localizedAuthError(raw, status)
    }

    private fun JSONObject.toSession(): AuthSession {
        val user = optJSONObject("user") ?: JSONObject()
        val token = optString("access_token")
        val refresh = optString("refresh_token")
        require(token.isNotBlank() && refresh.isNotBlank()) { "Sessione Supabase non valida." }
        return AuthSession(
            accessToken = token,
            refreshToken = refresh,
            expiresAtEpochSeconds = Instant.now().epochSecond + optLong("expires_in", 3600),
            userId = user.optString("id"),
            email = user.optString("email")
        )
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet { for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add) }
    }
}
