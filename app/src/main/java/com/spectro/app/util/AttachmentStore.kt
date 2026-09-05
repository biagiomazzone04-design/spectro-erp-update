package com.spectro.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object AttachmentStore {
    fun importToPrivateStorage(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val ext = context.contentResolver.getType(uri)?.substringAfter('/')?.takeIf { it.length <= 6 } ?: "jpg"
        val out = File(dir, "img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Impossibile leggere l'immagine selezionata." }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out.absolutePath
    }

    fun shareUri(context: Context, path: String): Uri {
        val file = File(path)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
