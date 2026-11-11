Stage 7 — встроенные API ID и API hash

Скрипт попросит API ID и API hash локально в Termux.
API hash вводится скрыто и не отправляется в чат.
После запуска значения будут встроены в MainActivity.kt, а поля API ID/API hash скроются из приложения.

Запускать из корня проекта:
python3 embed_api_stage7.py

После этого:
git add .
git commit -m "Embed Telegram API credentials in app"
git push

Важно: скрипт не сохраняет значения в отдельный файл, но они попадут в APK как часть приложения.
Не сохраняй скрипт с введёнными значениями в публичный репозиторий.
