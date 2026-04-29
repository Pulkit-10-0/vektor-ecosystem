# VEKTOR — Mobile App PRD
### Emergency Intelligence, On-Device First
**Version 3.0 | April 2026 | Kotlin / Android Native**

---

## 1. What Vektor Is

Vektor is a native Android app written in Kotlin that acts as your emergency identity card,
health companion, and silent guardian. It runs Gemma 4 locally via **Cactus SDK** —
meaning it thinks, analyses, and responds even when there is no internet. When an emergency
happens, it knows who you are, what your body needs, and who to call — before you can say
anything.

The app connects to exactly **one external surface: the VectorGo agent endpoint**. That
endpoint handles all orchestration. The app's job is to be fast, local, and trustworthy.

---

## 2. Core Architecture

```
VEKTOR (Kotlin / Jetpack Compose / MVVM + Clean Architecture)
│
├── Data Layer
│   ├── EncryptedDataStore     → UID, session token (local only)
│   ├── Room DB                → offline emergency queue, vitals log, chat history
│   ├── EncryptedSharedPrefs   → username, hashed password, blood group, allergies,
│   │                            conditions, medications
│   └── FileStore (internal)   → Cactus model weights (.gguf), PDF documents
│
├── Sensor Layer (ForegroundService)
│   ├── SensorManager          → Accelerometer, Gyroscope, Barometer
│   ├── LocationManager        → GPS (always-on for emergency)
│   └── SensorFusionEngine     → scoring logic, trigger threshold
│
├── AI Layer (Cactus SDK)
│   ├── cactusInit / cactusComplete → Gemma 4 on-device via libcactus.so
│   ├── SystemPromptBuilder    → injects user profile at load time
│   └── EmergencyAnalyser      → runs severity classification offline
│
├── Emergency Layer
│   ├── CountdownOrchestrator  → 10s timer, beep, vibration
│   ├── PayloadBuilder         → constructs full emergency JSON
│   ├── OfflineQueue (Room)    → queues payload if no network
│   └── RetryWorker (WorkManager) → retries every 30s until sent
│
├── Communication Layer
│   ├── VectorGoClient (Retrofit + OkHttp) → primary agent endpoint
│   ├── SmsBoostManager        → SmsManager fallback (UID + GPS in SMS)
│   └── NetworkMonitor         → ConnectivityManager callback
│
└── UI Layer (Jetpack Compose)
    ├── Lock screen overlay     → TYPE_APPLICATION_OVERLAY (name + blood group + QR)
    ├── Navigation Component    → NavHost + deep links
    └── Compose screens         → see Section 11
```

**Key principle:** No data ever touches a server unless an emergency fires or the user
explicitly syncs. Cactus + Gemma is the local brain. VectorGo is the cloud orchestrator.
No Firebase. No Google Auth. No external auth service of any kind.

---

## 3. Tech Stack

| Concern | Library / API | Why |
|---|---|---|
| Language | Kotlin 2.0 | Coroutines, sealed classes, value classes |
| UI | Jetpack Compose + Material 3 | Modern, declarative, animation-ready |
| Architecture | MVVM + Clean Architecture | Repository pattern, testable layers |
| DI | Hilt | Jetpack-native, compile-time safety |
| Async | Kotlin Coroutines + Flow | Cold streams for sensor data, hot for UI state |
| **Local AI** | **Cactus SDK** (`libcactus.so` + `Cactus.kt`) | Fast ARM inference, Gemma 4 support, multimodal |
| **Gemma model** | **google/gemma-4-E2B-it** (via Cactus-Compute HuggingFace) | Completion + tools + vision + speech |
| **Auth** | **Local only** — username + SHA-256 hashed password in EncryptedSharedPrefs | No Firebase, no server, fully offline |
| Structured DB | Room | Offline queue, vitals, chat history |
| Key-value store | EncryptedSharedPreferences | Profile data (blood group, allergies, etc.) |
| Secure store | EncryptedDataStore | UID, session flag |
| Sensors | Android SensorManager | Native, no library needed |
| Location | Google Fused Location Provider | Battery-efficient, accurate |
| Network | Retrofit 2 + OkHttp 4 | Typed API calls, interceptors for retry |
| Background | WorkManager | Offline queue retry (guaranteed execution) |
| Foreground svc | ForegroundService (sensor monitoring) | Keeps sensor loop alive |
| Lock screen | NotificationCompat (VISIBILITY_PUBLIC) + TYPE_APPLICATION_OVERLAY | Name + blood group + QR |
| **QR generation** | **ZXing Android Embedded** — auto-generated at signup, points to `/qr/:uid` | Responder card, embedded in lock screen |
| PDF | PdfRenderer + DocumentFile | Render + extract text from uploaded PDFs |
| SMS fallback | SmsManager | Native Android SMS, no library |
| Voice input | SpeechRecognizer (Android) | On-device STT |
| TTS | TextToSpeech (Android) | On-device voice output |
| Camera | CameraX | QR scan (bystander scans patient QR) |
| Image loading | Coil 3 | Kotlin-first, Compose-native |
| Serialization | kotlinx.serialization | Type-safe JSON |
| Testing | JUnit 4 + MockK + Turbine | Flow testing |

---

## 4. Auth — Local Only, No Firebase

There is no remote auth system. Credentials live entirely on-device in `EncryptedSharedPreferences`.

### 4.1 Auth Flow

```
First launch
  → Show login screen with two options:
    A) "Create account"  → username + password form → generates UID → saves locally
    B) "Use test account" → pre-fills admin/1234, UID=1, full demo profile loaded
  
Subsequent launches
  → Show login screen (username + password)
  → Validate against locally stored hash
  → On success → navigate to HomeScreen
```

### 4.2 Test Account (Hardcoded)

Available on the login screen as a visible button: **"Use test account"**.
Tapping it pre-fills the form — user still hits "Login" to proceed. This way the flow is
identical to a real login (no special code path for UI state).

```kotlin
object TestAccount {
    const val USERNAME = "admin"
    const val PASSWORD = "1234"
    const val UID = "1"

    val profile = UserProfile(
        uid = "1",
        name = "Arjun Mehta",
        dob = "1998-04-12",
        bloodGroup = "O+",
        allergies = listOf("Penicillin", "Latex"),
        conditions = listOf("Type 2 Diabetes"),
        medications = listOf("Metformin 500mg twice daily"),
        emergencyContacts = listOf(
            EmergencyContact(name = "Priya Mehta", phone = "+91-9876543210", relation = "Sister")
        ),
        medicalHistory = "Patient has well-controlled Type 2 Diabetes diagnosed in 2020. " +
            "No cardiac history. Penicillin allergy confirmed (anaphylaxis risk). " +
            "Latex allergy (contact dermatitis). Last HbA1c: 6.8% (March 2026).",
        insuranceProvider = "Star Health",
        insurancePolicyNo = "SH-2024-XXXX"
    )
}
```

### 4.3 AuthRepository

```kotlin
// AuthRepository.kt
class AuthRepository @Inject constructor(
    private val prefs: EncryptedSharedPreferences,
    private val profileStore: ProfileDataStore
) {
    fun signup(username: String, password: String): Result<String> {
        if (prefs.contains(KEY_USERNAME)) return Result.failure(Exception("Account exists"))
        val uid = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD_HASH, password.sha256())
            .putString(KEY_UID, uid)
            .apply()
        return Result.success(uid)
    }

    fun login(username: String, password: String): Result<String> {
        // Test account — always works, no stored hash needed
        if (username == TestAccount.USERNAME && password == TestAccount.PASSWORD) {
            loadTestProfile()
            return Result.success(TestAccount.UID)
        }
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(Exception("No account"))
        val storedUser = prefs.getString(KEY_USERNAME, null) ?: return Result.failure(Exception("No account"))
        return if (storedUser == username && password.sha256() == storedHash) {
            Result.success(prefs.getString(KEY_UID, "")!!)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }

    private fun loadTestProfile() {
        profileStore.saveProfileSync(TestAccount.profile)
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_USERNAME = "auth_username"
        const val KEY_PASSWORD_HASH = "auth_password_hash"
        const val KEY_UID = "auth_uid"
    }
}
```

### 4.4 Login Screen (Compose)

```kotlin
@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(/* ... */) {
        TextField(value = username, onValueChange = { username = it }, label = { Text("Username") })
        TextField(value = password, onValueChange = { password = it },
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())

        Button(onClick = { viewModel.login(username, password) }) { Text("Login") }

        // Test account shortcut — always visible
        OutlinedButton(
            onClick = { username = TestAccount.USERNAME; password = TestAccount.PASSWORD }
        ) {
            Text("Use test account  (admin / 1234)")
        }

        TextButton(onClick = { /* nav to signup */ }) { Text("Create new account") }
    }
}
```

---

## 5. Cactus SDK Integration

Replace all `com.google.mediapipe:tasks-genai` / `LlmInference` references with Cactus.

### 5.1 Setup

```
1. Clone cactus and build for Android:
   git clone https://github.com/cactus-compute/cactus && cd cactus && source ./setup
   cactus build --android

2. Copy output files into the project:
   android/libcactus.so  →  app/src/main/jniLibs/arm64-v8a/libcactus.so
   android/Cactus.kt     →  app/src/main/java/com/cactus/Cactus.kt

3. Download model weights (Gemma 4):
   cactus download google/gemma-4-E2B-it --precision INT4
   → weights land in ./weights/gemma-4-E2B-it/
   → on device: copied to app internal storage on first launch
```

No Maven dependency needed. Cactus is a native `.so` + a single Kotlin file.

### 5.2 GemmaEngine (Cactus API)

```kotlin
// ai/GemmaEngine.kt
class GemmaEngine @Inject constructor(
    private val context: Context,
    private val systemPromptBuilder: SystemPromptBuilder
) {
    private var modelHandle: Long = 0L
    private val modelPath: String
        get() = "${context.filesDir}/models/gemma-4-e2b-it"

    // Called once after model is downloaded to internal storage
    suspend fun load(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (modelHandle != 0L) return@withContext // already loaded

        // cactusInit: modelPath, corpusDir (null = no RAG), cacheIndex
        modelHandle = cactusInit(modelPath, null, false)
        if (modelHandle == 0L) throw RuntimeException(cactusGetLastError())

        onProgress(1.0f)
    }

    // Streaming chat — emits tokens as they are generated
    fun chat(userMessage: String): Flow<String> = callbackFlow {
        if (modelHandle == 0L) throw IllegalStateException("Model not loaded")

        val systemPrompt = systemPromptBuilder.buildSync() // reads from EncryptedSharedPrefs

        val messages = buildMessagesJson(systemPrompt, userMessage)
        val options  = """{"max_tokens":512,"temperature":0.7}"""

        // cactusComplete with streaming callback
        withContext(Dispatchers.IO) {
            cactusComplete(
                model      = modelHandle,
                messagesJson = messages,
                optionsJson  = options,
                toolsJson    = null,
                callback     = CactusTokenCallback { token, _ -> trySend(token) }
            )
        }
        close()
    }

    // One-shot call — used for emergency responder brief generation
    suspend fun complete(prompt: String, maxTokens: Int = 200): String = withContext(Dispatchers.IO) {
        val messages = """[{"role":"user","content":"${prompt.escapeJson()}"}]"""
        val options  = """{"max_tokens":$maxTokens,"temperature":0.3}"""
        val resultJson = cactusComplete(modelHandle, messages, options, null, null)
        val result = JSONObject(resultJson)
        if (!result.getBoolean("success")) throw RuntimeException(result.optString("error"))
        result.getString("response")
    }

    fun destroy() {
        if (modelHandle != 0L) cactusDestroy(modelHandle)
        modelHandle = 0L
    }

    private fun buildMessagesJson(systemPrompt: String, userMessage: String): String {
        return """[
            {"role":"system","content":"${systemPrompt.escapeJson()}"},
            {"role":"user","content":"${userMessage.escapeJson()}"}
        ]"""
    }

    private fun String.escapeJson() = replace("\"", "\\\"").replace("\n", "\\n")
}
```

### 5.3 SystemPromptBuilder (unchanged logic, wired to Cactus)

```kotlin
// ai/SystemPromptBuilder.kt
class SystemPromptBuilder @Inject constructor(
    private val profileStore: ProfileDataStore
) {
    fun buildSync(): String {
        val p = profileStore.getProfileSync()
        return buildString {
            appendLine("You are Vektor, a personal emergency health assistant.")
            appendLine("You run entirely on-device. You have no internet access.")
            appendLine("Always prioritise the user's safety. Keep responses concise.")
            appendLine()
            appendLine("USER MEDICAL PROFILE:")
            appendLine("Name: ${p.name}")
            appendLine("Date of birth: ${p.dob}")
            appendLine("Blood group: ${p.bloodGroup}")
            if (p.allergies.isNotEmpty())
                appendLine("⚠️ ALLERGIES (critical): ${p.allergies.joinToString(", ")}")
            if (p.conditions.isNotEmpty())
                appendLine("Chronic conditions: ${p.conditions.joinToString(", ")}")
            if (p.medications.isNotEmpty())
                appendLine("Current medications: ${p.medications.joinToString(", ")}")
            appendLine()
            appendLine("In any emergency context, ALWAYS lead with blood group, allergies, conditions.")
            appendLine("Never invent medical information not listed above.")
        }
    }
}
```

### 5.4 Model Download Screen

Model weights ship from HuggingFace via Cactus-Compute's CDN. The app downloads them
on first launch to `context.filesDir/models/`.

```kotlin
// ModelDownloadViewModel.kt
class ModelDownloadViewModel @Inject constructor(
    private val context: Context
) : ViewModel() {

    val progress = MutableStateFlow(0f)
    val statusText = MutableStateFlow("Preparing download...")

    fun startDownload() = viewModelScope.launch(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models/gemma-4-e2b-it")
        modelDir.mkdirs()

        // Download each shard file — Cactus-Compute HuggingFace repo
        val shardUrls = listOf(
            "https://huggingface.co/Cactus-Compute/gemma-4-E2B-it-INT4/resolve/main/model.gguf"
            // add more shards if multi-file
        )

        shardUrls.forEachIndexed { idx, url ->
            statusText.value = "Downloading model (${idx + 1}/${shardUrls.size})..."
            downloadFile(url, File(modelDir, "model.gguf")) { downloaded, total ->
                progress.value = (idx.toFloat() / shardUrls.size) + (downloaded.toFloat() / total / shardUrls.size)
            }
        }

        statusText.value = "Setting up emergency detection..."
        // Verify model loads correctly
        val handle = cactusInit(modelDir.absolutePath, null, false)
        if (handle == 0L) {
            statusText.value = "Error: ${cactusGetLastError()}"
            return@launch
        }
        cactusDestroy(handle)
        statusText.value = "Ready."
        progress.value = 1.0f
    }
}
```

---

## 6. QR — Auto-Generated, Lock Screen Embedded

### 6.1 Auto-Generation at Signup

QR is generated immediately after UID is created — no user action needed. Stored as a
`Bitmap` in the app's internal storage (`context.filesDir/qr_card.png`) so it can be
displayed on the lock screen without re-generating each time.

```kotlin
// QrManager.kt
class QrManager @Inject constructor(private val context: Context) {

    fun generateAndSave(uid: String): Bitmap {
        val url = "https://vektor.app/qr/$uid"
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512)
            for (y in 0 until 512)
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)

        // Save to internal storage for lock screen use
        FileOutputStream(File(context.filesDir, "qr_card.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    fun loadSaved(): Bitmap? {
        val file = File(context.filesDir, "qr_card.png")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}
```

`QrManager.generateAndSave(uid)` is called in `AuthRepository.signup()` and also when
the test account is loaded (using UID = "1").

### 6.2 QR Card Screen

```kotlin
@Composable
fun QrCardScreen(viewModel: QrCardViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    Column(modifier = Modifier.padding(24.dp)) {
        Text("Emergency Card", style = MaterialTheme.typography.headlineSmall)
        Text("Scan to view full medical profile — no app needed")

        Spacer(Modifier.height(16.dp))

        // QR bitmap
        qrBitmap?.let { bmp ->
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Emergency QR",
                modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally))
        }

        Spacer(Modifier.height(16.dp))

        // Summary of what scanner will see
        InfoRow("Blood group", profile?.bloodGroup ?: "—")
        InfoRow("Allergies", profile?.allergies?.joinToString(", ") ?: "None")
        InfoRow("Conditions", profile?.conditions?.joinToString(", ") ?: "None")
        InfoRow("Emergency contact", profile?.emergencyContacts?.firstOrNull()?.name ?: "—")

        Spacer(Modifier.height(16.dp))

        // Privacy toggles
        PrivacyToggleRow("Show full name", viewModel.showName) { viewModel.toggleName(it) }
        PrivacyToggleRow("Show contact phone", viewModel.showPhone) { viewModel.togglePhone(it) }

        OutlinedButton(onClick = { viewModel.share() }) { Text("Share QR card") }
    }
}
```

---

## 7. Lock Screen — Name + Blood Group + QR (on tap)

### 7.1 What Shows Without Any Interaction

The persistent notification (always visible on lock screen, `VISIBILITY_PUBLIC`) shows:

```
┌──────────────────────────────────────┐
│  ▊  VEKTOR                           │
│     Arjun Mehta · Blood Group: O+    │  ← name + blood group, always visible
│     ⚠️  Allergic: Penicillin, Latex  │
│                                      │
│  [📞 Call Priya]   [🆘 Send Alert]   │
└──────────────────────────────────────┘
```

### 7.2 What Shows When Tapped (Overlay)

When the user taps the notification OR when the `LockScreenOverlayService` detects the
phone is locked, a full overlay appears showing name + blood group + the pre-saved QR
bitmap. No unlock required.

```kotlin
// LockScreenOverlayService.kt
class LockScreenOverlayService : Service() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) { stopSelf(); return START_NOT_STICKY }
        if (overlayView != null) return START_NOT_STICKY // already showing

        val profile = // read from EncryptedSharedPrefs sync
        val qrBitmap = QrManager(this).loadSaved()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        overlayView = ComposeView(this).apply {
            setContent {
                LockScreenCard(
                    name        = profile.name,
                    bloodGroup  = profile.bloodGroup,
                    allergies   = profile.allergies.take(2),
                    qrBitmap    = qrBitmap,
                    onSosClick  = { startBystanderReport() },
                    onCallClick = { callEmergencyContact(profile) }
                )
            }
        }
        windowManager.addView(overlayView, params)
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }
}

// LockScreenCard.kt  (Compose UI)
@Composable
fun LockScreenCard(
    name: String,
    bloodGroup: String,
    allergies: List<String>,
    qrBitmap: Bitmap?,
    onSosClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0D0D0D).copy(alpha = 0.92f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▊", color = Color(0xFFFF4444), fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("VEKTOR", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Emergency Info", color = Color(0xFF888888), fontSize = 12.sp)
            }

            Divider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 12.dp))

            // Name + blood group — always large and readable
            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFB71C1C)) {
                    Text("🩸 $bloodGroup", color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (allergies.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("⚠️ ${allergies.joinToString(", ")}", color = Color(0xFFFFCC00), fontSize = 13.sp)
                }
            }

            // QR code
            qrBitmap?.let { bmp ->
                Spacer(Modifier.height(16.dp))
                Text("Scan for full medical profile:", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Emergency QR",
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("vektor.app/qr/[uid]", color = Color(0xFF666666), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("No app needed to scan", color = Color(0xFF555555), fontSize = 11.sp)
                    }
                }
            }

            Divider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCallClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF444444))
                ) { Text("📞 Call contact") }

                Button(
                    onClick = onSosClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("🆘 Send Alert") }
            }
        }
    }
}
```

### 7.3 Lock Screen Trigger Logic

The overlay service is started by a `BroadcastReceiver` listening for
`Intent.ACTION_SCREEN_ON` + `KeyguardManager.isKeyguardLocked`:

```kotlin
// LockScreenReceiver.kt
class LockScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            context.startService(Intent(context, LockScreenOverlayService::class.java))
        }
    }
}
```

Register in `AndroidManifest.xml`:
```xml
<receiver android:name=".lockscreen.LockScreenReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.SCREEN_ON"/>
        <action android:name="android.intent.action.USER_PRESENT"/>
    </intent-filter>
</receiver>
```

---

## 8. Sensor Fusion Engine

_(Unchanged from v2 — see Section 5 of v2)_

Runs inside a `ForegroundService`. Uses accelerometer + gyroscope + barometer with a
weighted score trigger. Sensitivity configurable: Low / Medium / High.

---

## 9. Emergency Countdown (Compose UI)

_(Unchanged from v2 — see Section 6 of v2)_

10-second countdown, pulsing number, escalating beep via `MediaPlayer`, vibration pattern,
large green CANCEL button.

---

## 10. Emergency Payload + Offline Queue

_(Unchanged from v2 — see Sections 7 and 4.3 of v2)_

- Room-first: payload written to DB before network call
- WorkManager retry: every 30s, up to 10 attempts
- SMS boost: fires after 60s offline via `SmsManager`
- All payloads tagged `network_status: "online" | "offline_queued"`

Gemma's `responderBrief` in the payload is now generated via `GemmaEngine.complete()`
using the Cactus API (one-shot, no streaming needed).

---

## 11. Project Structure

```
vektor/
├── app/
│   ├── src/main/
│   │   ├── jniLibs/arm64-v8a/
│   │   │   └── libcactus.so              ← Cactus native library
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/com/vektor/
│   │       ├── VektorApp.kt
│   │       ├── MainActivity.kt
│   │       │
│   │       ├── data/
│   │       │   ├── local/
│   │       │   │   ├── db/
│   │       │   │   │   ├── VektorDatabase.kt
│   │       │   │   │   ├── EmergencyQueueDao.kt
│   │       │   │   │   └── VitalsDao.kt
│   │       │   │   └── prefs/
│   │       │   │       ├── ProfileDataStore.kt
│   │       │   │       └── SettingsDataStore.kt
│   │       │   ├── remote/
│   │       │   │   ├── VectorGoClient.kt
│   │       │   │   └── dto/
│   │       │   └── repository/
│   │       │       ├── AuthRepository.kt       ← local auth, test account
│   │       │       ├── EmergencyRepository.kt
│   │       │       ├── ProfileRepository.kt
│   │       │       └── GemmaRepository.kt
│   │       │
│   │       ├── domain/
│   │       │   ├── model/
│   │       │   │   ├── UserProfile.kt
│   │       │   │   ├── EmergencyPayload.kt
│   │       │   │   └── TestAccount.kt          ← hardcoded test user
│   │       │   └── usecase/
│   │       │       ├── TriggerEmergencyUseCase.kt
│   │       │       ├── BuildPayloadUseCase.kt
│   │       │       └── SyncQueueUseCase.kt
│   │       │
│   │       ├── sensor/
│   │       │   ├── SensorFusionEngine.kt
│   │       │   ├── SensorMonitorService.kt
│   │       │   └── NetworkMonitor.kt
│   │       │
│   │       ├── ai/
│   │       │   ├── GemmaEngine.kt              ← Cactus SDK wrapper
│   │       │   ├── SystemPromptBuilder.kt
│   │       │   └── EmergencyAnalyser.kt
│   │       │
│   │       ├── emergency/
│   │       │   ├── CountdownOrchestrator.kt
│   │       │   ├── EmergencyQueueRepository.kt
│   │       │   ├── EmergencyRetryWorker.kt
│   │       │   ├── SmsBoostManager.kt
│   │       │   └── PayloadBuilder.kt
│   │       │
│   │       ├── lockscreen/
│   │       │   ├── LockScreenNotificationManager.kt
│   │       │   ├── LockScreenOverlayService.kt  ← name + blood group + QR on tap
│   │       │   ├── LockScreenReceiver.kt        ← SCREEN_ON broadcast
│   │       │   ├── LockScreenCard.kt            ← Compose UI for overlay
│   │       │   └── BystanderReportService.kt
│   │       │
│   │       ├── qr/
│   │       │   └── QrManager.kt                ← auto-generate + save on signup
│   │       │
│   │       └── ui/
│   │           ├── theme/
│   │           ├── navigation/VektorNavGraph.kt
│   │           └── screens/
│   │               ├── auth/
│   │               │   ├── LoginScreen.kt      ← username/pass + "Use test account" button
│   │               │   └── SignupScreen.kt
│   │               ├── onboarding/
│   │               │   ├── ProfileSetupScreen.kt
│   │               │   ├── MedicalHistoryScreen.kt
│   │               │   ├── EmergencyContactsScreen.kt
│   │               │   └── ModelDownloadScreen.kt
│   │               ├── home/HomeScreen.kt
│   │               ├── gemma/GemmaChatScreen.kt
│   │               ├── health/
│   │               │   ├── HealthScreen.kt
│   │               │   └── VitalsLogScreen.kt
│   │               ├── emergency/
│   │               │   ├── CountdownScreen.kt
│   │               │   ├── EmergencyActiveScreen.kt
│   │               │   └── DrillModeScreen.kt
│   │               ├── qr/QrCardScreen.kt      ← shows auto-generated QR + privacy toggles
│   │               └── settings/SettingsScreen.kt
│   │
│   └── build.gradle.kts
│
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 12. Gradle Dependencies

```kotlin
// app/build.gradle.kts
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Encrypted storage (local auth + profile)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // *** Cactus SDK — native .so, no Maven dep needed ***
    // libcactus.so placed in jniLibs/arm64-v8a/
    // Cactus.kt placed in com.cactus package
    // Nothing to add here — JNI loaded automatically

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // NO Firebase — removed entirely

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coil
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // JSON (for parsing Cactus response)
    implementation("org.json:json:20240303")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## 13. Permissions (AndroidManifest.xml)

```xml
<!-- Sensors -->
<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS"/>
<uses-permission android:name="android.permission.BODY_SENSORS"/>

<!-- Location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>

<!-- Communication -->
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE"/>

<!-- Lock screen overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.USE_BIOMETRIC"/>

<!-- Background execution -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<!-- Voice -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.VIBRATE"/>
```

---

## 14. Screen List

```
Auth (always shown at launch)
├── LoginScreen
│   ├── Username + password fields
│   ├── "Login" button
│   ├── "Use test account (admin / 1234)" button  ← pre-fills form
│   └── "Create account" link → SignupScreen
└── SignupScreen
    ├── Username + password + confirm password
    ├── Submit → generates UID → QR auto-saved → goes to onboarding
    └── "Use test account" option → skips signup entirely

Onboarding (one-time, DataStore flag)
├── ProfileSetupScreen     (name, DOB, blood group)
├── MedicalHistoryScreen   (allergies, conditions, medications, PDF)
├── EmergencyContactsScreen
└── ModelDownloadScreen    (Cactus + Gemma 4, real progress bar, non-skippable)

Main App (BottomNavigation)
├── HomeScreen
│   ├── Pulse indicator (ForegroundService active)
│   ├── Profile summary card
│   ├── Ask Gemma → GemmaChatScreen
│   ├── My Health → HealthScreen
│   └── Manual SOS (hold 3s)
├── GemmaChatScreen        (streaming Cactus tokens via Flow)
├── HealthScreen
│   ├── Profile edit
│   ├── Vitals log
│   └── PDF documents
└── SettingsScreen
    ├── Detection sensitivity
    ├── Safe zone / snooze
    ├── Lock screen card toggle
    ├── SMS boost toggle
    ├── Privacy (QR field visibility)
    └── Drill mode

QR Card Screen
└── QrCardScreen
    ├── Auto-generated QR (ZXing → vektor.app/qr/:uid)
    ├── Summary: blood group, allergies, contact
    ├── Privacy toggles
    └── Share button

Emergency Overlays
├── CountdownScreen        (10s beep + cancel)
├── EmergencyActiveScreen  (sent + Gemma guidance)
├── CancelConfirmScreen
└── SafetyCheckScreen      ("Are you okay?" 5min after cancel)

Lock Screen (no unlock needed)
└── Notification (always): name + blood group + allergies + call/SOS buttons
└── Overlay (on screen-on): name + blood group + QR + call/SOS buttons
```

---

## 15. Endpoint Summary

_(Unchanged — VectorGo agent, single external connection)_

```
POST  /check-uid          First launch. Registers UID (uid=1 for test account).
POST  /emergency          Full payload — triggers VectorGo pipeline.
POST  /bystander-report   Guest mode / lock screen SOS tap.
GET   /sync/:uid          Reconcile after offline period.
GET   /qr/:uid            Served by VectorGo — responder web view. NOT called by app.
```

SMS format (Twilio webhook):
```
VEKTOR_EMERGENCY:uid=1,lat=28.6139,lng=77.2090,ts=1745000000000
```

---

## 16. Change Log

| Version | Change |
|---|---|
| v1.0 | Initial PRD — React Native |
| v2.0 | Kotlin native, Google AI Edge SDK (MediaPipe), Firebase Auth |
| **v3.0** | **Cactus SDK replaces MediaPipe. Local username/password auth replaces Firebase. Test account (admin/1234, UID=1) added. QR auto-generated at signup. Lock screen shows name + blood group + QR on tap.** |

---

*Document: Vektor App PRD v3 (Kotlin + Cactus SDK)*
*Connects to: VectorGo Agent Engine PRD v1*
*Built alongside: NADI Hospital Dashboard (Next.js)*