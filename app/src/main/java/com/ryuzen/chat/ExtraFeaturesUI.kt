package com.ryuzen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ryuzen.chat.*

/** Chat sarlavhasida ko'rsatiladigan End-to-End Encryption indikatori */
@Composable
fun E2EEBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Uchidan-uchigacha shifrlangan",
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Shifrlangan", style = MaterialTheme.typography.labelSmall)
    }
}

/** Business account profil kartasi */
@Composable
fun BusinessProfileCard(profile: BusinessProfile, onOpenCatalog: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.companyName, style = MaterialTheme.typography.titleMedium)
                if (profile.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "Tasdiqlangan", modifier = Modifier.size(18.dp))
                }
            }
            profile.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            profile.workingHours?.let {
                Spacer(Modifier.height(4.dp))
                Text("Ish vaqti: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (profile.catalogEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenCatalog) { Text("Katalogni ko'rish") }
            }
        }
    }
}

/** Bot boshqaruv qatori - guruh/sozlamalar ro'yxatida ko'rsatiladi */
@Composable
fun BotAccountRow(bot: BotAccount, onToggleActive: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.SmartToy, contentDescription = "Bot") },
        headlineContent = { Text(bot.name) },
        supportingContent = { Text("@${bot.username}") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = bot.isActive, onCheckedChange = onToggleActive)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Sozlamalar")
                }
            }
        }
    )
}

/** Cloud storage - ishlatilgan joy va fayllar ro'yxati */
@Composable
fun CloudStorageHeader(quota: CloudStorageQuota) {
    val usedGb = quota.usedBytes / (1024.0 * 1024 * 1024)
    val totalGb = quota.totalBytes / (1024.0 * 1024 * 1024)
    val progress = (quota.usedBytes.toFloat() / quota.totalBytes.toFloat()).coerceIn(0f, 1f)

    Column(Modifier.padding(16.dp)) {
        Text("Bulutli xotira", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(
            "%.1f GB / %.1f GB ishlatilgan".format(usedGb, totalGb),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun CloudFileRow(file: CloudFile, onClick: () -> Unit) {
    val icon = when (file.type) {
        CloudFileType.IMAGE -> Icons.Filled.Image
        CloudFileType.VIDEO -> Icons.Filled.Videocam
        CloudFileType.DOCUMENT -> Icons.Filled.InsertDriveFile
        CloudFileType.AUDIO -> Icons.Filled.AudioFile
        CloudFileType.OTHER -> Icons.Filled.InsertDriveFile
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(file.fileName, maxLines = 1) },
        supportingContent = { Text("${file.sizeBytes / 1024} KB") }
    )
}

/** Chat ichida to'lov yuborish/qabul qilish kartasi */
@Composable
fun PaymentCard(payment: Payment, isMine: Boolean) {
    val statusColor = when (payment.status) {
        PaymentStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        PaymentStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        PaymentStatus.FAILED -> MaterialTheme.colorScheme.error
        PaymentStatus.REFUNDED -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.widthIn(max = 240.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Payments, contentDescription = "To'lov")
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isMine) "Siz yubordingiz" else "Siz qabul qildingiz",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${payment.amount} ${payment.currency}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = when (payment.status) {
                    PaymentStatus.COMPLETED -> "Bajarildi"
                    PaymentStatus.PENDING -> "Kutilmoqda"
                    PaymentStatus.FAILED -> "Muvaffaqiyatsiz"
                    PaymentStatus.REFUNDED -> "Qaytarildi"
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
            payment.comment?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Pul yuborish dialogi */
@Composable
fun SendPaymentDialog(onDismiss: () -> Unit, onSend: (Double, String?) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pul yuborish") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Miqdor (UZS)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Izoh (ixtiyoriy)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amountText.toDoubleOrNull()?.let { onSend(it, comment.ifBlank { null }) }
            }) { Text("Yuborish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } }
    )
}

