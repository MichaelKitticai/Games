package com.michaelkitticai.statuscompressor

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File

/** Log tag for the app's own messages. */
const val TAG = "StatusCompressor"

/**
 * Lets testers send the app's log without a USB cable: apps can read their own log, so we dump
 * it with some phone details into a text file and open the share sheet.
 */
object DebugLog {

    fun share(context: Context) {
        val file = File(context.cacheDir, "out/status-compressor-log.txt")
        file.parentFile?.mkdirs()
        val header = buildString {
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ABIs ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Chip: ${if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE}")
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("App ${pkg.versionName} (${pkg.longVersionCode}), x264 ${if (X264.available) "loaded" else "NOT loaded"}")
            appendLine()
        }
        val log = try {
            ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Couldn't read the log: $e"
        }
        file.writeText(header + log)

        val uri = Storage.shareUri(context, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Status Compressor debug log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, "Send debug log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
