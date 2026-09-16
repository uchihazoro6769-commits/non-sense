package com.ryuzen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ryuzen.chat.*

/**
 * Chat ichida havola yuborilganda uning skaner natijasini ko'rsatuvchi kichik karta.
 */
@Composable
fun LinkScanCard(result: LinkScanResult) {
    val (bgColor, icon, label) = when (result.verdict) {
        ScanVerdict.SAFE -> Triple(Color(0xFFE6F4EA), Icons.Filled.CheckCircle, "Xavfsiz havola")
        ScanVerdict.SUSPICIOUS -> Triple(Color(0xFFFFF3CD), Icons.Filled.Warning, "Shubhali havola")
        ScanVerdict.MALICIOUS -> Triple(Color(0xFFFDE7E9), Icons.Filled.Dangerous, "Xavfli havola (Phishing/Malware)")
        ScanVerdict.SCANNING -> Triple(Color(0xFFF0F0F0), Icons.Filled.Sync, "Tekshirilmoqda...")
        ScanVerdict.UNKNOWN -> Triple(Color(0xFFF0F0F0), Icons.Filled.HelpOutline, "Noma'lum")
    }

    Surface(color = bgColor, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(result.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                result.detailMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

/**
 * .apk fayl yuborilganda ko'rsatiladigan skaner karta - ruxsatlar va tahdidlar bilan.
 */
@Composable
fun ApkScanCard(result: ApkScanResult) {
    val isDanger = result.verdict == ScanVerdict.MALICIOUS
    val isWarn = result.verdict == ScanVerdict.SUSPICIOUS

    Surface(
        color = when {
            isDanger -> Color(0xFFFDE7E9)
            isWarn -> Color(0xFFFFF3CD)
            else -> Color(0xFFE6F4EA)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = "APK"
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(result.fileName, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when (result.verdict) {
                            ScanVerdict.MALICIOUS -> "⚠️ Zararli dastur aniqlandi!"
                            ScanVerdict.SUSPICIOUS -> "Shubhali fayl"
                            ScanVerdict.SAFE -> "Xavfsiz"
                            else -> "Tekshirilmoqda..."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (result.detectedThreats.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aniqlangan tahdidlar: ${result.detectedThreats.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB00020)
                )
            }
            if (result.requestedPermissions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "So'ralgan ruxsatlar: ${result.requestedPermissions.take(3).joinToString(", ")}" +
                        if (result.requestedPermissions.size > 3) " va yana ${result.requestedPermissions.size - 3}..." else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            if (isDanger) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { /* faylni bloklash/o'chirish */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) {
                    Text("Faylni o'chirish")
                }
            }
        }
    }
}

/**
 * Kirish yoki chat ichida phishing xavfi haqida to'liq ekran ogohlantirish.
 */
@Composable
fun PhishingWarningBanner(url: String, onProceedAnyway: () -> Unit, onGoBack: () -> Unit) {
    Surface(color = Color(0xFFFDE7E9), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dangerous, contentDescription = null, tint = Color(0xFFB00020))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Diqqat! Bu havola phishing (firibgar) sahifa bo'lishi mumkin",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFB00020)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onGoBack, modifier = Modifier.weight(1f)) {
                    Text("Ortga qaytish")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onProceedAnyway, modifier = Modifier.weight(1f)) {
                    Text("Baribir ochish")
                }
            }
        }
    }
}
