package com.ryuzen.chat

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/** Local, privacy-preserving attachment guard.
 *  It does not claim to replace a malware engine; it blocks dangerous containers,
 *  detects malformed archives and produces a stable SHA-256 for server-side quarantine.
 */
object SecurityGuard {
    private const val MAX_ATTACHMENT_BYTES = 100L * 1024L * 1024L
    private val blockedExtensions = setOf(
        "exe", "msi", "bat", "cmd", "com", "scr", "pif", "vbs", "vbe", "js", "jse",
        "wsf", "wsh", "ps1", "psm1", "jar", "hta", "dll", "so", "dylib"
    )

    fun inspectAttachment(context: Context, uri: Uri): AttachmentSecurityResult {
        return try {
            val name = queryName(context, uri) ?: "Tanlangan fayl"
            val size = querySize(context, uri)
            if (size != null && size > MAX_ATTACHMENT_BYTES) {
                return AttachmentSecurityResult(ScanVerdict.SUSPICIOUS, name, null, listOf("FILE_TOO_LARGE"), "Fayl hajmi 100 MB limitidan oshdi")
            }
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext in blockedExtensions) {
                return AttachmentSecurityResult(ScanVerdict.MALICIOUS, name, null, listOf("EXECUTABLE_BLOCKED"), "Ijro etiladigan fayl turi Veyra orqali yuborilmaydi")
            }
            val local = copyToCache(context, uri)
            val sha = sha256(local)
            if (ext == "apk") {
                val apk = inspectApk(local, name, sha)
                local.delete()
                return apk
            }
            local.delete()
            AttachmentSecurityResult(ScanVerdict.SAFE, name, sha, emptyList(), "Qurilmadagi boshlang'ich xavfsizlik tekshiruvidan o'tdi")
        } catch (e: Exception) {
            AttachmentSecurityResult(ScanVerdict.UNKNOWN, queryName(context, uri) ?: "Tanlangan fayl", null, listOf("SCAN_ERROR"), "Faylni tekshirishning imkoni bo'lmadi")
        }
    }

    fun inspectUrl(raw: String): LinkScanResult {
        val value = raw.trim()
        return try {
            val uri = Uri.parse(value)
            val scheme = uri.scheme?.lowercase(Locale.US)
            if (scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
                return LinkScanResult(value, ScanVerdict.MALICIOUS, ThreatType.PHISHING, System.currentTimeMillis(), "Noma'lum yoki xavfli URL sxemasi")
            }
            if (!uri.userInfo.isNullOrBlank()) {
                return LinkScanResult(value, ScanVerdict.SUSPICIOUS, ThreatType.SCAM_LINK, System.currentTimeMillis(), "URL ichida yashirin login ma'lumoti bor")
            }
            val host = uri.host!!.lowercase(Locale.US)
            val suspicious = host.startsWith("xn--") || host.contains("localhost") || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1"
            if (suspicious) {
                return LinkScanResult(value, ScanVerdict.SUSPICIOUS, ThreatType.SUSPICIOUS_REDIRECT, System.currentTimeMillis(), "Domen yoki manzil shubhali ko'rinadi")
            }
            LinkScanResult(value, ScanVerdict.UNKNOWN, ThreatType.NONE, System.currentTimeMillis(), "Mahalliy tekshiruv o'tdi; reputatsiya bazasi tekshiruvi hali ulanmagan")
        } catch (_: Exception) {
            LinkScanResult(value, ScanVerdict.MALICIOUS, ThreatType.PHISHING, System.currentTimeMillis(), "URL formati noto'g'ri")
        }
    }

    private fun inspectApk(file: File, name: String, sha: String): AttachmentSecurityResult {
        return try {
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry("AndroidManifest.xml")
                if (manifest == null) {
                    AttachmentSecurityResult(ScanVerdict.MALICIOUS, name, sha, listOf("INVALID_APK"), "APK manifest topilmadi")
                } else {
                    var uncompressed = 0L
                    var entries = 0
                    zip.entries().asSequence().forEach { entry ->
                        entries++
                        if (entries > 20000) throw SecurityException("ARCHIVE_TOO_MANY_ENTRIES")
                        if (entry.size > 0) uncompressed += entry.size
                        if (uncompressed > 500L * 1024L * 1024L) throw SecurityException("ARCHIVE_EXPANSION_LIMIT")
                    }
                    AttachmentSecurityResult(ScanVerdict.UNKNOWN, name, sha, listOf("APK_REQUIRES_SERVER_SANDBOX"), "APK aniqlanadi, lekin haqiqiy malware tahlili server sandboxida bajarilishi kerak")
                }
            }
        } catch (e: SecurityException) {
            AttachmentSecurityResult(ScanVerdict.MALICIOUS, name, sha, listOf(e.message ?: "ARCHIVE_SECURITY_LIMIT"), "Arxiv tuzilmasi xavfli deb topildi")
        } catch (_: Exception) {
            AttachmentSecurityResult(ScanVerdict.MALICIOUS, name, sha, listOf("INVALID_ARCHIVE"), "APK/arxivni ochib tekshirishda xatolik")
        }
    }

    private fun queryName(context: Context, uri: Uri): String? = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

    private fun querySize(context: Context, uri: Uri): Long? = context.contentResolver.query(uri, arrayOf("_size"), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val out = File.createTempFile("veyra_scan_", ".bin", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(out).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_ATTACHMENT_BYTES) throw SecurityException("FILE_TOO_LARGE")
                    output.write(buffer, 0, n)
                }
            }
        }
        if (out.length() > MAX_ATTACHMENT_BYTES) {
            out.delete()
            throw SecurityException("FILE_TOO_LARGE")
        }
        return out
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class AttachmentSecurityResult(
    val verdict: ScanVerdict,
    val fileName: String,
    val sha256: String?,
    val threats: List<String>,
    val detail: String
)
