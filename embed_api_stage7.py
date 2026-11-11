from pathlib import Path
from datetime import datetime
import getpass
import shutil

root = Path.cwd()
target = root / 'app/src/main/java/com/example/telegramrust/MainActivity.kt'
if not target.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')

api_id_text = input('Введи API ID локально: ').strip()
api_hash = getpass.getpass('Введи API hash локально (на экране не показывается): ').strip()
if not api_id_text.isdigit() or not api_hash:
    raise SystemExit('API ID должен быть числом, а API hash — непустым')
api_id = int(api_id_text)

s = target.read_text(encoding='utf-8')
if 'EMBEDDED_API_ID' in s:
    raise SystemExit('API ID и API hash уже встроены в MainActivity.kt')

backup_dir = root / ('backup-before-api-embed-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
backup = backup_dir / target.relative_to(root)
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(target, backup)

escaped_hash = api_hash.replace('\\', '\\\\').replace('"', '\\"')
constants = (
    f'    private companion object {{\n'
    f'        const val EMBEDDED_API_ID = {api_id}\n'
    f'        const val EMBEDDED_API_HASH = "{escaped_hash}"\n'
    f'    }}\n\n'
)
s = s.replace('class MainActivity : Activity() {\n', 'class MainActivity : Activity() {\n' + constants, 1)
s = s.replace('val id = apiId.text.toString().toIntOrNull()', 'val id = EMBEDDED_API_ID')
s = s.replace('val hash = apiHash.text.toString().trim()', 'val hash = EMBEDDED_API_HASH')
needle = 'password = field("Пароль двухэтапной проверки Telegram", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)'
replacement = needle + '\n        apiId.visibility = View.GONE\n        apiHash.visibility = View.GONE'
s = s.replace(needle, replacement, 1)
target.write_text(s, encoding='utf-8')
print('Готово: API ID и API hash встроены в APK, поля скрыты')
print('Резервная копия:', backup)
