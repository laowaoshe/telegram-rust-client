package com.example.telegramrust

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var apiId: EditText
    private lateinit var apiHash: EditText
    private lateinit var phone: EditText
    private lateinit var code: EditText
    private lateinit var password: EditText
    private lateinit var requestButton: Button
    private lateinit var codeButton: Button
    private lateinit var passwordButton: Button
    private lateinit var dialogsButton: Button
    private lateinit var dialogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.TOP
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

        requestButton = Button(this).apply { text = "Получить код Telegram" }
        codeButton = Button(this).apply { text = "Войти по коду" }
        passwordButton = Button(this).apply { text = "Войти по паролю 2FA" }
        dialogsButton = Button(this).apply { text = "Показать диалоги" }
        status = text("Готово к подключению", 16f)
        dialogs = text("", 15f)

        root.addView(apiId)
        root.addView(apiHash)
        root.addView(phone)
        root.addView(requestButton)
        root.addView(code)
        root.addView(codeButton)
        root.addView(password)
        root.addView(passwordButton)
        root.addView(dialogsButton)
        root.addView(status)
        root.addView(dialogs)

        requestButton.setOnClickListener {
            val id = apiId.text.toString().toIntOrNull()
            val hash = apiHash.text.toString().trim()
            val phoneValue = phone.text.toString().trim()
            if (id == null || hash.isEmpty() || phoneValue.isEmpty()) {
                status.text = "Заполни API ID, API hash и номер телефона"
                return@setOnClickListener
            }
            status.text = "Запрашиваю код…"
            runRust({ RustBridge.nativeRequestCode(id, hash, phoneValue, File(filesDir, "telegram.session").absolutePath) })
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
                dialogs.text = result
                status.text = "Диалоги загружены"
            })
        }

        setContentView(root)
    }

    private fun runRust(call: () -> String, after: ((String) -> Unit)? = null) {
        Thread {
            val result = try { call() } catch (e: UnsatisfiedLinkError) { "Ошибка: Rust-библиотека не собрана" }
            runOnUiThread {
                if (after != null) after(result) else status.text = result
            }
        }.start()
    }
}
