#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import shutil

path = Path('rust/src/lib.rs')
if not path.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')
text = path.read_text(encoding='utf-8')
old = '''fn with_dialog<F, Fut>(engine: &mut Engine, index: usize, action: F) -> Result<String, String>
where
    F: FnOnce(grammers_session::types::PeerRef, Client) -> Fut,
    Fut: std::future::Future<Output = Result<String, String>>,
{'''
new = '''fn with_dialog<F, Fut, T>(engine: &mut Engine, index: usize, action: F) -> Result<T, String>
where
    F: FnOnce(grammers_session::types::PeerRef, Client) -> Fut,
    Fut: std::future::Future<Output = Result<T, String>>,
{'''
if old not in text:
    if 'fn with_dialog<F, Fut, T>' in text:
        print('Исправление уже применено.')
        raise SystemExit(0)
    raise SystemExit('Не найден ожидаемый фрагмент with_dialog')
backup = path.with_name('lib.rs.backup-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
shutil.copy2(path, backup)
path.write_text(text.replace(old, new), encoding='utf-8')
print('Исправлено:', path)
print('Резервная копия:', backup)
