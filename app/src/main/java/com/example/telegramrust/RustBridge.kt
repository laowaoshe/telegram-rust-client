package com.example.telegramrust

object RustBridge {
    init {
        try { System.loadLibrary("telegram_core") } catch (_: UnsatisfiedLinkError) { }
    }

    external fun nativeBuildInfo(): String
}
