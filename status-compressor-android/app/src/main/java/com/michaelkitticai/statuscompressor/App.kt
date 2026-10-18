package com.michaelkitticai.statuscompressor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Accent = Color(0xFF25D366)
private val Bg = Color(0xFF0B141A)
private val Panel = Color(0xFF111B21)
private val Panel2 = Color(0xFF1F2C34)
private val TextMain = Color(0xFFE9EDEF)
private val Muted = Color(0xFF8696A0)
private val Danger = Color(0xFFF15C6D)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF062E14),
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = Panel2,
    onSurfaceVariant = Muted,
    error = Danger,
)

@Composable
fun App(vm: MainViewModel) {
    MaterialTheme(colorScheme = Colors) {
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
        ) { uris -> if (uris.isNotEmpty()) vm.add(uris) }

        Scaffold(containerColor = Bg) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header() }
                item { SavingsCard(vm.stats) }
                item {
                    PresetCard(
                        selected = vm.preset,
                        onSelect = vm::selectPreset,
                        saveToGallery = vm.saveToGallery,
                        onSaveToGallery = vm::updateSaveToGallery,
                    )
                }
                item {
                    Column {
                        Button(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Choose photos or videos", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        Text(
                            "Or from your gallery: tap Share, then Status Compressor.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
                if (vm.items.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent", color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (vm.items.any { !it.busy }) {
                                TextButton(onClick = vm::clearFinished) { Text("Clear finished") }
                            }
                        }
                    }
                    items(vm.items, key = { it.id }) { item -> ItemCard(item, onCancel = { vm.cancel(item) }) }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text("Status Compressor", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Post sharp photos and videos to WhatsApp, Instagram and TikTok.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun SavingsCard(stats: Stats) {
    Section {
        Text("Storage & data saved", color = Muted, fontSize = 13.sp)
        if (stats.files == 0) {
            Text("0 MB", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text("Your savings show up here after your first compression.", color = Muted, fontSize = 13.sp)
            return@Section
        }
        Text(formatBytes(stats.savedBytes), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Accent)
        val ratio = stats.outputBytes.toFloat() / stats.originalBytes.coerceAtLeast(1)
        val pct = ((1 - ratio) * 100).roundToInt()
        Text(
            "${stats.files} file${if (stats.files == 1) "" else "s"} · $pct% smaller on average",
            color = TextMain,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        SizeBar(ratio)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("Before ${formatBytes(stats.originalBytes)}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("After ${formatBytes(stats.outputBytes)}", color = Muted, fontSize = 12.sp)
        }
    }
}

/** Full bar = original size; green part = what's left after compression. */
@Composable
private fun SizeBar(ratio: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Panel2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(Accent)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetCard(
    selected: Preset,
    onSelect: (Preset) -> Unit,
    saveToGallery: Boolean,
    onSaveToGallery: (Boolean) -> Unit,
) {
    Section {
        Text("Compress for", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Preset.entries.forEach { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    label = { Text(p.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent,
                        selectedLabelColor = Color(0xFF062E14),
                        labelColor = TextMain,
                    ),
                )
            }
        }
        Text(selected.description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Save copies to gallery", color = TextMain, fontSize = 15.sp)
                Text("In the \"Status Compressor\" album", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = saveToGallery,
                onCheckedChange = onSaveToGallery,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
            )
        }
    }
}

@Composable
private fun ItemCard(item: Item, onCancel: () -> Unit) {
    Section {
        Row {
            Thumb(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.preset.label, item.detail).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = Muted,
                    fontSize = 12.sp,
                )
                Text(
                    item.status,
                    color = when {
                        item.failed -> Danger
                        item.done -> Accent
                        else -> Muted
                    },
                    fontSize = 13.sp,
                )
                item.progress?.let { p ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Panel2,
                    )
                }
                if (item.busy) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("Cancel", color = Muted) }
                }
            }
        }
        if (item.done) {
            Spacer(Modifier.height(12.dp))
            ItemSavings(item)
            if (item.outputs.size > 1) {
                Spacer(Modifier.height(8.dp))
                item.outputs.forEach { o ->
                    Text("${o.label} · ${formatBytes(o.size)}", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            ShareRow(item)
        }
    }
}

@Composable
private fun Thumb(item: Item) {
    Box(
        Modifier
            .size(width = 64.dp, height = 84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Panel2),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = item.thumb
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (item.kind == Kind.VIDEO) {
            Text("▶", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ItemSavings(item: Item) {
    val before = item.originalSize
    val after = item.outputSize
    val saved = before - after
    val ratio = after.toFloat() / before.coerceAtLeast(1)
    val pct = ((1 - ratio) * 100).roundToInt()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel2)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatBytes(before)} → ${formatBytes(after)}",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (pct >= 0) "$pct% smaller" else "${-pct}% larger",
                    color = if (pct >= 0) Accent else Muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            SizeBar(ratio)
            Spacer(Modifier.height(6.dp))
            Text(
                if (saved > 0) "Saved ${formatBytes(saved)} of storage and upload data"
                else "Already lean, so it's sized for sharpness rather than savings",
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareRow(item: Item) {
    val context = LocalContext.current
    val installed = remember { ShareTarget.entries.associateWith { Sharing.installedPackage(context, it) } }
    val uris = item.outputs.map { it.uri }

    Text("Send to", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShareTarget.entries.forEach { target ->
            val pkg = installed[target] ?: return@forEach
            ShareButton(
                label = if (target == ShareTarget.WHATSAPP_STATUS || target == ShareTarget.WHATSAPP_CHAT) {
                    "WhatsApp\n${target.label}"
                } else {
                    target.label
                },
                pkg = pkg,
                statusRing = target == ShareTarget.WHATSAPP_STATUS,
                onClick = { Sharing.share(context, uris, item.shareMime, target) },
            )
        }
        ShareButton(label = "More", pkg = null, statusRing = false) {
            Sharing.share(context, uris, item.shareMime, null)
        }
    }
}

@Composable
private fun ShareButton(label: String, pkg: String?, statusRing: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(pkg) { pkg?.let { Sharing.appIcon(context, it) } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .width(60.dp),
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            if (statusRing) {
                // WhatsApp's dashed green Status ring around the app icon.
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 2.5.dp.toPx()
                    drawCircle(
                        color = Accent,
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 7f)),
                        ),
                    )
                }
            }
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape))
            } else {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Panel2),
                    contentAlignment = Alignment.Center,
                ) { Text("•••", color = TextMain, fontWeight = FontWeight.Bold) }
            }
        }
        Text(label, color = TextMain, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}
