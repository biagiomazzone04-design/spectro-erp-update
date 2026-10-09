package com.spectro.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object AttachmentStore {
    fun importToPrivateStorage(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if(cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        val mime = context.contentResolver.getType(uri)
        val originalExtension = displayName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
        val extension = originalExtension ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.orEmpty()) ?: "bin"
        val stem = displayName?.substringBeforeLast('.')?.replace(Regex("[^A-Za-z0-9_-]"), "_")?.take(80)?.ifBlank { null } ?: "allegato"
        val out = File(dir, "${UUID.randomUUID()}_${stem}.$extension")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Impossibile leggere l'allegato selezionato." }
                out.outputStream().use { output -> input.copyTo(output) }
            }
        } catch(t: Exception) { out.delete(); throw t }
        return out.absolutePath
    }

    fun shareUri(context: Context, path: String): Uri {
        val file = File(path)
        require(file.isFile) { "Allegato non disponibile: ${file.name}" }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
