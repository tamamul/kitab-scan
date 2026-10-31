package com.marsa9.kitabscan

import android.content.Context
import android.net.Uri
import java.io.File

object PageStore {
    private fun dir(context: Context): File =
        File(context.filesDir, "pages").also { it.mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "jpg" }
            ?.sortedBy { it.name } ?: emptyList()

    fun addAll(context: Context, uris: List<Uri>) {
        val base = System.currentTimeMillis()
        uris.forEachIndexed { i, uri ->
            val out = File(dir(context), "${base}_${i.toString().padStart(3, '0')}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
    }

    fun delete(file: File) {
        file.delete()
    }
}