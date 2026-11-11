from pathlib import Path
from datetime import datetime
import shutil
import re

root = Path.cwd()
main = root / 'app/src/main/java/com/example/telegramrust/MainActivity.kt'
rust = root / 'rust/src/lib.rs'
if not main.exists() or not rust.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')

main_text = main.read_text(encoding='utf-8')
rust_text = rust.read_text(encoding='utf-8')
if 'private fun addMessageBlock' not in main_text or 'private lateinit var messages: LinearLayout' not in main_text:
    raise SystemExit('Нужна версия проекта после Stage 2: в ней сообщения уже должны быть LinearLayout-блоками')
if 'message.outgoing()' not in rust_text:
    if 'lines.push(format!("#{} {}", message.id(), text));' not in rust_text:
        raise SystemExit('Не найден ожидаемый участок Rust-кода истории сообщений')

backup = root / ('backup-before-stage4-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
for target in (main, rust):
    dst = backup / target.relative_to(root)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target, dst)

# Rust: передаём направление сообщения вместе с текстом.
rust_text = rust_text.replace(
    'lines.push(format!("#{} {}", message.id(), text));',
    'let direction = if message.outgoing() { "OUT" } else { "IN" };\n                lines.push(format!("{}\\t#{} {}", direction, message.id(), text));',
)
rust.write_text(rust_text, encoding='utf-8')

# Android: заменяем вывод истории на настоящие пузырьки слева/справа.
load_pattern = re.compile(r'    private fun loadMessages\(index: Int\) \{.*?\n    \}\n\n    private fun addMessageBlock', re.S)
load_replacement = '''    private fun loadMessages(index: Int) {
        runRust({ RustBridge.nativeMessages(index) }) { result ->
            messages.removeAllViews()
            if (result.startsWith("Ошибка:")) {
                addMessageBlock(result, outgoing = false, isError = true)
                status.text = result
                return@runRust
            }
            val rows = result.lines().filter { it.isNotBlank() }
            if (rows.isEmpty()) {
                addMessageBlock("Сообщений пока нет", outgoing = false, isError = false)
            } else {
                rows.forEach { row ->
                    val parts = row.split("\\t", limit = 2)
                    val outgoing = parts.firstOrNull() == "OUT"
                    val value = parts.getOrNull(1) ?: row
                    addMessageBlock(value.replace(Regex("^#\\\\d+\\\\s"), ""), outgoing, false)
                }
            }
            status.text = "История загружена"
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addMessageBlock'''
new_main, count = load_pattern.subn(load_replacement, main_text, count=1)
if count != 1:
    raise SystemExit('Не удалось найти функцию loadMessages')

block_pattern = re.compile(r'    private fun addMessageBlock\(.*?\n    \}\n\n    private fun makeQrBitmap', re.S)
block_replacement = '''    private fun addMessageBlock(value: String, outgoing: Boolean, isError: Boolean) {
        val block = TextView(this).apply {
            text = value
            textSize = 16f
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            setTextColor(Color.WHITE)
            setPadding(20, 13, 20, 13)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                val fill = when {
                    isError -> Color.rgb(62, 20, 30)
                    outgoing -> Color.rgb(79, 70, 229)
                    else -> Color.rgb(17, 17, 22)
                }
                setColor(fill)
                if (!outgoing && !isError) {
                    setStroke(1, Color.rgb(45, 42, 64))
                }
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        params.gravity = if (outgoing) Gravity.END else Gravity.START
        params.setMargins(0, 5, 0, 5)
        messages.addView(block, params)
    }

    private fun makeQrBitmap'''
new_main, count = block_pattern.subn(block_replacement, new_main, count=1)
if count != 1:
    raise SystemExit('Не удалось найти функцию addMessageBlock')
main.write_text(new_main, encoding='utf-8')
print('Stage 4 применён: свои сообщения справа фиолетовые, чужие слева чёрные')
print('Резервная копия:', backup)
