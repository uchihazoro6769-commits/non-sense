package com.ryuzen.chat

enum class ScanVerdict {
    SAFE, SUSPICIOUS, MALICIOUS, UNKNOWN, SCANNING
}

enum class ThreatType {
    PHISHING, MALWARE, MALICIOUS_APK, SCAM_LINK, SUSPICIOUS_REDIRECT, NONE
}

/**
 * Xabar ichidagi havolani tekshirish natijasi.
 * Backend tomonda VirusTotal/Google Safe Browsing kabi servis bilan tekshiriladi,
 * bu yerda faqat natija modeli.
 */
data class LinkScanResult(
    val url: String,
    val verdict: ScanVerdict,
    val threatType: ThreatType = ThreatType.NONE,
    val scannedAt: Long,
    val detailMessage: String? = null // masalan: "Phishing sahifasiga o'xshaydi"
)

/**
 * Yuborilgan/qabul qilingan .apk fayl uchun skaner natijasi.
 */
data class ApkScanResult(
    val fileName: String,
    val packageName: String? = null,
    val sha256: String,
    val verdict: ScanVerdict,
    val detectedThreats: List<String> = emptyList(), // masalan: ["Trojan.GenericKD", "Adware"]
    val requestedPermissions: List<String> = emptyList(),
    val isFromKnownStore: Boolean = false,
    val scannedAt: Long
)

data class MalwareScanResult(
    val fileName: String,
    val sha256: String,
    val verdict: ScanVerdict,
    val engineResults: Map<String, String> = emptyMap(), // "Antivirus nomi" -> "natija"
    val scannedAt: Long
)
