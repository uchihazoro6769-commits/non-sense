package com.ryuzen.chat

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.Window
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.GCMParameterSpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

private const val APP_NAME = "Veyra"
private const val PREFS = "veyra_local"
private const val API_BASE_URL = "https://yale-sides-unions-amsterdam.trycloudflare.com"

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { VeyraApp() }
    }

    fun showBiometric(onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
                override fun onAuthenticationFailed() = onFail("Biometrik tasdiqlash amalga oshmadi")
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("$APP_NAME himoyasi")
                    .setSubtitle("Barmoq izi yoki biometrik tasdiqlash")
                    .setNegativeButtonText("Bekor qilish")
                    .build()
            )
        } else onFail("Qurilmada biometrik himoya mavjud emas yoki sozlanmagan")
    }
}

@Composable
private fun VeyraApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var loggedIn by remember { mutableStateOf(prefs.getBoolean("session", false)) }
    var locked by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(loggedIn) {
        val activity = context as? Activity
        if (prefs.getBoolean("pin_enabled", false) || prefs.getBoolean("bio_enabled", false)) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(lifecycleOwner, loggedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && loggedIn && (prefs.getBoolean("pin_enabled", false) || prefs.getBoolean("bio_enabled", false)) && (prefs.getString("pin_hash", null) != null || prefs.getBoolean("bio_enabled", false))) {
                locked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !showTerms && !showProfile && !locked) {
        val now = System.currentTimeMillis()
        if (now - lastBackTime < 2000) (context as Activity).finish() else {
            lastBackTime = now
            android.widget.Toast.makeText(context, "Chiqish uchun yana bir marta bosing", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (showTerms) {
        TermsScreen(onBack = { showTerms = false }, onAccept = { prefs.edit().putBoolean("terms", true).apply(); showTerms = false })
        return
    }
    if (locked) {
        AppLockScreen(prefs, onUnlocked = { locked = false })
        return
    }
    if (!loggedIn) {
        LoginScreen(
            prefs = prefs,
            onLogin = { name ->
                username = name
                prefs.edit().putString("username", name).putBoolean("session", true).apply()
                loggedIn = true
                if (prefs.getBoolean("login_notify", true)) sendLoginNotification(context, name)
            },
            openTerms = { showTerms = true }
        )
    } else if (showProfile) {
        ProfileScreen(prefs, username) { showProfile = false }
    } else {
        MessengerShell(
            prefs = prefs,
            username = username,
            openProfile = { showProfile = true },
            openTerms = { showTerms = true },
            logout = { prefs.edit().putBoolean("session", false).remove("auth_token").apply(); loggedIn = false }
        )
    }
}

private var lastBackTime = 0L

private fun sendLoginNotification(context: Context, username: String) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("login", "$APP_NAME login", NotificationManager.IMPORTANCE_DEFAULT))
    if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(context as ComponentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 91)
        return
    }
    nm.notify(91, NotificationCompat.Builder(context, "login")
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentTitle("Yangi login")
        .setContentText("$username akkaunti $APP_NAME ilovasiga kirdi")
        .setAutoCancel(true)
        .build())
}

@Composable
private fun LoginScreen(
    prefs: android.content.SharedPreferences,
    onLogin: (String) -> Unit,
    openTerms: () -> Unit
) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(prefs.getBoolean("terms", false)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun submit() {
        error = ""
        val cleanEmail = email.trim().lowercase()
        if (register) {
            if (!AuthRules.validUsername(username)) {
                error = "Veyra ID 3–32 belgidan iborat bo‘lsin: harf, raqam va _"
                return
            }
            if (!AuthRules.validEmail(cleanEmail)) {
                error = "Email @veyra.com bilan tugashi kerak"
                return
            }
            if (password.length < 10) {
                error = "Parol kamida 10 belgidan iborat bo‘lsin"
                return
            }
            if (password != confirmPassword) {
                error = "Parollar mos kelmayapti"
                return
            }
            if (!accepted) {
                error = "Avval Terms of Use ni qabul qiling"
                return
            }
        } else {
            if (!AuthRules.validEmail(cleanEmail)) {
                error = "Veyra email manzilini kiriting: username@veyra.com"
                return
            }
            if (password.isBlank()) {
                error = "Parolni kiriting"
                return
            }
        }

        loading = true
        scope.launch {
            val result = if (register) {
                EmailAuthApi.register(API_BASE_URL, username.trim(), cleanEmail, password)
            } else {
                EmailAuthApi.login(API_BASE_URL, cleanEmail, password)
            }
            loading = false
            if (result.ok) {
                prefs.edit()
                    .putBoolean("session", true)
                    .putString("auth_token", result.token)
                    .putString("username", result.username.ifBlank { cleanEmail.substringBefore("@") })
                    .putString("email", result.email.ifBlank { cleanEmail })
                    .apply()
                onLogin(result.username.ifBlank { cleanEmail.substringBefore("@") })
            } else {
                error = result.message
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().imePadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                Modifier.size(88.dp),
                CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(APP_NAME, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                if (register) "Yangi Veyra akkaunti" else "Veyra akkauntingizga kiring",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(26.dp))

            if (register) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(32)
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Veyra ID") },
                    placeholder = { Text("ryuzen") },
                    singleLine = true,
                    enabled = !loading
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(120); error = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Veyra email") },
                placeholder = { Text("username@veyra.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !loading
            )
            if (register) {
                Text(
                    "Faqat @veyra.com manzillari",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128); error = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Parol") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !loading
            )

            if (register) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it.take(128); error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parolni qayta kiriting") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !loading
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    TextButton(onClick = openTerms) { Text("Terms of Use") }
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                Text(if (loading) "Kutilmoqda…" else if (register) "Akkaunt yaratish" else "Kirish")
            }

            TextButton(
                onClick = {
                    register = !register
                    error = ""
                    password = ""
                    confirmPassword = ""
                },
                enabled = !loading
            ) {
                Text(if (register) "Menda akkaunt bor — Kirish" else "Yangi akkaunt yaratish")
            }
        }
    }
}

private object AuthRules {
    private val usernameRe = Regex("^[A-Za-z0-9_]{3,32}$")
    private val emailRe = Regex("^[a-z0-9][a-z0-9._-]{2,31}@veyra\\.com$")

    fun validUsername(value: String): Boolean = usernameRe.matches(value)
    fun validEmail(value: String): Boolean = emailRe.matches(value)
}

private data class AuthResult(
    val ok: Boolean,
    val message: String,
    val token: String = "",
    val username: String = "",
    val email: String = ""
)

private object EmailAuthApi {
    suspend fun register(baseUrl: String, username: String, email: String, password: String): AuthResult =
        request(baseUrl, "/v1/auth/register",
            """{"username":"${escape(username)}","email":"${escape(email)}","password":"${escape(password)}"}""")

    suspend fun login(baseUrl: String, email: String, password: String): AuthResult =
        request(baseUrl, "/v1/auth/login",
            """{"email":"${escape(email)}","password":"${escape(password)}"}""")

    private suspend fun request(baseUrl: String, path: String, body: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL(baseUrl.trimEnd('/') + path)
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (status in 200..299) {
                    AuthResult(
                        true,
                        "OK",
                        json?.optString("token").orEmpty(),
                        json?.optString("username").orEmpty(),
                        json?.optString("email").orEmpty()
                    )
                } else {
                    AuthResult(false, json?.optString("error").takeUnless { it.isNullOrBlank() } ?: "Server xatosi")
                }
            } catch (_: Exception) {
                AuthResult(false, "Veyra serveriga ulanib bo‘lmadi.")
            }
        }

    private fun escape(s: String) =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsScreen(onBack: () -> Unit, onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    val sections = listOf(
        "1. Umumiy qoidalar" to "Veyra foydalanuvchilar o‘rtasida xabarlar, rasmlar, fayllar va boshqa raqamli ma’lumotlarni almashish uchun mo‘ljallangan aloqa xizmatidir. Xizmatdan foydalanish orqali siz ushbu shartlarni o‘qiganingizni va ularga rioya qilishga roziligingizni bildirasiz.",
        "2. Akkaunt yaratish" to "Akkaunt ochishda taqdim etilgan ma’lumotlar imkon qadar to‘g‘ri bo‘lishi kerak. Username boshqa foydalanuvchini aldash yoki taqlid qilish maqsadida tanlanmasligi kerak.",
        "3. Parol va xavfsizlik" to "Parolni maxfiy saqlash foydalanuvchining zimmasida. Veyra PIN, biometrik ochish, ikki bosqichli tasdiqlash va login bildirishnomalari kabi mahalliy xavfsizlik vositalarini taqdim etadi.",
        "4. Taqiqlangan faoliyat" to "Spam, phishing, firibgarlik, zararli dastur tarqatish, akkaunt o‘g‘irlash, tahdid, ta’qib, noqonuniy kontent tarqatish va xavfsizlik mexanizmlarini chetlab o‘tish taqiqlanadi.",
        "5. Foydalanuvchi kontenti" to "Siz yuborgan xabar, rasm, video, hujjat yoki boshqa kontent uchun o‘zingiz javobgarsiz. Uchinchi shaxslarning mualliflik huquqi va maxfiyligini hurmat qiling.",
        "6. Maxfiylik" to "Mahalliy xavfsizlik funksiyalari qurilmadagi ma’lumotlarni himoyalashga yordam beradi. Serverga ulangan versiyada autentifikatsiya va xabar uzatish uchun alohida server xavfsizligi talab qilinadi.",
        "7. PIN va biometrik himoya" to "PIN ilovani ochishni cheklaydi. Biometrik himoya Android BiometricPrompt orqali bajariladi va biometrik ma’lumot Veyra serveriga yuborilmaydi.",
        "8. Ikki bosqichli tasdiqlash" to "2FA yoqilganda paroldan tashqari vaqtga bog‘langan tasdiqlash kodi talab qilinadi. Kodni boshqa shaxslarga bermang.",
        "9. Login bildirishnomalari" to "Login bildirishnomalari yangi kirish haqida Android qurilmada ogohlantirish beradi va Android notification ruxsatiga bog‘liq.",
        "10. Guruhlar va kanallar" to "Guruh va kanallarda administratorlar qo‘shimcha qoidalar belgilashi mumkin. Foydalanuvchilar moderator ko‘rsatmalariga rioya qilishlari kerak.",
        "11. Xizmatning mavjudligi" to "Veyra doimiy ravishda ishlashi kafolatlanmaydi. Texnik xizmat, yangilanish yoki internet uzilishi sababli ayrim funksiyalar vaqtincha ishlamasligi mumkin.",
        "12. Yangilanishlar" to "Ilova funksiyalari vaqt o‘tishi bilan o‘zgarishi, yangi xavfsizlik choralariga ega bo‘lishi yoki eski funksiyalar almashtirilishi mumkin.",
        "13. Akkauntni cheklash" to "Xavfsizlikka yoki boshqa foydalanuvchilarga jiddiy xavf tug‘diruvchi faoliyat aniqlansa, server versiyasida akkaunt cheklanishi mumkin.",
        "14. Mas’uliyat" to "Foydalanuvchi xizmatdan o‘z xavfi ostida foydalanadi. Qurilma yo‘qolishi yoki maxfiy ma’lumot oshkor qilinishi oqibatlari uchun foydalanuvchi ehtiyot choralarini ko‘rishi kerak.",
        "15. Shartlarni qabul qilish" to "Qabul qilish katagini belgilash orqali ushbu shartlarni o‘qiganingizni, tushunganingizni va ularga rioya qilishga roziligingizni tasdiqlaysiz."
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("Terms of Use") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) },
        bottomBar = { Surface(tonalElevation = 4.dp) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { checked = it }); Text("Men shartlarni o‘qidim va qabul qilaman.", Modifier.weight(1f)); Button(onClick = onAccept, enabled = checked) { Text("Qabul") } } } }
    ) { p ->
        LazyColumn(Modifier.padding(p).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("$APP_NAME Terms of Use", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Oxirgi yangilanish: 14-sentabr 2026") }
            items(sections) { (title, body) -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerShell(prefs: android.content.SharedPreferences, username: String, openProfile: () -> Unit, openTerms: () -> Unit, logout: () -> Unit) {
    var page by remember { mutableStateOf("chats") }
    var selected by remember { mutableStateOf<Conversation?>(null) }
    var searchPage by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var newChatDialog by remember { mutableStateOf(false) }
    var newGroupDialog by remember { mutableStateOf(false) }
    var newChannelDialog by remember { mutableStateOf(false) }
    var contactsPage by remember { mutableStateOf(false) }
    var savedPage by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var chats by remember { mutableStateOf(loadConversations(prefs)) }
    var archived by remember { mutableStateOf(loadArchived(prefs)) }
    var deletedForUndo by remember { mutableStateOf<Conversation?>(null) }

    fun persist() { saveConversations(prefs, chats); saveArchived(prefs, archived) }
    fun openConversation(c: Conversation) {
        if (chats.none { it.id == c.id }) { chats = listOf(c) + chats; persist() }
        selected = c; searchPage = false; contactsPage = false; savedPage = false
    }
    fun createChat(title: String, type: ConversationType, members: Int = 0, subscribers: Int = 0, public: Boolean = false): Conversation {
        return Conversation(id = "${type.name.lowercase()}_${System.currentTimeMillis()}", title = title, type = type, membersCount = members, subscribersCount = subscribers, isPublic = public, onlyAdminsCanPost = type == ConversationType.CHANNEL, lastMessage = when (type) { ConversationType.GROUP -> "Guruh yaratildi"; ConversationType.CHANNEL -> "Kanal yaratildi"; else -> "Yangi suhbat" }, lastMessageTime = System.currentTimeMillis())
    }

    BackHandler(enabled = selected != null || searchPage || contactsPage || savedPage || page != "chats" || drawerState.isOpen) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selected != null -> selected = null
            searchPage -> searchPage = false
            contactsPage -> contactsPage = false
            savedPage -> savedPage = false
            page != "chats" -> page = "chats"
        }
    }

    if (searchPage) { SearchPage(chats, onBack = { searchPage = false }, onOpen = ::openConversation); return }
    if (contactsPage) { ContactsPage(chats, onBack = { contactsPage = false }, onOpen = ::openConversation); return }
    if (savedPage) {
        val saved = chats.firstOrNull { it.id == "saved" } ?: Conversation("saved", "Saved Messages", ConversationType.PRIVATE, lastMessage = "Shaxsiy saqlangan xabarlar", lastMessageTime = System.currentTimeMillis())
        SavedMessagesPage(saved, onBack = { savedPage = false }); return
    }
    if (selected != null) { ChatPage(selected!!, onBack = { selected = null }); return }
    if (page == "archive") { ArchivePage(archived, onBack = { page = "chats" }, onOpen = ::openConversation, onRestore = { c -> archived = archived.filterNot { it.id == c.id }; chats = listOf(c) + chats; persist() }) ; return }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { openProfile(); scope.launch { drawerState.close() } }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(prefs.getString("profile_image", null), 58)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(prefs.getString("display_name", username) ?: username, fontWeight = FontWeight.Bold); Text("@$username", style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider()
                NavigationDrawerItem(label = { Text("Chats") }, selected = page == "chats", onClick = { page = "chats"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) })
                NavigationDrawerItem(label = { Text("Settings") }, selected = page == "settings", onClick = { page = "settings"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Filled.Settings, null) })
                NavigationDrawerItem(label = { Text("Privacy & Security") }, selected = page == "security", onClick = { page = "security"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Filled.Security, null) })
                NavigationDrawerItem(label = { Text("Contacts") }, selected = false, onClick = { contactsPage = true; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Filled.Contacts, null) })
                NavigationDrawerItem(label = { Text("Saved Messages") }, selected = false, onClick = { savedPage = true; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Filled.Bookmark, null) })
                NavigationDrawerItem(label = { Text("Archive") }, selected = page == "archive", onClick = { page = "archive"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Filled.Archive, null) })
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(label = { Text("Log out") }, selected = false, onClick = { scope.launch { drawerState.close() }; logout() }, icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) })
                Spacer(Modifier.height(10.dp))
            }
        }
    ) {
        when (page) {
            "security" -> SecurityPage(prefs) { page = "chats" }
            "settings" -> SettingsPage(prefs, username, openProfile = openProfile, openSecurity = { page = "security" }, openTerms = openTerms) { page = "chats" }
            else -> ChatListPage(
                chats = chats,
                menu = { scope.launch { drawerState.open() } },
                openSearch = { searchPage = true },
                onOpen = ::openConversation,
                onMore = { menu = true },
                onArchive = { c -> chats = chats.filterNot { it.id == c.id }; archived = listOf(c) + archived; persist(); android.widget.Toast.makeText(context, "Archived", android.widget.Toast.LENGTH_SHORT).show() },
                onDelete = { c -> chats = chats.filterNot { it.id == c.id }; persist(); deletedForUndo = c },
                onNewChat = { newChatDialog = true },
                onNewGroup = { newGroupDialog = true },
                onNewChannel = { newChannelDialog = true },
                onSaved = { savedPage = true },
                onContacts = { contactsPage = true },
                menuOpen = menu,
                closeMenu = { menu = false }
            )
        }
    }

    if (deletedForUndo != null) AlertDialog(onDismissRequest = { deletedForUndo = null }, title = { Text("Chat deleted") }, text = { Text("The chat was removed from your chat list.") }, confirmButton = { TextButton({ val c = deletedForUndo!!; chats = listOf(c) + chats; persist(); deletedForUndo = null }) { Text("Undo") } }, dismissButton = { TextButton({ deletedForUndo = null }) { Text("Close") } })

    if (newChatDialog) NewChatDialog(onDismiss = { newChatDialog = false }) { title -> val c = createChat(title.removePrefix("@").ifBlank { "New chat" }, ConversationType.PRIVATE); chats = listOf(c) + chats; persist(); newChatDialog = false; selected = c }
    if (newGroupDialog) CreateConversationDialog("New Group", "Guruh nomi", "Create group", { newGroupDialog = false }) { title -> val c = createChat(title, ConversationType.GROUP, members = 1); chats = listOf(c) + chats; persist(); newGroupDialog = false; selected = c }
    if (newChannelDialog) CreateConversationDialog("New Channel", "Kanal nomi", "Create channel", { newChannelDialog = false }) { title -> val c = createChat(title, ConversationType.CHANNEL, subscribers = 1, public = true); chats = listOf(c) + chats; persist(); newChannelDialog = false; selected = c }
}

private fun loadConversations(prefs: android.content.SharedPreferences): List<Conversation> {
    val raw = prefs.getString("chat_store", null) ?: return defaultConversations()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Conversation(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    type = ConversationType.valueOf(o.getString("type")),
                    membersCount = o.optInt("members", 0),
                    subscribersCount = o.optInt("subscribers", 0),
                    isPublic = o.optBoolean("public", false),
                    onlyAdminsCanPost = o.optBoolean("admins", false),
                    lastMessage = o.optString("last", ""),
                    lastMessageTime = if (o.has("time") && !o.isNull("time")) o.getLong("time") else null
                ))
            }
        }.filterNot { it.title.equals("Veyra Team", true) || it.title.equals("Veyra Community", true) || it.title.equals("Veyra Communities", true) }.ifEmpty { defaultConversations() }
    } catch (_: Exception) { defaultConversations() }
}

private fun saveConversations(prefs: android.content.SharedPreferences, chats: List<Conversation>) {
    val arr = JSONArray()
    chats.forEach { c ->
        arr.put(JSONObject().apply {
            put("id", c.id); put("title", c.title); put("type", c.type.name); put("members", c.membersCount); put("subscribers", c.subscribersCount); put("public", c.isPublic); put("admins", c.onlyAdminsCanPost); put("last", c.lastMessage ?: "")
            if (c.lastMessageTime != null) put("time", c.lastMessageTime) else put("time", JSONObject.NULL)
        })
    }
    prefs.edit().putString("chat_store", arr.toString()).apply()
}

private fun loadArchived(prefs: android.content.SharedPreferences): List<Conversation> {
    val raw = prefs.getString("archive_store", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(Conversation(o.getString("id"), o.getString("title"), ConversationType.valueOf(o.getString("type")), lastMessage = o.optString("last", ""), lastMessageTime = if (o.has("time") && !o.isNull("time")) o.getLong("time") else null)) } }
    } catch (_: Exception) { emptyList() }
}

private fun saveArchived(prefs: android.content.SharedPreferences, chats: List<Conversation>) {
    val arr = JSONArray(); chats.forEach { c -> arr.put(JSONObject().apply { put("id", c.id); put("title", c.title); put("type", c.type.name); put("last", c.lastMessage ?: ""); if (c.lastMessageTime != null) put("time", c.lastMessageTime) }) }; prefs.edit().putString("archive_store", arr.toString()).apply()
}

private fun defaultConversations() = listOf(
    Conversation("saved", "Saved Messages", ConversationType.PRIVATE)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListPage(
    chats: List<Conversation>, menu: () -> Unit, openSearch: () -> Unit, onOpen: (Conversation) -> Unit,
    onMore: () -> Unit, onArchive: (Conversation) -> Unit, onDelete: (Conversation) -> Unit, onNewChat: () -> Unit, onNewGroup: () -> Unit, onNewChannel: () -> Unit,
    onSaved: () -> Unit, onContacts: () -> Unit, menuOpen: Boolean, closeMenu: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(APP_NAME, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = menu) { Icon(Icons.Filled.Menu, "Menyu") } }, actions = {
                IconButton(onClick = openSearch) { Icon(Icons.Filled.Search, "Qidirish") }
                Box {
                    IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "Ko‘proq") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = closeMenu) {
                        DropdownMenuItem(text = { Text("New chat") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }, onClick = { closeMenu(); onNewChat() })
                        DropdownMenuItem(text = { Text("New group") }, leadingIcon = { Icon(Icons.Filled.Group, null) }, onClick = { closeMenu(); onNewGroup() })
                        DropdownMenuItem(text = { Text("New channel") }, leadingIcon = { Icon(Icons.Filled.Campaign, null) }, onClick = { closeMenu(); onNewChannel() })
                        DropdownMenuItem(text = { Text("Saved Messages") }, leadingIcon = { Icon(Icons.Filled.Bookmark, null) }, onClick = { closeMenu(); onSaved() })
                        DropdownMenuItem(text = { Text("Contacts") }, leadingIcon = { Icon(Icons.Filled.Contacts, null) }, onClick = { closeMenu(); onContacts() })
                    }
                }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = onNewChat) { Icon(Icons.AutoMirrored.Filled.Chat, "Yangi chat") } }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            items(chats, key = { it.id }) { c ->
                var dragX by remember(c.id) { mutableFloatStateOf(0f) }
                Row(Modifier.fillMaxWidth().pointerInput(c.id) { detectHorizontalDragGestures(onDragEnd = { val x = dragX; dragX = 0f; if (x < -140f) onArchive(c) else if (x > 140f) onDelete(c) }, onHorizontalDrag = { _, delta -> dragX += delta }).clickable { if (kotlin.math.abs(dragX) < 20f) onOpen(c) } }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConversationAvatar(c); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(formatTime(c.lastMessageTime), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(c.lastMessage ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (c.unreadCount > 0) { Spacer(Modifier.width(8.dp)); Badge { Text(c.unreadCount.toString()) } }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ConversationAvatar(c: Conversation) {
    Surface(Modifier.size(56.dp), CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(when (c.type) { ConversationType.GROUP -> Icons.Filled.Group; ConversationType.CHANNEL -> Icons.Filled.Campaign; else -> Icons.Filled.Person }, null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivePage(archived: List<Conversation>, onBack: () -> Unit, onOpen: (Conversation) -> Unit, onRestore: (Conversation) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Archive") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            if (archived.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) { Text("Archive is empty", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(archived, key = { it.id }) { c ->
                ListItem(headlineContent = { Text(c.title) }, supportingContent = { Text(c.lastMessage ?: "") }, leadingContent = { ConversationAvatar(c) }, trailingContent = { TextButton({ onRestore(c) }) { Text("Restore") } }, modifier = Modifier.clickable { onOpen(c) })
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPage(chats: List<Conversation>, onBack: () -> Unit, onOpen: (Conversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = chats.filter { query.isBlank() || it.title.contains(query, true) || (it.lastMessage ?: "").contains(query, true) }
    Scaffold(topBar = { TopAppBar(title = { OutlinedTextField(query, { query = it }, placeholder = { Text("Chat yoki xabar qidiring") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            if (query.isBlank()) item { Text("Qidiruv", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp)) }
            items(results, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { ConversationAvatar(c); Spacer(Modifier.width(14.dp)); Column { Text(c.title, fontWeight = FontWeight.SemiBold); Text(c.lastMessage ?: "", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                HorizontalDivider()
            }
            if (query.isNotBlank() && results.isEmpty()) item { Text("Hech narsa topilmadi", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatPage(c: Conversation, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var messages by remember(c.id) { mutableStateOf(emptyList<ChatMessage>()) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var attach by remember { mutableStateOf(false) }
    var chatMenu by remember { mutableStateOf(false) }
    var securityWarning by remember { mutableStateOf<AttachmentSecurityResult?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var linkWarning by remember { mutableStateOf<LinkScanResult?>(null) }
    var info by remember { mutableStateOf(false) }
    var callType by remember { mutableStateOf<CallType?>(null) }
    var requestedCall by remember { mutableStateOf<CallType?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingPath by remember { mutableStateOf<String?>(null) }
    var recordingPermissionRequested by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val videoOk = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val requested = requestedCall
        requestedCall = null
        callType = when (requested) {
            CallType.AUDIO -> if (audioOk) CallType.AUDIO else null
            CallType.VIDEO -> if (audioOk && videoOk) CallType.VIDEO else null
            null -> null
        }
        if (recordingPermissionRequested && audioOk) {
            recordingPermissionRequested = false
            val path = File(context.filesDir, "audio_${System.currentTimeMillis()}.m4a")
            try {
                val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(128000); r.setAudioSamplingRate(44100); r.setOutputFile(path.absolutePath); r.prepare(); r.start()
                recorder = r; recordingPath = path.absolutePath; recordingStartedAt = System.currentTimeMillis(); recording = true
            } catch (_: Exception) { try { recorder?.release() } catch (_: Exception) {}; recorder = null; recording = false; path.delete() }
        } else if (recordingPermissionRequested) {
            recordingPermissionRequested = false
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", imageUrl = uri.toString(), type = MessageType.IMAGE, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true) }
    fun stopRecording() {
        val r = recorder ?: return
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        recorder = null
        recording = false
        val path = recordingPath
        if (path != null) {
            val duration = System.currentTimeMillis() - recordingStartedAt
            if (duration >= 500L) messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", audioUrl = path, audioDurationMs = duration, type = MessageType.AUDIO, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true)
            else File(path).delete()
        }
        recordingPath = null
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { recordingPermissionRequested = true; permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); return }
        val path = File(context.filesDir, "audio_${System.currentTimeMillis()}.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(128000); r.setAudioSamplingRate(44100); r.setOutputFile(path.absolutePath); r.prepare(); r.start()
            recorder = r; recordingPath = path.absolutePath; recordingStartedAt = System.currentTimeMillis(); recording = true
        } catch (_: Exception) { try { recorder?.release() } catch (_: Exception) {}; recorder = null; recording = false; path.delete() }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val result = SecurityGuard.inspectAttachment(context, uri)
            if (result.verdict == ScanVerdict.SAFE) {
                messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", fileUrl = uri.toString(), fileName = result.fileName, type = MessageType.FILE, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true)
            } else {
                pendingUri = uri
                securityWarning = result
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Column { Text(c.title, fontWeight = FontWeight.SemiBold); Text(if (c.type == ConversationType.PRIVATE) "online" else if (c.type == ConversationType.GROUP) "${c.membersCount} members" else "${c.subscribersCount} subscribers", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }, actions = {
            if (c.type != ConversationType.CHANNEL) {
                IconButton(onClick = { requestedCall = CallType.AUDIO; permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }) { Icon(Icons.Filled.Call, "Audio qo‘ng‘iroq") }
                IconButton(onClick = { requestedCall = CallType.VIDEO; permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) }) { Icon(Icons.Filled.Videocam, "Video qo‘ng‘iroq") }
            }
            Box {
                IconButton(onClick = { chatMenu = true }) { Icon(Icons.Filled.MoreVert, "Chat amallari") }
                DropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                    DropdownMenuItem(text = { Text("Rasm yuborish") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { chatMenu = false; imagePicker.launch("image/*") })
                    DropdownMenuItem(text = { Text("Fayl yuborish") }, leadingIcon = { Icon(Icons.Filled.AttachFile, null) }, onClick = { chatMenu = false; filePicker.launch("*/*") })
                    DropdownMenuItem(text = { Text("Chat haqida") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { chatMenu = false; info = true })
                    DropdownMenuItem(text = { Text("Xabarlarni tozalash") }, leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) }, onClick = { chatMenu = false; messages = emptyList() })
                }
            }
        }) },
        bottomBar = {
            Column {
                if (attach) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { attach = false; imagePicker.launch("image/*") }, label = { Text("Rasm") }, leadingIcon = { Icon(Icons.Filled.Image, null) })
                    AssistChip(onClick = { attach = false; filePicker.launch("*/*") }, label = { Text("Fayl") }, leadingIcon = { Icon(Icons.Filled.AttachFile, null) })
                }
                Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { attach = !attach }) { Icon(Icons.Filled.Add, "Qo‘shish") }
                        OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Xabar yozing…") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
                        if (input.isBlank()) {
                            IconButton(onClick = { if (recording) stopRecording() else startRecording() }) { Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, if (recording) "To‘xtatish" else "Audio xabar") }
                        }
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value
                                if (url != null) {
                                    val result = SecurityGuard.inspectUrl(url)
                                    if (result.verdict != ScanVerdict.SAFE) linkWarning = result
                                    else { messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", text = text, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true); input = "" }
                                } else {
                                    messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", text = text, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true); input = ""
                                }
                            }
                        }, enabled = input.isNotBlank()) { Icon(Icons.Filled.Send, "Yuborish") }
                    }
                }
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(messages, key = { it.id }) { m -> MessageBubbleV7(m) { if (m.isMine && m.type == MessageType.TEXT) editing = m } }
            if (messages.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) { Text("Hozircha xabar yo‘q", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
    securityWarning?.let { result ->
        AlertDialog(
            onDismissRequest = { securityWarning = null; pendingUri = null },
            title = { Text("Veyra Security Guard") },
            text = { Column { Text(result.detail); if (result.threats.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Belgi: ${result.threats.joinToString(", ")}") }; result.sha256?.let { sha -> Spacer(Modifier.height(8.dp)); Text("SHA-256: ${sha.take(16)}…", style = MaterialTheme.typography.labelSmall) } } },
            confirmButton = { TextButton(onClick = { securityWarning = null; pendingUri = null }) { Text("Bloklash") } },
            dismissButton = { if (result.verdict == ScanVerdict.UNKNOWN) TextButton(onClick = { val uri = pendingUri; if (uri != null) messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", fileUrl = uri.toString(), fileName = result.fileName, type = MessageType.FILE, timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true); securityWarning = null; pendingUri = null }) { Text("Baribir yuborish") } }
        )
    }
    linkWarning?.let { result ->
        AlertDialog(
            onDismissRequest = { linkWarning = null },
            title = { Text("Havola tekshiruvi") },
            text = { Column { Text(result.detailMessage ?: "Bu havola tekshiruvdan o'tmadi."); Spacer(Modifier.height(8.dp)); Text(result.url, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { linkWarning = null }) { Text("Bekor qilish") } },
            dismissButton = { TextButton(onClick = { messages = messages + ChatMessage(System.currentTimeMillis().toString(), "me", text = input.trim(), timestamp = System.currentTimeMillis(), status = MessageStatus.DELIVERED, isMine = true); input = ""; linkWarning = null }) { Text("Baribir yuborish") } }
        )
    }

    if (editing != null) EditMessageDialog(editing!!, { editing = null }, { text -> messages = messages.map { if (it.id == editing!!.id) it.copy(text = text, isEdited = true) else it }; editing = null }, { messages = messages.filterNot { it.id == editing!!.id }; editing = null })
    if (callType != null) CallScreenDialog(c, callType!!, onEnd = { callType = null })
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text(c.title) }, text = { Text("Tur: ${c.type}\nA’zolar: ${if (c.type == ConversationType.CHANNEL) c.subscribersCount else c.membersCount}\n\nBu versiyada chat ma’lumotlari qurilmaning lokal demo sessiyasida ishlaydi.") }, confirmButton = { TextButton({ info = false }) { Text("Yopish") } })
}

private enum class CallType { AUDIO, VIDEO }

@Composable
private fun CallScreenDialog(c: Conversation, type: CallType, onEnd: () -> Unit) {
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(type == CallType.VIDEO) }
    AlertDialog(
        onDismissRequest = onEnd,
        title = { Text(if (type == CallType.VIDEO) "Video qo‘ng‘iroq" else "Audio qo‘ng‘iroq") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(Modifier.size(100.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(if (type == CallType.VIDEO) Icons.Filled.Videocam else Icons.Filled.Call, null, Modifier.size(44.dp)) } }
                Spacer(Modifier.height(12.dp))
                Text(c.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Qo‘ng‘iroq oynasi tayyor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { muted = !muted }) { Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mikrofon") }
                    FilledTonalIconButton(onClick = { speaker = !speaker }) { Icon(if (speaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Dinamik") }
                    if (type == CallType.VIDEO) FilledTonalIconButton(onClick = { camera = !camera }) { Icon(if (camera) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Kamera") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Haqiqiy foydalanuvchilar o‘rtasidagi audio/video aloqa uchun Go signaling + WebRTC serveri ulanadi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onEnd) { Icon(Icons.Filled.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("Qo‘ng‘iroqni tugatish") } }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleV7(message: ChatMessage, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val shape = if (message.isMine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    var player by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(message.id) { mutableStateOf(false) }
    DisposableEffect(message.id) { onDispose { try { player?.release() } catch (_: Exception) {}; player = null } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Surface(shape = shape, color = if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 310.dp).combinedClickable(onClick = {
            if (message.type == MessageType.AUDIO && message.audioUrl != null) {
                try {
                    if (playing) { player?.pause(); playing = false } else {
                        if (player == null) { player = MediaPlayer().apply { setDataSource(message.audioUrl); prepare(); setOnCompletionListener { playing = false } } }
                        player?.start(); playing = true
                    }
                } catch (_: Exception) { playing = false }
            }
        }, onLongClick = onLongPress)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                when (message.type) {
                    MessageType.IMAGE -> AsyncImage(message.imageUrl, "Rasm", Modifier.sizeIn(maxWidth = 260.dp, maxHeight = 300.dp).clip(RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    MessageType.FILE -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.InsertDriveFile, null); Spacer(Modifier.width(8.dp)); Text(message.fileName ?: "Fayl", fontWeight = FontWeight.Medium) }
                    MessageType.AUDIO -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play"); Spacer(Modifier.width(8.dp)); Text("Audio · ${message.audioDurationMs / 1000}s", fontWeight = FontWeight.Medium) }
                    MessageType.TEXT -> Text(message.text ?: "", style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isMine) { Spacer(Modifier.width(4.dp)); Text(if (message.status == MessageStatus.READ) "Read" else "Delivered", style = MaterialTheme.typography.labelSmall) }
                    if (message.isEdited) { Spacer(Modifier.width(5.dp)); Text("edited", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun EditMessageDialog(message: ChatMessage, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(message.id) { mutableStateOf(message.text ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Xabarni tahrirlash") }, text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2) }, confirmButton = { TextButton({ if (text.isNotBlank()) onSave(text.trim()) }) { Text("Saqlash") } }, dismissButton = { Row { TextButton(onDelete) { Text("O‘chirish") }; TextButton(onDismiss) { Text("Bekor") } } })
}

@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) = CreateConversationDialog("New chat", "Username yoki ism", "Open chat", onDismiss, onCreate)

@Composable
private fun CreateConversationDialog(title: String, hint: String, confirm: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(hint) }, singleLine = true) }, confirmButton = { TextButton({ if (value.isNotBlank()) onCreate(value.trim()) }, enabled = value.isNotBlank()) { Text(confirm) } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsPage(chats: List<Conversation>, onBack: () -> Unit, onOpen: (Conversation) -> Unit) {
    var add by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf(listOf("Veyra Support", "Alex")) }
    Scaffold(topBar = { TopAppBar(title = { Text("Contacts") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }, actions = { IconButton({ add = true }) { Icon(Icons.Filled.PersonAdd, "Kontakt qo‘shish") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            items(contacts) { name ->
                val existing = chats.firstOrNull { it.title.equals(name, true) }
                ListItem(headlineContent = { Text(name, fontWeight = FontWeight.Medium) }, supportingContent = { Text("Suhbatni ochish uchun bosing") }, leadingContent = { Surface(Modifier.size(48.dp), CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null) } } }, modifier = Modifier.clickable { onOpen(existing ?: Conversation("contact_${name}", name, ConversationType.PRIVATE, lastMessage = "Kontakt")) })
                HorizontalDivider()
            }
        }
    }
    if (add) CreateConversationDialog("Kontakt qo‘shish", "Ism", "Qo‘shish", { add = false }) { name -> contacts = (contacts + name).distinct(); add = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMessagesPage(saved: Conversation, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(emptyList<String>()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Saved Messages") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }, bottomBar = { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Saqlash…") }); Spacer(Modifier.width(6.dp)); FilledIconButton(onClick = { if (text.isNotBlank()) { notes = notes + text.trim(); text = "" } }, enabled = text.isNotBlank()) { Icon(Icons.Filled.Send, "Saqlash") } } }) { p -> LazyColumn(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(notes) { note -> Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(note, Modifier.padding(14.dp)) } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(prefs: android.content.SharedPreferences, username: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(prefs.getString("display_name", username) ?: username) }
    var bio by remember { mutableStateOf(prefs.getString("bio", "") ?: "") }
    var image by remember { mutableStateOf(prefs.getString("profile_image", null)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val target = File(context.filesDir, "profile_image")
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            image = target.toURI().toString(); prefs.edit().putString("profile_image", image).apply()
        } catch (_: Exception) { image = uri.toString(); prefs.edit().putString("profile_image", image).apply() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box { ProfileAvatar(image, 110); FloatingActionButton({ picker.launch("image/*") }, Modifier.align(Alignment.BottomEnd).size(42.dp)) { Icon(Icons.Filled.CameraAlt, "Rasm tanlash") } }
            Spacer(Modifier.height(20.dp)); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ism") }); Spacer(Modifier.height(10.dp)); OutlinedTextField("@$username", {}, Modifier.fillMaxWidth(), label = { Text("Username") }, enabled = false); Spacer(Modifier.height(10.dp)); OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3); Spacer(Modifier.height(16.dp)); Button({ prefs.edit().putString("display_name", name).putString("bio", bio).apply(); onBack() }, Modifier.fillMaxWidth()) { Text("Saqlash") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(prefs: android.content.SharedPreferences, username: String, openProfile: () -> Unit, openSecurity: () -> Unit, openTerms: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var languageDialog by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item { ListItem(headlineContent = { Text(prefs.getString("display_name", username) ?: username, fontWeight = FontWeight.Bold) }, supportingContent = { Text("@$username") }, leadingContent = { ProfileAvatar(prefs.getString("profile_image", null), 52) }, modifier = Modifier.clickable(onClick = openProfile) ) }
            item { HorizontalDivider() }
            item { ListItem(headlineContent = { Text("Privacy & Security") }, supportingContent = { Text("PIN, biometrika, 2FA va maxfiylik") }, leadingContent = { Icon(Icons.Filled.Security, null) }, modifier = Modifier.clickable(onClick = openSecurity)) }
            item { ListItem(headlineContent = { Text("Language") }, supportingContent = { Text(languageLabel(prefs.getString("language", "uz") ?: "uz")) }, leadingContent = { Icon(Icons.Filled.Language, null) }, modifier = Modifier.clickable { languageDialog = true }) }
            item { ListItem(headlineContent = { Text("Terms of Use") }, supportingContent = { Text("Xizmat shartlari") }, leadingContent = { Icon(Icons.Filled.Description, null) }, modifier = Modifier.clickable(onClick = openTerms)) }
            item { ListItem(headlineContent = { Text("Test notification") }, supportingContent = { Text("Bildirishnoma ruxsatini tekshirish") }, leadingContent = { Icon(Icons.Filled.Notifications, null) }, modifier = Modifier.clickable { sendLoginNotification(context, username) }) }
        }
    }
    if (languageDialog) AlertDialog(onDismissRequest = { languageDialog = false }, title = { Text("Language") }, text = { Column { listOf("en" to "English", "ru" to "Русский", "uz" to "O‘zbekcha").forEach { (code, label) -> Row(Modifier.fillMaxWidth().clickable { prefs.edit().putString("language", code).apply(); languageDialog = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(prefs.getString("language", "uz") == code, null); Spacer(Modifier.width(8.dp)); Text(label) } } } }, confirmButton = { TextButton({ languageDialog = false }) { Text("Close") } })
}

private fun languageLabel(code: String): String = when (code) { "en" -> "English"; "ru" -> "Русский"; else -> "O‘zbekcha" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityPage(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    var pinEnabled by remember { mutableStateOf(prefs.getBoolean("pin_enabled", false)) }
    var bioEnabled by remember { mutableStateOf(prefs.getBoolean("bio_enabled", false)) }
    var twoFa by remember { mutableStateOf(prefs.getBoolean("2fa", false)) }
    var autoDelete by remember { mutableStateOf(prefs.getBoolean("auto_delete", false)) }
    var loginNotify by remember { mutableStateOf(prefs.getBoolean("login_notify", true)) }
    var privacyDialog by remember { mutableStateOf<String?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var twoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val premium = prefs.getBoolean("is_premium", false)
    val privacyItems = listOf("Last seen & online", "Profile photos", "Forwarded messages", "Calls", "Voice messages", "Messages", "Birthday", "Gifts", "Bio", "Saved Music", "Invites", "Bots and websites")
    Scaffold(topBar = { TopAppBar(title = { Text("Privacy & Security") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga") } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item { Text("Security", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 6.dp), color = MaterialTheme.colorScheme.primary) }
            item { SettingSwitch("Two-Step Verification", "Qo‘shimcha login himoyasi", twoFa) { if (it) twoDialog = true else { twoFa = false; prefs.edit().putBoolean("2fa", false).remove("totp_secret_enc").apply() } } }
            item { SettingSwitch("Auto-Delete Messages", "Yangi xabarlar uchun avtomatik o‘chirish", autoDelete) { autoDelete = it; prefs.edit().putBoolean("auto_delete", it).apply() } }
            item { SettingSwitch("Local passcode", "Ilovani lokal PIN bilan qulflash", pinEnabled) { if (it) pinDialog = true else { pinEnabled = false; prefs.edit().putBoolean("pin_enabled", false).apply() } } }
            item { SettingSwitch("Passkeys", "Qurilma passkey orqali kirish", prefs.getBoolean("passkeys", false)) { prefs.edit().putBoolean("passkeys", it).apply() } }
            item { ListItem(headlineContent = { Text("Login Email") }, supportingContent = { Text(prefs.getString("email", "username@veyra.com") ?: "username@veyra.com") }, leadingContent = { Icon(Icons.Filled.Email, null) }) }
            item { ListItem(headlineContent = { Text("Blocked users") }, supportingContent = { Text(prefs.getInt("blocked_users", 0).toString()) }, leadingContent = { Icon(Icons.Filled.Block, null) }) }
            if (premium) item { ListItem(headlineContent = { Text("Active Sessions") }, supportingContent = { Text("Manage your sessions on all your devices") }, leadingContent = { Icon(Icons.Filled.Devices, null) }, modifier = Modifier.clickable { privacyDialog = "Active Sessions" }) }
            item { HorizontalDivider() }
            item { Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 6.dp), color = MaterialTheme.colorScheme.primary) }
            items(privacyItems) { item -> ListItem(headlineContent = { Text(item) }, supportingContent = { Text(prefs.getString("privacy_${item.lowercase().replace(" ", "_").replace("&", "and")}", "Everybody") ?: "Everybody") }, modifier = Modifier.clickable { privacyDialog = item }) }
            item { HorizontalDivider() }
            item { SettingSwitch("Login notifications", "Yangi login haqida Android bildirishnomasi", loginNotify) { loginNotify = it; prefs.edit().putBoolean("login_notify", it).apply() } }
        }
    }
    if (pinDialog) PinSetupDialog(prefs) { pinEnabled = true; prefs.edit().putBoolean("pin_enabled", true).apply(); pinDialog = false }
    if (twoDialog) TotpSetupDialog(prefs) { twoFa = true; prefs.edit().putBoolean("2fa", true).apply(); twoDialog = false }
    privacyDialog?.let { item ->
        if (item == "Active Sessions") {
            AlertDialog(onDismissRequest = { privacyDialog = null }, title = { Text("Active Sessions") }, text = { Text("Premium session management will show this account’s active devices here.\n\nDevice binding and server-side session revocation are required for production.") }, confirmButton = { TextButton({ privacyDialog = null }) { Text("Close") } })
        } else {
            var value by remember(item) { mutableStateOf(prefs.getString("privacy_${item.lowercase().replace(" ", "_").replace("&", "and")}", "Everybody") ?: "Everybody") }
            val options = listOf("Everybody", "My contacts", "Nobody")
            AlertDialog(onDismissRequest = { privacyDialog = null }, title = { Text(item) }, text = { Column { options.forEach { option -> Row(Modifier.fillMaxWidth().clickable { value = option; prefs.edit().putString("privacy_${item.lowercase().replace(" ", "_").replace("&", "and")}", option).apply(); privacyDialog = null }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(value == option, null); Spacer(Modifier.width(8.dp)); Text(option) } } } }, confirmButton = { TextButton({ privacyDialog = null }) { Text("Close") } })
        }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) { ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub) }, trailingContent = { Switch(checked = value, onCheckedChange = onChange) }) }

@Composable
private fun PinSetupDialog(prefs: android.content.SharedPreferences, done: () -> Unit) {
    var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = {}, title = { Text("PIN o‘rnatish") }, text = { Column { OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("4–6 xonali PIN") }, visualTransformation = PasswordVisualTransformation()); Spacer(Modifier.height(8.dp)); OutlinedTextField(confirm, { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it }, label = { Text("PINni takrorlang") }, visualTransformation = PasswordVisualTransformation()); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton({ if (pin.length in 4..6 && pin == confirm) { val salt = randomSalt(); prefs.edit().putString("pin_salt", salt).putString("pin_hash", pinHash(pin, salt)).apply(); done() } else error = "PIN 4–6 raqam bo‘lishi va mos kelishi kerak" }) { Text("Saqlash") } }, dismissButton = { TextButton({}) { Text("Bekor qilish") } })
}

@Composable
private fun TotpSetupDialog(prefs: android.content.SharedPreferences, done: () -> Unit) {
    var secret by remember { mutableStateOf(decryptSecret(prefs.getString("totp_secret_enc", null) ?: "").ifBlank { base32Secret() }) }
    var code by remember { mutableStateOf("") }
    var nowCounter by remember { mutableLongStateOf(System.currentTimeMillis() / 30000) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); nowCounter = System.currentTimeMillis() / 30000 } }
    val current = totp(secret, nowCounter)
    AlertDialog(onDismissRequest = {}, title = { Text("2FA / TOTP") }, text = { Column { Text("Authenticator uchun maxfiy kalit:"); Text(secret, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Authenticator ilovasiga shu kalitni qo‘shing."); Text("Hozirgi kod: $current", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); OutlinedTextField(code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, label = { Text("6 xonali kod") }) } }, confirmButton = { TextButton({ if (code == current) { prefs.edit().putString("totp_secret_enc", encryptSecret(secret)).apply(); done() } }) { Text("Tasdiqlash") } }, dismissButton = { TextButton({}) { Text("Bekor qilish") } })
}

@Composable
private fun AppLockScreen(prefs: android.content.SharedPreferences, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, Modifier.size(60.dp)); Text("$APP_NAME qulflangan", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(20.dp))
        OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(10.dp)); Button({ val salt = prefs.getString("pin_salt", null); if (salt != null && pinHash(pin, salt) == prefs.getString("pin_hash", null)) { error = ""; onUnlocked() } else error = "PIN noto‘g‘ri" }, enabled = pin.isNotBlank()) { Text("Ochish") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (prefs.getBoolean("bio_enabled", false)) TextButton({ (context as MainActivity).showBiometric({ onUnlocked() }) }) { Text("Biometrik bilan ochish") }
    }
}

@Composable
private fun ProfileAvatar(uri: String?, size: Int) { if (uri != null) AsyncImage(uri, "Profil rasmi", Modifier.size(size.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Surface(Modifier.size(size.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null) } } }

private fun formatTime(time: Long?): String = if (time == null) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))

private fun randomSalt(): String = Base64.encodeToString(Random.nextBytes(16), Base64.NO_WRAP)

private fun pinHash(pin: String, saltB64: String): String {
    val salt = Base64.decode(saltB64, Base64.NO_WRAP)
    val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
    val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private const val KEY_ALIAS = "veyra_local_aes_v1"

private fun getAesKey(): SecretKey {
    val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build())
    return generator.generateKey()
}

private fun encryptSecret(value: String): String { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, getAesKey()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP) }

private fun decryptSecret(encoded: String): String {
    return try {
        if (encoded.isBlank()) return ""
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val data = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getAesKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }
}

private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun base32Secret(): String {
    val bytes = Random.nextBytes(20); val out = StringBuilder(); var buffer = 0; var bits = 0
    for (b in bytes) { buffer = (buffer shl 8) or (b.toInt() and 0xff); bits += 8; while (bits >= 5) { bits -= 5; out.append(B32[(buffer shr bits) and 31]) } }
    if (bits > 0) out.append(B32[(buffer shl (5 - bits)) and 31])
    return out.toString()
}

private fun base32Decode(value: String): ByteArray {
    var buffer = 0; var bits = 0; val out = ArrayList<Byte>()
    for (ch in value.uppercase().filter { it in B32 }) { buffer = (buffer shl 5) or B32.indexOf(ch); bits += 5; if (bits >= 8) { bits -= 8; out.add(((buffer shr bits) and 255).toByte()) } }
    return out.toByteArray()
}

private fun totp(secret: String, counter: Long): String = try {
    val key = base32Decode(secret); val data = ByteBuffer.allocate(8).putLong(counter).array(); val mac = Mac.getInstance("HmacSHA1"); mac.init(SecretKeySpec(key, "HmacSHA1")); val h = mac.doFinal(data); val o = h[h.size - 1].toInt() and 15
    val n = ((h[o].toInt() and 127) shl 24) or ((h[o + 1].toInt() and 255) shl 16) or ((h[o + 2].toInt() and 255) shl 8) or (h[o + 3].toInt() and 255)
    "%06d".format(n % 1000000)
} catch (_: Exception) { "000000" }
