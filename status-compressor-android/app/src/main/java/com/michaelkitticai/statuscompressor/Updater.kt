package com.michaelkitticai.statuscompressor

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Self-update, same idea as Mifugo Wallet: on launch (and on return to the app, at most every
 * 10 minutes) read latest.json from the android-latest GitHub release. If CI has published a
 * newer versionCode, offer to download the APK and hand it to the system installer.
 *
 * Updates only install if they're signed with the same key as the installed app (see
 * keystore/shared.keystore and the SIGNING_* secrets).
 */
object Updater {

    private const val MANIFEST_URL =
        "https://github.com/MichaelKitticai/Games/releases/download/android-latest/latest.json"
    private const val APK_NAME = "status-compressor-update.apk"
    private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L

    private data class Release(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var lastCheckMs = 0L
    private var dialogShowing = false
    /** Set while the user is in "install unknown apps" settings; picked up in [resumeIfPending]. */
    private var pendingAfterPermission: Release? = null

    fun check(activity: Activity) {
        if (dialogShowing || System.currentTimeMillis() - lastCheckMs < CHECK_INTERVAL_MS) return
        io.execute {
            val release = try {
                fetch()
            } catch (e: Exception) {
                return@execute // Offline or the release is mid-publish; try again next time.
            }
            lastCheckMs = System.currentTimeMillis()
            if (release.versionCode <= installedVersion(activity)) return@execute
            main.post {
                if (!activity.isFinishing && !activity.isDestroyed && !dialogShowing) showDialog(activity, release)
            }
        }
    }

    /** Call from onResume so the update continues after the user allows installs. */
    fun resumeIfPending(activity: Activity) {
        val release = pendingAfterPermission ?: return
        if (activity.packageManager.canRequestPackageInstalls()) {
            pendingAfterPermission = null
            download(activity, release)
        }
    }

    private fun fetch(): Release {
        val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return Release(
                versionCode = o.getLong("versionCode"),
                versionName = o.optString("versionName", o.getLong("versionCode").toString()),
                apkUrl = o.getString("apkUrl"),
                notes = o.optString("notes", ""),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun installedVersion(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    private fun showDialog(activity: Activity, release: Release) {
        val message = buildString {
            append("Version ${release.versionName} is ready.")
            if (release.notes.isNotBlank()) append("\n\nWhat's new:\n${release.notes}")
        }
        dialogShowing = true
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Update now") { _, _ -> onAccepted(activity, release) }
            .setNegativeButton("Later", null)
            .setOnDismissListener { dialogShowing = false }
            .show()
    }

    private fun onAccepted(activity: Activity, release: Release) {
        if (activity.packageManager.canRequestPackageInstalls()) {
            download(activity, release)
            return
        }
        pendingAfterPermission = release
        AlertDialog.Builder(activity)
            .setTitle("Allow updates")
            .setMessage(
                "Android needs your OK once for Status Compressor to install its own updates. " +
                    "On the next screen, turn on \"Allow from this source\", then come back."
            )
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                } catch (e: Exception) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                }
            }
            .setNegativeButton("Cancel") { _, _ -> pendingAfterPermission = null }
            .show()
    }

    private fun apkFile(context: Context) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)

    private fun download(activity: Activity, release: Release) {
        val app = activity.applicationContext
        apkFile(app).delete()
        val dm = app.getSystemService(DownloadManager::class.java)
        val id = try {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("Status Compressor ${release.versionName}")
                    .setDescription("Downloading update...")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
            )
        } catch (e: Exception) {
            toast(activity, "Couldn't start the update download.")
            return
        }
        toast(activity, "Downloading update...")
        poll(activity, dm, id)
    }

    private fun poll(activity: Activity, dm: DownloadManager, id: Long) {
        main.postDelayed({
            val status = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> install(activity)
                DownloadManager.STATUS_FAILED, null -> toast(activity, "Update download failed. Try again later.")
                else -> poll(activity, dm, id)
            }
        }, 700)
    }

    private fun install(activity: Activity) {
        val apk = apkFile(activity)
        if (!apk.exists() || apk.length() == 0L) {
            toast(activity, "The update download was empty. Try again later.")
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun toast(context: Context, text: String) =
        main.post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
}
