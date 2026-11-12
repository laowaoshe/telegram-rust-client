Stage 12 — TDLib backend через Rust FFI

Этот этап заменяет Telegram backend grammers на TDLib. Kotlin по-прежнему отвечает только за UI/JNI, а авторизация, чаты, история и отправка проходят через Rust.

Добавлено:
- Rust FFI к td_json_client_*.
- TDLib JSON строится официальным скриптом под arm64-v8a, armeabi-v7a и x86_64.
- Rust build.rs выбирает библиотеку TDLib по Android ABI.
- Авторизация по номеру, коду, 2FA и базовый QR-запрос.
- Получение списка диалогов и последних сообщений.
- Пагинация истории через getChatHistory.
- Отправка текста и файлов.
- Поиск публичного чата по username.
- Отдельная база TDLib в files/tdlib.
- Workflow использует ubuntu-22.04 и собирает libtdjson.so, OpenSSL и libc++_shared.so.

Как работает сборка:
1. GitHub Actions скачивает исходники TDLib и собирает их под Android.
2. cargo-ndk компилирует Rust и линкует libtdjson для каждого ABI.
3. Gradle собирает APK вместе с TDLib.

Важно:
- Первая сборка с TDLib может идти заметно дольше обычной, потому что компилируются TDLib и OpenSSL.
- Локально Gradle и cargo-ndk в Termux не запускаем: нужен GitHub Actions.
- Формат сессии TDLib не совместим с grammers, поэтому авторизацию нужно пройти заново.
- TDLib не заменяет UI: темы, анимации и обои остаются в Android-интерфейсе.
- Настоящие API ID и API hash не следует хранить в публичном репозитории. После проверки лучше вынести их в GitHub Secrets и удалить секреты из Git-истории.
