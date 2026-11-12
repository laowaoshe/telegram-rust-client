package com.example.telegramrust

object RustBridge {
    init {
        try { System.loadLibrary("telegram_core") } catch (_: UnsatisfiedLinkError) { }
    }

    external fun nativeBuildInfo(): String
    external fun nativeRequestCode(apiId: Int, apiHash: String, phone: String, sessionPath: String): String
    external fun nativeRequestQr(apiId: Int, apiHash: String, sessionPath: String): String
    external fun nativeCheckQr(): String
    external fun nativeSubmitCode(code: String): String
    external fun nativeSubmitPassword(password: String): String
    external fun nativeDialogs(): String
    external fun nativeSearchUsername(query: String): String
    external fun nativeMessages(index: Int, offsetId: Int, limit: Int): String
    external fun nativeSendMessage(index: Int, text: String): String
    external fun nativeSendFile(index: Int, path: String, caption: String): String
}
