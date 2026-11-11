package com.example.telegramrust

import android.app.Activity
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var dialogs: LinearLayout
    private lateinit var messages: TextView
    private lateinit var messageInput: EditText
    private lateinit var qrImage: ImageView
    private var selectedDialog = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.TOP
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(root)
        }

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
        root.addView(text("Подключение к Telegram через Rust MTProto", 16f))
        root.addView(text("API ID и API hash бери только на my.telegram.org. Не отправляй их в чат.", 13f))

        apiId = field("API ID", InputType.TYPE_CLASS_NUMBER)
        apiHash = field("API hash")
        phone = field("Номер телефона, например +380...", InputType.TYPE_CLASS_PHONE)
        code = field("Код из Telegram или SMS", InputType.TYPE_CLASS_NUMBER)
        password = field("Пароль двухэтапной проверки Telegram", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        val requestButton = Button(this).apply { text = "Получить код Telegram" }
        val qrButton = Button(this).apply { text = "Показать QR-код" }
        val qrCheckButton = Button(this).apply { text = "Проверить QR-вход" }
        val codeButton = Button(this).apply { text = "Войти по коду" }
        val passwordButton = Button(this).apply { text = "Войти по паролю 2FA" }
        val dialogsButton = Button(this).apply { text = "Показать диалоги" }
        val chatTitle = text("История диалога", 18f)
        dialogs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        messages = text("Выбери диалог выше", 15f)
        messages.setTextIsSelectable(true)
        messageInput = field("Сообщение")
        val sendButton = Button(this).apply { text = "Отправить" }
        qrImage = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(12, 12, 12, 12)
            visibility = View.GONE
        }
        status = text("Готово к подключению", 16f)

        root.addView(apiId)
        root.addView(apiHash)
        root.addView(phone)
        root.addView(requestButton)
        root.addView(qrButton)
        root.addView(qrCheckButton)
        root.addView(code)
        root.addView(codeButton)
        root.addView(password)
        root.addView(passwordButton)
        root.addView(dialogsButton)
        root.addView(status)
        root.addView(dialogs)
        root.addView(chatTitle)
        root.addView(messages)
        root.addView(messageInput)
        root.addView(sendButton)
        root.addView(qrImage)

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
                        status.text = "Открой Telegram на другом устройстве: Настройки → Устройства → Сканировать QR-код. Наведи камеру на этот QR-код, затем нажми «Проверить QR-вход»."
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
            runRust({ RustBridge.nativeDialogs() }, { result ->
                showDialogs(result)
            })
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
                    status.text = "Загружаю историю…"
                    loadMessages(index)
                }
            }
            dialogs.addView(button)
        }
        status.text = "Диалоги загружены. Выбери один."
    }

    private fun loadMessages(index: Int) {
        runRust({ RustBridge.nativeMessages(index) }) { result ->
            messages.text = result
            status.text = if (result.startsWith("Ошибка:")) result else "История загружена"
        }
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
