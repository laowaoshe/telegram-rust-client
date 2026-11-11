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
    private lateinit var status: TextView
    private lateinit var apiId: EditText
    private lateinit var apiHash: EditText
    private lateinit var phone: EditText
    private lateinit var code: EditText
    private lateinit var password: EditText
    private lateinit var authPanel: LinearLayout
    private lateinit var chatPanel: LinearLayout
    private lateinit var dialogs: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var chatTitle: TextView
    private lateinit var qrImage: ImageView
    private lateinit var scroll: ScrollView
    private var selectedDialog = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.TOP
        }
        scroll = ScrollView(this).apply { addView(root) }

        fun text(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 8)
        }

        fun field(hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            hint = hintText
            inputType = type
        }

        root.addView(text("Telegram Rust Client", 24f))
        status = text("Готово к подключению", 16f)
        root.addView(status)

        authPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        authPanel.addView(text("Подключение через Rust MTProto", 16f))
        authPanel.addView(text("API ID и API hash бери только на my.telegram.org. Не отправляй их в чат.", 13f))

        apiId = field("API ID", InputType.TYPE_CLASS_NUMBER)
        apiHash = field("API hash")
        phone = field("Номер телефона, например +380...", InputType.TYPE_CLASS_PHONE)
        code = field("Код из Telegram или SMS", InputType.TYPE_CLASS_NUMBER)
        password = field(
            "Пароль двухэтапной проверки Telegram",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )

        val requestButton = Button(this).apply { text = "Получить код Telegram" }
        val qrButton = Button(this).apply { text = "Показать QR-код" }
        val qrCheckButton = Button(this).apply { text = "Проверить QR-вход" }
        val codeButton = Button(this).apply { text = "Войти по коду" }
        val passwordButton = Button(this).apply { text = "Войти по паролю 2FA" }
        val dialogsButton = Button(this).apply { text = "Открыть диалоги" }

        qrImage = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(12, 12, 12, 12)
            visibility = View.GONE
        }

        authPanel.addView(apiId)
        authPanel.addView(apiHash)
        authPanel.addView(phone)
        authPanel.addView(requestButton)
        authPanel.addView(qrButton)
        authPanel.addView(qrCheckButton)
        authPanel.addView(code)
        authPanel.addView(codeButton)
        authPanel.addView(password)
        authPanel.addView(passwordButton)
        authPanel.addView(dialogsButton)
        authPanel.addView(qrImage)
        root.addView(authPanel)

        chatPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val chatToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backButton = Button(this).apply { text = "← Диалоги" }
        val refreshButton = Button(this).apply { text = "Обновить" }
        chatTitle = text("Чаты", 20f)
        chatToolbar.addView(backButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        chatToolbar.addView(refreshButton)
        chatPanel.addView(chatToolbar)
        chatPanel.addView(chatTitle)

        dialogs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatPanel.addView(text("Диалоги", 18f))
        chatPanel.addView(dialogs)

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        chatPanel.addView(text("Сообщения", 18f))
        chatPanel.addView(messages)

        messageInput = field("Сообщение")
        val sendButton = Button(this).apply { text = "Отправить" }
        chatPanel.addView(messageInput)
        chatPanel.addView(sendButton)
        root.addView(chatPanel)

        val sessionPath = File(filesDir, "telegram.session").absolutePath

        requestButton.setOnClickListener {
            val id = apiId.text.toString().toIntOrNull()
            val hash = apiHash.text.toString().trim()
            val phoneValue = phone.text.toString().trim()
            if (id == null || hash.isEmpty() || phoneValue.isEmpty()) {
                status.text = "Заполни API ID, API hash и номер телефона"
                return@setOnClickListener
            }
            status.text = "Запрашиваю код…"
            runRust({ RustBridge.nativeRequestCode(id, hash, phoneValue, sessionPath) })
        }

        qrButton.setOnClickListener {
            val id = apiId.text.toString().toIntOrNull()
            val hash = apiHash.text.toString().trim()
            if (id == null || hash.isEmpty()) {
                status.text = "Заполни API ID и API hash"
                return@setOnClickListener
            }
            status.text = "Создаю ссылку входа…"
            runRust({ RustBridge.nativeRequestQr(id, hash, sessionPath) }) { result ->
                if (result.startsWith("tg://login?")) {
                    try {
                        qrImage.setImageBitmap(makeQrBitmap(result))
                        qrImage.visibility = View.VISIBLE
                        status.text = "Отсканируй QR в Telegram, затем нажми «Проверить QR-вход»."
                    } catch (_: Exception) {
                        status.text = "Не удалось создать QR-код"
                    }
                } else {
                    status.text = result
                }
            }
        }

        qrCheckButton.setOnClickListener {
            status.text = "Проверяю QR-вход…"
            runRust({ RustBridge.nativeCheckQr() })
        }

        codeButton.setOnClickListener {
            val value = code.text.toString().trim()
            if (value.isEmpty()) {
                status.text = "Введи код из Telegram или SMS"
                return@setOnClickListener
            }
            status.text = "Проверяю код…"
            runRust({ RustBridge.nativeSubmitCode(value) })
        }

        passwordButton.setOnClickListener {
            val value = password.text.toString()
            if (value.isEmpty()) {
                status.text = "Введи пароль 2FA, если Telegram его запросил"
                return@setOnClickListener
            }
            status.text = "Проверяю пароль…"
            runRust({ RustBridge.nativeSubmitPassword(value) })
        }

        dialogsButton.setOnClickListener {
            status.text = "Загружаю диалоги…"
            runRust({ RustBridge.nativeDialogs() }) { result -> showChat(result) }
        }
        refreshButton.setOnClickListener {
            status.text = "Обновляю диалоги…"
            runRust({ RustBridge.nativeDialogs() }) { result -> showDialogs(result) }
        }
        backButton.setOnClickListener {
            chatPanel.visibility = View.GONE
            authPanel.visibility = View.VISIBLE
            status.text = "Возврат к подключению"
        }
        sendButton.setOnClickListener {
            if (selectedDialog < 0) {
                status.text = "Сначала выбери диалог"
                return@setOnClickListener
            }
            val value = messageInput.text.toString().trim()
            if (value.isEmpty()) {
                status.text = "Введи сообщение"
                return@setOnClickListener
            }
            status.text = "Отправляю…"
            runRust({ RustBridge.nativeSendMessage(selectedDialog, value) }) { result ->
                status.text = result
                if (!result.startsWith("Ошибка:")) {
                    messageInput.text.clear()
                    loadMessages(selectedDialog)
                }
            }
        }

        setContentView(scroll)
    }

    private fun showChat(result: String) {
        if (result.startsWith("Ошибка:")) {
            status.text = result
            return
        }
        showDialogs(result)
        authPanel.visibility = View.GONE
        chatPanel.visibility = View.VISIBLE
        status.text = "Диалоги загружены. Выбери чат."
    }

    private fun showDialogs(result: String) {
        dialogs.removeAllViews()
        if (result.startsWith("Ошибка:")) {
            status.text = result
            return
        }
        val rows = result.lines().filter { it.contains("|") }
        if (rows.isEmpty()) {
            status.text = result
            return
        }
        rows.forEach { row ->
            val parts = row.split("|", limit = 2)
            val index = parts[0].toIntOrNull() ?: return@forEach
            val title = parts.getOrNull(1)?.ifBlank { "Без названия" } ?: "Без названия"
            val button = Button(this).apply {
                text = title
                setOnClickListener {
                    selectedDialog = index
                    chatTitle.text = title
                    status.text = "Загружаю историю…"
                    loadMessages(index)
                }
            }
            dialogs.addView(button)
        }
    }

    private fun loadMessages(index: Int) {
        runRust({ RustBridge.nativeMessages(index) }) { result ->
            messages.removeAllViews()
            if (result.startsWith("Ошибка:")) {
                addMessageBlock(result, true)
                status.text = result
                return@runRust
            }
            val rows = result.lines().filter { it.isNotBlank() }
            if (rows.isEmpty()) {
                addMessageBlock("Сообщений пока нет", false)
            } else {
                rows.forEach { row ->
                    addMessageBlock(row.replace(Regex("^#\\d+\\s"), ""), false)
                }
            }
            status.text = "История загружена"
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addMessageBlock(value: String, isError: Boolean) {
        val block = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(if (isError) Color.rgb(150, 0, 0) else Color.DKGRAY)
            setPadding(18, 12, 18, 12)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(if (isError) Color.rgb(255, 235, 235) else Color.rgb(238, 242, 247))
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        params.setMargins(0, 4, 0, 4)
        messages.addView(block, params)
    }

    private fun makeQrBitmap(value: String): Bitmap {
        val size = (resources.displayMetrics.widthPixels * 0.72f).toInt().coerceAtLeast(480)
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun runRust(call: () -> String, after: ((String) -> Unit)? = null) {
        Thread {
            val result = try { call() } catch (_: UnsatisfiedLinkError) { "Ошибка: Rust-библиотека не собрана" }
            runOnUiThread {
                if (after != null) after(result) else status.text = result
            }
        }.start()
    }
}
