from pathlib import Path
from datetime import datetime
import shutil

root = Path.cwd()
rust = root / 'rust/src/lib.rs'
if not rust.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')
s = rust.read_text(encoding='utf-8')
if '.limit(30)' not in s and 'lines.len() >= 30' not in s:
    raise SystemExit('Ограничение списка уже изменено или участок кода не найден')
backup = root / ('backup-before-stage5-' + datetime.now().strftime('%Y%m%d-%H%M%S')) / rust.relative_to(root)
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(rust, backup)
s = s.replace('.limit(30)', '.limit(100)', 1)
s = s.replace('            if lines.len() >= 30 {\n                break;\n            }\n', '', 1)
rust.write_text(s, encoding='utf-8')
print('Stage 5 применён: до 100 последних сообщений и все доступные диалоги')
print('Резервная копия:', backup.parent)
