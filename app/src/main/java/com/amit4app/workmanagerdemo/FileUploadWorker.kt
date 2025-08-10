package com.amit4app.workmanagerdemo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.delay
import java.io.File
import java.io.InputStream
import kotlin.math.min

class FileUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repo = UploadRepo(appContext)

    override suspend fun doWork(): Result {
        return try {
            val uriStr = inputData.getString(KEY_FILE_URI) ?: return fail("No URI")
            val uploadId = inputData.getString(KEY_UPLOAD_ID) ?: return fail("No uploadId")
            val uri = Uri.parse(uriStr)

            val name = queryDisplayName(applicationContext, uri) ?: "File"
            val total = queryFileSizeSafe(applicationContext, uri)
                ?: return fail("Could not determine file size")

            setForeground(createForegroundInfo(0, name))

            var offset = repo.getUploadedBytes(uploadId).coerceAtMost(total)
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                skipFully(input, offset)

                val buf = ByteArray(64 * 1024)
                while (offset < total) {
                    val toRead = min(buf.size.toLong(), (total - offset)).toInt()
                    val read = input.read(buf, 0, toRead)
                    if (read == -1) break

                    // Simulate network
                    delay(25)

                    offset += read
                    repo.saveUploadedBytes(uploadId, offset)

                    val progress = ((offset * 100) / total).toInt()
                    setProgress(workDataOf(PROGRESS to progress))
                    setForeground(createForegroundInfo(progress, name))

                    if (isStopped) return Result.retry()
                }
            } ?: return fail("Cannot open input stream")

            repo.clear(uploadId)
            Result.success()
        } catch (t: Throwable) {
            Log.e("FileUploadWorker", "doWork failed", t)
            Result.retry() // or Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "upload_channel")
            .setContentTitle("Uploading: $title")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun fail(msg: String): Result {
        Log.e("FileUploadWorker", msg)
        return Result.failure(workDataOf("error" to msg))
    }

    companion object {
        const val KEY_FILE_URI = "fileUri"
        const val KEY_UPLOAD_ID = "uploadId"
        const val PROGRESS = "progress"
        const val NOTIF_ID = 1001
    }

    // --- Helpers ---
    private fun queryDisplayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun queryFileSizeSafe(ctx: Context, uri: Uri): Long? {
        // Try OpenableColumns.SIZE
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val size = c.getLong(0)
                if (size > 0) return size
            }
        }
        // Fallback: AssetFileDescriptor length
        ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length >= 0) return afd.length
        }
        return null
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}