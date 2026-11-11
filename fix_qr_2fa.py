#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import shutil

path = Path('rust/src/lib.rs')
if not path.exists():
    raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')
text = path.read_text(encoding='utf-8')
if 'Err(e) if e.is("SESSION_PASSWORD_NEEDED")' in text:
    print('Обработка QR 2FA уже добавлена.')
    raise SystemExit(0)
old = '''        Ok(tl::enums::auth::LoginToken::MigrateTo(_)) => {
            err("Telegram попросил другой сервер. Создай новый QR.")
        }
        Err(e) => err(e.to_string()),
    }
}

fn submit_code'''
new = '''        Ok(tl::enums::auth::LoginToken::MigrateTo(_)) => {
            err("Telegram попросил другой сервер. Создай новый QR.")
        }
        Err(e) if e.is("SESSION_PASSWORD_NEEDED") => {
            let password_result = engine.runtime.block_on(engine.client.invoke(
                &tl::functions::account::GetPassword {},
            ));
            match password_result {
                Ok(password) => {
                    let password: tl::types::account::Password = password.into();
                    engine.password_token = Some(PasswordToken::new(password));
                    ok("QR подтверждён. Введи пароль 2FA и нажми «Войти по паролю 2FA».")
                }
                Err(password_error) => err(format!("Не удалось получить данные 2FA: {password_error}")),
            }
        }
        Err(e) => err(e.to_string()),
    }
}

fn submit_code'''
if old not in text:
    raise SystemExit('Нужный блок QR не найден')
backup = path.with_name('lib.rs.backup-qr-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
shutil.copy2(path, backup)
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Обработка QR 2FA добавлена')
print('Резервная копия:', backup)
