package com.michaelkitticai.statuscompressor

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

enum class ShareTarget(
    val label: String,
    val packages: List<String>,
    val extras: Map<String, String> = emptyMap(),
) {
    // "status@broadcast" opens WhatsApp's Status composer directly on versions that
    // support it. Others ignore it and show the usual picker, with "My status" on top.
    WHATSAPP_STATUS("Status", listOf("com.whatsapp", "com.whatsapp.w4b"), mapOf("jid" to "status@broadcast")),
    WHATSAPP_CHAT("Chat", listOf("com.whatsapp", "com.whatsapp.w4b")),
    INSTAGRAM("Instagram", listOf("com.instagram.android")),
    TIKTOK("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
}

object Sharing {

    /** The first installed package for [target], or null if the app isn't installed. */
    fun installedPackage(context: Context, target: ShareTarget): String? =
        target.packages.firstOrNull { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    fun appIcon(context: Context, pkg: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun share(context: Context, uris: List<Uri>, mime: String, target: ShareTarget?) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime
        // ClipData carries the read grant for every file, not just the first.
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            if (target == null) {
                context.startActivity(Intent.createChooser(intent, "Share to"))
                return
            }
            val pkg = installedPackage(context, target)
            if (pkg == null) {
                Toast.makeText(context, "${target.label} isn't installed", Toast.LENGTH_SHORT).show()
                return
            }
            intent.setPackage(pkg)
            target.extras.forEach { (k, v) -> intent.putExtra(k, v) }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }
}
