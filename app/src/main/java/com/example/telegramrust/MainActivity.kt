package com.example.telegramrust

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.TOP
        }

        fun text(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.DKGRAY)
        }

        root.addView(text("Telegram Rust Client", 24f))
        root.addView(text("Первая версия: подготовка подключения к Telegram", 16f))

        val apiId = EditText(this).apply { hint = "API ID"; inputType = 2 }
        val apiHash = EditText(this).apply { hint = "API hash" }
        val status = text("Rust-ядро ещё не подключено", 16f)
        val button = Button(this).apply { text = "Проверить Rust" }

        root.addView(apiId)
        root.addView(apiHash)
        root.addView(button)
        root.addView(status)
        root.addView(text("Данные пока не отправляются и не сохраняются.", 14f))

        button.setOnClickListener {
            status.text = if (apiId.text.isNullOrBlank() || apiHash.text.isNullOrBlank()) {
                "Заполни API ID и API hash"
            } else {
                try { RustBridge.nativeBuildInfo() }
                catch (_: UnsatisfiedLinkError) { "Rust-библиотека пока не собрана" }
            }
        }

        setContentView(root)
    }
}
