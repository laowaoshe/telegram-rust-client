Stage 6 — Glass-интерфейс и разделы

Скрипт обновляет MainActivity на отдельные разделы:
- Чаты
- Контакты
- Настройки
- отдельный экран выбранного чата

Также добавляет чёрно-фиолетовый Glass-дизайн и сохраняет QR/2FA/отправку сообщений.
Скрипт создаёт резервную копию старого MainActivity.kt.

Запускать из корня проекта:
python3 update_stage6.py

После этого:
git add .
git commit -m "Add Glass UI sections and separate chat screen"
git push
