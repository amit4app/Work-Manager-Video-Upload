package com.amit4app.workmanagerdemo

import android.content.Context
import androidx.core.content.edit

/**
 * Tiny helper to checkpoint/resume upload progress.
 * Stores uploaded byte offset per uploadId in SharedPreferences.
 */
class UploadRepo(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("upload_prefs", Context.MODE_PRIVATE)
    }

    fun getUploadedBytes(uploadId: String): Long =
        prefs.getLong("off_$uploadId", 0L)

    fun saveUploadedBytes(uploadId: String, bytes: Long) {
        prefs.edit { putLong("off_$uploadId", bytes) }
    }

    fun clear(uploadId: String) {
        prefs.edit { remove("off_$uploadId") }
    }
}
