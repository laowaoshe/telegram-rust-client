from pathlib import Path
import shutil
from datetime import datetime
source = Path('/data/telegram-rust-client-androidide-compatible/app/src/main/java/com/example/telegramrust/MainActivity.kt').read_text(encoding='utf-8')
target = Path('app/src/main/java/com/example/telegramrust/MainActivity.kt')
if not target.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')
backup = Path('backup-before-ui-stage2-' + datetime.now().strftime('%Y%m%d-%H%M%S')) / target
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(target, backup)
target.write_text(source, encoding='utf-8')
print('Интерфейс второго этапа обновлён')
print('Резервная копия:', backup)
