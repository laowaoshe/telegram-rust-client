package com.example.telegramrust

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.animation.DecelerateInterpolator
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

class MainActivity : Activity() {
    private companion object {
        const val EMBEDDED_API_ID = 37506075
        const val EMBEDDED_API_HASH = "6fdf2220f3a9db5814221e8cbeed89b9"
        const val REQUEST_FILE = 7101
        const val REQUEST_AUDIO = 7102
        const val REQUEST_LOCATION = 7103
    }

    private lateinit var status: TextView
    private lateinit var apiId: EditText
    private lateinit var apiHash: EditText
    private lateinit var phone: EditText
    private lateinit var code: EditText
    private lateinit var password: EditText
    private lateinit var authPanel: LinearLayout
    private lateinit var chatPanel: LinearLayout
    private lateinit var dialogsScreen: LinearLayout
    private lateinit var chatScreen: LinearLayout
    private lateinit var contactsScreen: LinearLayout
    private lateinit var settingsScreen: LinearLayout
    private lateinit var profileScreen: LinearLayout
    private lateinit var dialogs: LinearLayout
    private var cachedDialogsResult = ""
    private lateinit var messages: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var chatTitle: TextView
    private lateinit var qrImage: ImageView
    private lateinit var scroll: ScrollView
    private var selectedDialog = -1
    private var selectedDialogTitle = ""
    private var voiceRecorder: MediaRecorder? = null
    private var voiceFile: java.io.File? = null
    private var recordingVoice = false

    private val black = Color.BLACK
    // Полупрозрачные поверхности оставляют живой фон видимым сквозь «стекло».
    // В отличие от старой темы здесь намеренно нет светлых обводок.
    private val surface = Color.argb(178, 18, 18, 30)
    private val surfaceSoft = Color.argb(158, 30, 25, 54)
    private val purple = Color.argb(232, 88, 73, 246)
    private val purpleDark = Color.argb(142, 55, 42, 112)
    private val white = Color.rgb(245, 243, 255)
    private val muted = Color.rgb(139, 139, 152)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun glass(fill: Int = surface, radius: Int = 18): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        // Без setStroke: именно эта контрастная линия давала неприятную рамку.
        setDither(true)
    }

    private fun glassButton(label: String, fill: Int = surfaceSoft): Button = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(white)
        isAllCaps = false
        minHeight = dp(48)
        stateListAnimator = null
        background = glass(fill, 16)
        setPadding(dp(16), dp(4), dp(16), dp(4))
        elevation = dp(1).toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // Небольшая пружинистая реакция на касание вместо резкого изменения цвета.
        setOnTouchListener { view, event -> pressAnimation(view, event) }
    }

    private fun pressAnimation(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().scaleX(0.985f).scaleY(0.985f)
                    .setDuration(90)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        return false
    }

    private fun avatarView(title: String, size: Int = 52): TextView = TextView(this).apply {
        val colors = intArrayOf(
            Color.rgb(78, 105, 190),
            Color.rgb(126, 76, 178),
            Color.rgb(36, 137, 132),
            Color.rgb(180, 95, 80)
        )
        val color = colors[(title.hashCode() and Int.MAX_VALUE) % colors.size]
        text = title.trim().split(Regex("\\s+"))
            .mapNotNull { it.firstOrNull()?.toString()?.toUpperCase() }
            .take(2)
            .joinToString("")
            .ifBlank { "?" }
        textSize = if (size >= 64) 25f else 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun label(value: String, size: Float, color: Int = white): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Color.argb(28, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }

    private fun launchFilePicker() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            REQUEST_FILE
        )
    }

    private fun toggleVoiceRecording() {
        if (recordingVoice) {
            try {
                voiceRecorder?.stop()
            } catch (_: RuntimeException) {
                voiceFile?.delete()
            }
            voiceRecorder?.release()
            voiceRecorder = null
            recordingVoice = false
            status.text = "Голосовое сохранено: ${voiceFile?.name ?: "файл"}"
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            status.text = "Разреши доступ к микрофону и нажми запись ещё раз"
            return
        }
        val target = java.io.File(cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        try {
            voiceRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            voiceFile = target
            recordingVoice = true
            status.text = "Идёт запись голосового… нажми ещё раз, чтобы остановить"
        } catch (error: Exception) {
            voiceRecorder?.release()
            voiceRecorder = null
            status.text = "Не удалось начать запись: ${error.message ?: "ошибка"}"
        }
    }

    private fun shareLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            status.text = "Разреши доступ к геолокации и нажми ещё раз"
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .asSequence()
            .mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            .firstOrNull()
        if (location == null) {
            status.text = "Геолокация пока недоступна"
            return
        }
        if (selectedDialog < 0) {
            status.text = "Сначала открой диалог"
            return
        }
        val text = "📍 Моя геопозиция: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        status.text = "Отправляю геопозицию…"
        runRust({ RustBridge.nativeSendMessage(selectedDialog, text) }) { result ->
            status.text = result
            if (!result.startsWith("Ошибка:")) loadMessages(selectedDialog)
        }
    }

    private fun openProfile(showScreen: (View) -> Unit) {
        if (selectedDialog < 0) {
            status.text = "Сначала выбери диалог"
            return
        }
        profileScreen.removeAllViews()
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = glassButton("‹ Чат", surfaceSoft)
        toolbar.addView(back)
        toolbar.addView(label("Профиль", 20f), LinearLayout.LayoutParams(0, -2, 1f))
        profileScreen.addView(toolbar)
        addGap(profileScreen, 18)
        val avatar = avatarView(selectedDialogTitle, 92)
        profileScreen.addView(avatar, LinearLayout.LayoutParams(-1, dp(92)))
        val name = label(selectedDialogTitle.ifBlank { "Пользователь" }, 25f)
        name.gravity = Gravity.CENTER
        profileScreen.addView(name)
        val username = label("Профиль Telegram", 15f, muted)
        username.gravity = Gravity.CENTER
        profileScreen.addView(username)
        addGap(profileScreen, 22)
        profileScreen.addView(divider())
        profileScreen.addView(label("Информация", 15f, muted))
        profileScreen.addView(label("Имя: ${selectedDialogTitle.ifBlank { "Не указано" }}", 16f))
        profileScreen.addView(label("Фото и описание профиля появятся после подключения Telegram profile API.", 14f, muted))
        addGap(profileScreen, 16)
        val share = glassButton("Поделиться контактом", purpleDark)
        val media = glassButton("Общие медиа", surfaceSoft)
        profileScreen.addView(share)
        addGap(profileScreen, 8)
        profileScreen.addView(media)
        back.setOnClickListener { showScreen(chatScreen) }
        showScreen(profileScreen)
    }

    private fun addGap(parent: LinearLayout, height: Int = 8) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = black
        window.navigationBarColor = black

        val padding = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(padding, padding, padding, padding)
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            addView(root)
        }

        fun text(value: String, size: Float, color: Int = white): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(5), 0, dp(5))
        }

        fun field(hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            hint = hintText
            inputType = type
            textSize = 15f
            setTextColor(white)
            setHintTextColor(muted)
            background = glass(surface, 16)
            setPadding(dp(16), 0, dp(16), 0)
        }

        root.addView(text("Telegram", 28f))
        root.addView(text("Безопасные сообщения на Rust", 15f, muted))
        status = text("Готово к подключению", 14f, muted)
        root.addView(status)
        addGap(root, 8)

        authPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        authPanel.addView(text("Вход в Telegram", 20f))
        authPanel.addView(text("Войди по QR-коду или номеру телефона. Сессия хранится только на устройстве.", 13f, muted))
        addGap(authPanel, 6)

        apiId = field("API ID", InputType.TYPE_CLASS_NUMBER)
        apiHash = field("API hash")
        phone = field("Номер телефона, например +380...", InputType.TYPE_CLASS_PHONE)
        code = field("Код из Telegram или SMS", InputType.TYPE_CLASS_NUMBER)
        password = field("Пароль двухэтапной проверки Telegram", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        apiId.visibility = View.GONE
        apiHash.visibility = View.GONE

        val requestButton = glassButton("Получить код Telegram", purple)
        val qrButton = glassButton("Показать новый QR-код", purpleDark)
        val qrCheckButton = glassButton("Проверить QR-вход", purpleDark)
        val codeButton = glassButton("Войти по коду", purpleDark)
        val passwordButton = glassButton("Войти по паролю 2FA", purpleDark)
        val dialogsButton = glassButton("Открыть Telegram", purple)

        qrImage = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            visibility = View.GONE
        }
        authPanel.addView(apiId)
        addGap(authPanel)
        authPanel.addView(apiHash)
        addGap(authPanel)
        authPanel.addView(phone)
        addGap(authPanel)
        authPanel.addView(requestButton)
        addGap(authPanel)
        authPanel.addView(qrButton)
        addGap(authPanel)
        authPanel.addView(qrCheckButton)
        addGap(authPanel)
        authPanel.addView(code)
        addGap(authPanel)
        authPanel.addView(codeButton)
        addGap(authPanel)
        authPanel.addView(password)
        addGap(authPanel)
        authPanel.addView(passwordButton)
        addGap(authPanel)
        authPanel.addView(dialogsButton)
        authPanel.addView(qrImage)
        root.addView(authPanel)

        chatPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(surface, 20)
            setPadding(dp(12), dp(5), dp(8), dp(5))
        }
        val brand = text("Telegram", 19f)
        topBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val refreshButton = glassButton("⟳", surfaceSoft)
        topBar.addView(refreshButton)
        chatPanel.addView(topBar)
        addGap(chatPanel, 10)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glass(surface, 18)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val chatsNav = glassButton("Чаты", purpleDark)
        val contactsNav = glassButton("Контакты", surface)
        val settingsNav = glassButton("Настройки", surface)
        nav.addView(chatsNav, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(contactsNav, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(settingsNav, LinearLayout.LayoutParams(0, -2, 1f))
        chatPanel.addView(nav)
        addGap(chatPanel, 10)

        val screenHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dialogsScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        dialogsScreen.addView(text("Чаты", 22f))
        val dialogSearch = field("⌕  Поиск")
        dialogsScreen.addView(dialogSearch)
        dialogSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderDialogs(cachedDialogsResult, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        addGap(dialogsScreen, 8)
        dialogs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dialogsScreen.addView(dialogs)

        chatScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val chatToolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val backButton = glassButton("‹", surfaceSoft)
        chatTitle = text("Чат", 20f)
        chatToolbar.addView(backButton)
        chatToolbar.addView(chatTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val profileButton = glassButton("ⓘ", surfaceSoft)
        chatToolbar.addView(profileButton)
        chatScreen.addView(chatToolbar)
        addGap(chatScreen)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        chatScreen.addView(text("История", 16f, muted))
        chatScreen.addView(messages)
        messageInput = field("Сообщение")
        val attachButton = glassButton("＋", surfaceSoft)
        val sendButton = glassButton("➤", purple)
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        composer.addView(attachButton, LinearLayout.LayoutParams(dp(54), -2))
        composer.addView(messageInput, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(dp(6), 0, dp(6), 0)
        })
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(62), -2))
        addGap(chatScreen)
        chatScreen.addView(composer)

        val attachmentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surfaceSoft, 18)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val fileAction = glassButton("📎  Файл", surface)
        val voiceAction = glassButton("🎙  Голосовое", surface)
        val locationAction = glassButton("📍  Геопозиция", surface)
        attachmentPanel.addView(fileAction)
        addGap(attachmentPanel, 6)
        attachmentPanel.addView(voiceAction)
        addGap(attachmentPanel, 6)
        attachmentPanel.addView(locationAction)
        chatScreen.addView(attachmentPanel)

        attachButton.setOnClickListener {
            attachmentPanel.visibility = if (attachmentPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            attachmentPanel.alpha = 0f
            attachmentPanel.animate().alpha(1f).setDuration(180).start()
        }
        fileAction.setOnClickListener { launchFilePicker() }
        voiceAction.setOnClickListener {
            toggleVoiceRecording()
            voiceAction.text = if (recordingVoice) "⏹  Остановить запись" else "🎙  Голосовое"
        }
        locationAction.setOnClickListener { shareLocation() }

        contactsScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        contactsScreen.addView(text("Пользователи", 22f))
        contactsScreen.addView(text("Найди пользователя по @username", 15f, muted))
        val usernameField = field("@username")
        val usernameButton = glassButton("Найти пользователя", purple)
        val usernameResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contactsScreen.addView(usernameField)
        addGap(contactsScreen)
        contactsScreen.addView(usernameButton)
        addGap(contactsScreen)
        contactsScreen.addView(usernameResults)
        usernameButton.setOnClickListener {
            val query = usernameField.text.toString().trim()
            if (query.isEmpty()) {
                status.text = "Введи username пользователя"
                return@setOnClickListener
            }
            status.text = "Ищу пользователя…"
            runRust({ RustBridge.nativeSearchUsername(query) }) { result ->
                usernameResults.removeAllViews()
                val resultCard = glassButton("◉  $result", surfaceSoft)
                usernameResults.addView(resultCard)
                status.text = if (result.startsWith("Ошибка:")) result else "Пользователь найден"
            }
        }

        settingsScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        settingsScreen.addView(text("Настройки", 22f))
        settingsScreen.addView(text("Тема: AMOLED Glass", 16f))
        settingsScreen.addView(text("Акцент: глубокий фиолетовый", 16f, muted))
        settingsScreen.addView(text("Сессия сохраняется локально на устройстве.", 15f, muted))

        profileScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }

        screenHost.addView(dialogsScreen)
        screenHost.addView(chatScreen)
        screenHost.addView(contactsScreen)
        screenHost.addView(settingsScreen)
        screenHost.addView(profileScreen)
        chatPanel.addView(screenHost)
        root.addView(chatPanel)

        val sessionPath = File(filesDir, "telegram.session").absolutePath

        fun showScreen(screen: View) {
            val screens = listOf(dialogsScreen, chatScreen, contactsScreen, settingsScreen, profileScreen)
            screens.forEach { candidate ->
                if (candidate !== screen) {
                    candidate.animate().cancel()
                    candidate.visibility = View.GONE
                    candidate.alpha = 1f
                    candidate.translationY = 0f
                }
            }
            screen.animate().cancel()
            screen.visibility = View.VISIBLE
            screen.alpha = 0f
            screen.translationY = dp(14).toFloat()
            screen.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        fun openChats() {
            authPanel.visibility = View.GONE
            chatPanel.visibility = View.VISIBLE
            showScreen(dialogsScreen)
        }

        requestButton.setOnClickListener {
            val id = EMBEDDED_API_ID
            val hash = EMBEDDED_API_HASH
            val phoneValue = phone.text.toString().trim()
            if (id == null || hash.isEmpty() || phoneValue.isEmpty()) {
                status.text = "Заполни API ID, API hash и номер телефона"
                return@setOnClickListener
            }
            status.text = "Запрашиваю код…"
            runRust({ RustBridge.nativeRequestCode(id, hash, phoneValue, sessionPath) })
        }
        qrButton.setOnClickListener {
            val id = EMBEDDED_API_ID
            val hash = EMBEDDED_API_HASH
            if (id == null || hash.isEmpty()) {
                status.text = "Заполни API ID и API hash"
                return@setOnClickListener
            }
            status.text = "Создаю новый QR-код…"
            runRust({ RustBridge.nativeRequestQr(id, hash, sessionPath) }) { result ->
                if (result.startsWith("tg://login?")) {
                    try {
                        qrImage.setImageBitmap(makeQrBitmap(result))
                        qrImage.visibility = View.VISIBLE
                        status.text = "Отсканируй новый QR в Telegram, затем нажми «Проверить QR-вход»."
                    } catch (_: Exception) { status.text = "Не удалось создать QR-код" }
                } else status.text = result
            }
        }
        qrCheckButton.setOnClickListener {
            status.text = "Проверяю QR-вход…"
            runRust({ RustBridge.nativeCheckQr() })
        }
        codeButton.setOnClickListener {
            val value = code.text.toString().trim()
            if (value.isEmpty()) { status.text = "Введи код из Telegram или SMS"; return@setOnClickListener }
            status.text = "Проверяю код…"
            runRust({ RustBridge.nativeSubmitCode(value) })
        }
        passwordButton.setOnClickListener {
            val value = password.text.toString()
            if (value.isEmpty()) { status.text = "Введи пароль 2FA"; return@setOnClickListener }
            status.text = "Проверяю пароль…"
            runRust({ RustBridge.nativeSubmitPassword(value) })
        }
        dialogsButton.setOnClickListener {
            status.text = "Загружаю диалоги…"
            runRust({ RustBridge.nativeDialogs() }) { result ->
                showDialogs(result)
                openChats()
                status.text = "Диалоги загружены"
            }
        }
        refreshButton.setOnClickListener {
            status.text = "Обновляю диалоги…"
            runRust({ RustBridge.nativeDialogs() }) { result -> showDialogs(result); showScreen(dialogsScreen); status.text = "Диалоги обновлены" }
        }
        chatsNav.setOnClickListener { showScreen(dialogsScreen) }
        contactsNav.setOnClickListener { showScreen(contactsScreen) }
        settingsNav.setOnClickListener { showScreen(settingsScreen) }
        backButton.setOnClickListener { showScreen(dialogsScreen) }
        sendButton.setOnClickListener {
            if (selectedDialog < 0) { status.text = "Сначала выбери диалог"; return@setOnClickListener }
            val value = messageInput.text.toString().trim()
            if (value.isEmpty()) { status.text = "Введи сообщение"; return@setOnClickListener }
            status.text = "Отправляю…"
            runRust({ RustBridge.nativeSendMessage(selectedDialog, value) }) { result ->
                status.text = result
                if (!result.startsWith("Ошибка:")) { messageInput.text.clear(); loadMessages(selectedDialog) }
            }
        }
        profileButton.setOnClickListener { openProfile { target -> showScreen(target) } }

        // Анимированный фон находится под ScrollView, поэтому цветные «световые
        // пятна» медленно двигаются и просматриваются через полупрозрачные панели.
        val backdrop = AnimatedBackdropView(this)
        val content = FrameLayout(this).apply {
            setBackgroundColor(black)
            addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(content)
        authPanel.alpha = 0f
        authPanel.translationY = dp(10).toFloat()
        authPanel.post {
            authPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun showDialogs(result: String) {
        cachedDialogsResult = result
        renderDialogs(result, "")
    }

    private fun renderDialogs(result: String, query: String) {
        dialogs.removeAllViews()
        if (result.startsWith("Ошибка:")) { status.text = result; return }
        val rows = result.lines().filter { it.contains("|") }
            .filter { query.isBlank() || it.substringAfter("|").contains(query.trim(), ignoreCase = true) }
        if (rows.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isBlank()) "Диалогов пока нет" else "Ничего не найдено"
                textSize = 15f
                setTextColor(muted)
                setPadding(dp(8), dp(16), dp(8), dp(16))
            }
            dialogs.addView(empty)
            return
        }
        rows.forEachIndexed { rowIndex, row ->
            val parts = row.split("|", limit = 2)
            val index = parts[0].toIntOrNull() ?: return@forEachIndexed
            val title = parts.getOrNull(1)?.ifBlank { "Без названия" } ?: "Без названия"
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glass(surfaceSoft, 18)
                setPadding(dp(12), dp(9), dp(12), dp(9))
                isClickable = true
                isFocusable = true
            }
            item.addView(avatarView(title, 52))
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(4), 0)
            }
            val titleView = label(title, 16f)
            titleView.maxLines = 1
            titleView.ellipsize = android.text.TextUtils.TruncateAt.END
            details.addView(titleView)
            details.addView(label("Нажмите, чтобы открыть диалог", 12f, muted))
            item.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            item.setOnTouchListener { view, event -> pressAnimation(view, event) }
            item.setOnClickListener {
                selectedDialog = index
                selectedDialogTitle = title
                chatTitle.text = title
                chatScreen.visibility = View.VISIBLE
                dialogsScreen.visibility = View.GONE
                contactsScreen.visibility = View.GONE
                settingsScreen.visibility = View.GONE
                profileScreen.visibility = View.GONE
                chatScreen.alpha = 0f
                chatScreen.translationY = dp(12).toFloat()
                chatScreen.animate().alpha(1f).translationY(0f).setDuration(260).start()
                status.text = "Загружаю историю…"
                loadMessages(index)
            }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, dp(5), 0, dp(5))
            dialogs.addView(item, params)
            item.alpha = 0f
            item.translationY = dp(8).toFloat()
            item.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((rowIndex * 28L).coerceAtMost(180L))
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun loadMessages(index: Int) {
        runRust({ RustBridge.nativeMessages(index) }) { result ->
            messages.removeAllViews()
            if (result.startsWith("Ошибка:")) {
                addMessageBlock(result, outgoing = false, isError = true)
                status.text = result
                return@runRust
            }
            val rows = result.lines().filter { it.isNotBlank() }
            if (rows.isEmpty()) addMessageBlock("Сообщений пока нет", false, false)
            else rows.forEach { row ->
                val parts = row.split("\t", limit = 2)
                val outgoing = parts.firstOrNull() == "OUT"
                val value = parts.getOrNull(1) ?: row
                addMessageBlock(value.replace(Regex("^#[0-9]+[[:space:]]"), ""), outgoing, false)
            }
            status.text = "История загружена"
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addMessageBlock(value: String, outgoing: Boolean, isError: Boolean) {
        val block = TextView(this).apply {
            text = value
            textSize = 16f
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            setTextColor(white)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = glass(when {
                isError -> Color.rgb(62, 20, 30)
                outgoing -> purple
                else -> Color.rgb(17, 17, 22)
            }, 18)
        }
        val params = LinearLayout.LayoutParams(-2, -2)
        params.gravity = if (outgoing) Gravity.END else Gravity.START
        params.setMargins(0, dp(5), 0, dp(5))
        messages.addView(block, params)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "выбранный файл"
            messageInput.setText("📎 $name")
            messageInput.setSelection(messageInput.length())
            status.text = "Файл выбран. Отправка медиа через Rust API будет добавлена следующим этапом."
        }
    }

    override fun onDestroy() {
        try {
            voiceRecorder?.stop()
        } catch (_: RuntimeException) {
        }
        voiceRecorder?.release()
        voiceRecorder = null
        super.onDestroy()
    }

    private fun makeQrBitmap(value: String): Bitmap {
        val size = (resources.displayMetrics.widthPixels * 0.72f).toInt().coerceAtLeast(480)
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun runRust(call: () -> String, after: ((String) -> Unit)? = null) {
        Thread {
            val result = try { call() } catch (_: UnsatisfiedLinkError) { "Ошибка: Rust-библиотека не собрана" }
            runOnUiThread { if (after != null) after(result) else status.text = result }
        }.start()
    }
}
