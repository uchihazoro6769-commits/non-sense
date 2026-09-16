package com.ryuzen.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ryuzen.chat.ReportReason

/**
 * Foydalanuvchi/xabar/guruhni shikoyat qilish dialogi.
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var comment by remember { mutableStateOf("") }

    val reasons = listOf(
        ReportReason.SPAM to "Spam",
        ReportReason.HARASSMENT to "Ta'qib / haqorat",
        ReportReason.VIOLENCE to "Zo'ravonlik",
        ReportReason.NUDITY to "Nomaqbul kontent",
        ReportReason.FAKE_ACCOUNT to "Soxta akkaunt",
        ReportReason.ILLEGAL_CONTENT to "Noqonuniy kontent",
        ReportReason.OTHER to "Boshqa"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shikoyat qilish") },
        text = {
            Column {
                reasons.forEach { (reason, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
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
            TextButton(
                enabled = selectedReason != null,
                onClick = {
                    selectedReason?.let { onSubmit(it, comment.ifBlank { null }) }
                }
            ) { Text("Yuborish") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Bekor qilish") }
        }
    )
}

/**
 * Anti-spam tomonidan bloklangan/cheklangan foydalanuvchiga ko'rsatiladigan banner.
 * Masalan: "Siz juda tez-tez xabar yubordingiz" yoki "Havola yuborish taqiqlangan".
 */
@Composable
fun AntiSpamWarningBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFFFFF3CD),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Ogohlantirish",
                tint = Color(0xFF8A6D00)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = Color(0xFF8A6D00),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onDismiss) { Text("Yopish") }
        }
    }
}
