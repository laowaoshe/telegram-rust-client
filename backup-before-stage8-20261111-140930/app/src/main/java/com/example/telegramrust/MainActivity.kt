package com.example.telegramrust

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

class MainActivity : Activity() {
    private companion object {
        const val EMBEDDED_API_ID = 37506075
        const val EMBEDDED_API_HASH = "6fdf2220f3a9db5814221e8cbeed89b9"
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
    private lateinit var dialogs: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var chatTitle: TextView
    private lateinit var qrImage: ImageView
    private lateinit var scroll: ScrollView
    private var selectedDialog = -1

    private val black = Color.BLACK
    private val surface = Color.rgb(15, 15, 22)
    private val surfaceSoft = Color.rgb(24, 21, 38)
    private val purple = Color.rgb(79, 70, 229)
    private val purpleDark = Color.rgb(33, 26, 69)
    private val white = Color.rgb(245, 243, 255)
    private val muted = Color.rgb(139, 139, 152)
    private val outline = Color.rgb(48, 44, 72)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun glass(fill: Int = surface, radius: Int = 18): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(1), outline)
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
            setBackgroundColor(black)
            setPadding(padding, padding, padding, padding)
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(black)
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

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.rustgram_logo)
            adjustViewBounds = true
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(86)))
        root.addView(text("RustGram", 28f))
        root.addView(text("Чёрный Telegram-клиент на Rust", 15f, muted))
        status = text("Готово к подключению", 14f, muted)
        root.addView(status)
        addGap(root, 8)

        authPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        authPanel.addView(text("Подключение к аккаунту", 20f))
        authPanel.addView(text("QR-вход или код Telegram. Данные API хранятся только на устройстве.", 13f, muted))
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
        val dialogsButton = glassButton("Открыть RustGram", purple)

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
        val brand = text("RustGram", 19f)
        topBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val refreshButton = glassButton("Обновить", surfaceSoft)
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
        dialogsScreen.addView(text("Диалоги", 22f))
        dialogsScreen.addView(field("Поиск по чатам"))
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
        val backButton = glassButton("‹ Чаты", surfaceSoft)
        chatTitle = text("Чат", 20f)
        chatToolbar.addView(backButton)
        chatToolbar.addView(chatTitle, LinearLayout.LayoutParams(0, -2, 1f))
        chatScreen.addView(chatToolbar)
        addGap(chatScreen)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        chatScreen.addView(text("История", 16f, muted))
        chatScreen.addView(messages)
        messageInput = field("Сообщение")
        val sendButton = glassButton("Отправить", purple)
        addGap(chatScreen)
        chatScreen.addView(messageInput)
        addGap(chatScreen)
        chatScreen.addView(sendButton)

        contactsScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = glass(surface, 22)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        contactsScreen.addView(text("Контакты", 22f))
        contactsScreen.addView(text("Раздел контактов подготовлен для следующего этапа.", 15f, muted))

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

        screenHost.addView(dialogsScreen)
        screenHost.addView(chatScreen)
        screenHost.addView(contactsScreen)
        screenHost.addView(settingsScreen)
        chatPanel.addView(screenHost)
        root.addView(chatPanel)

        val sessionPath = File(filesDir, "telegram.session").absolutePath

        fun showScreen(screen: View) {
            dialogsScreen.visibility = if (screen === dialogsScreen) View.VISIBLE else View.GONE
            chatScreen.visibility = if (screen === chatScreen) View.VISIBLE else View.GONE
            contactsScreen.visibility = if (screen === contactsScreen) View.VISIBLE else View.GONE
            settingsScreen.visibility = if (screen === settingsScreen) View.VISIBLE else View.GONE
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

        setContentView(scroll)
    }

    private fun showDialogs(result: String) {
        dialogs.removeAllViews()
        if (result.startsWith("Ошибка:")) { status.text = result; return }
        val rows = result.lines().filter { it.contains("|") }
        if (rows.isEmpty()) { status.text = result; return }
        rows.forEach { row ->
            val parts = row.split("|", limit = 2)
            val index = parts[0].toIntOrNull() ?: return@forEach
            val title = parts.getOrNull(1)?.ifBlank { "Без названия" } ?: "Без названия"
            val item = glassButton("◉  $title", surfaceSoft)
            item.gravity = Gravity.START
            item.setOnClickListener {
                selectedDialog = index
                chatTitle.text = title
                chatScreen.visibility = View.VISIBLE
                dialogsScreen.visibility = View.GONE
                contactsScreen.visibility = View.GONE
                settingsScreen.visibility = View.GONE
                status.text = "Загружаю историю…"
                loadMessages(index)
            }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, dp(5), 0, dp(5))
            dialogs.addView(item, params)
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
