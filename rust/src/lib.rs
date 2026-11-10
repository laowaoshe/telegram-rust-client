use std::sync::{Arc, Mutex, OnceLock};

use grammers_client::{Client, LoginToken, PasswordToken, SignInError};
use grammers_mtsender::SenderPool;
use grammers_session::storages::SqliteSession;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use tokio::runtime::{Builder, Runtime};

struct Engine {
    runtime: Runtime,
    client: Client,
    login_token: Option<LoginToken>,
    password_token: Option<PasswordToken>,
}

static ENGINE: OnceLock<Mutex<Option<Engine>>> = OnceLock::new();

fn engine_slot() -> &'static Mutex<Option<Engine>> {
    ENGINE.get_or_init(|| Mutex::new(None))
}

fn ok(message: impl Into<String>) -> String {
    message.into()
}

fn err(message: impl Into<String>) -> String {
    format!("Ошибка: {}", message.into())
}

fn make_runtime() -> Result<Runtime, String> {
    Builder::new_multi_thread()
        .enable_all()
        .build()
        .map_err(|e| e.to_string())
}

fn start_login(api_id: i32, api_hash: String, phone: String, session_path: String) -> String {
    let runtime = match make_runtime() {
        Ok(value) => value,
        Err(e) => return err(format!("не удалось запустить Rust runtime: {e}")),
    };

    let result = runtime.block_on(async {
        let session = Arc::new(
            SqliteSession::open(&session_path)
                .await
                .map_err(|e| e.to_string())?,
        );
        let grammers_mtsender::SenderPool { runner, handle, .. } =
            SenderPool::new(Arc::clone(&session), api_id);
        let client = Client::new(handle);
        tokio::spawn(runner.run());

        if client.is_authorized().await.map_err(|e| e.to_string())? {
            return Ok::<_, String>((client, None));
        }

        let token = client
            .request_login_code(&phone, &api_hash)
            .await
            .map_err(|e| e.to_string())?;
        Ok((client, Some(token)))
    });

    match result {
        Ok((client, token)) => {
            let mut slot = match engine_slot().lock() {
                Ok(value) => value,
                Err(_) => return err("внутреннее состояние Rust повреждено"),
            };
            *slot = Some(Engine {
                runtime,
                client,
                login_token: token,
                password_token: None,
            });
            if slot.as_ref().and_then(|e| e.login_token.as_ref()).is_some() {
                ok("Код отправлен в Telegram. Введи код из Telegram или SMS.")
            } else {
                ok("Сессия уже авторизована. Можно загрузить диалоги.")
            }
        }
        Err(e) => err(e),
    }
}

fn submit_code(code: String) -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала нажми «Получить код»"),
    };
    let token = match engine.login_token.as_ref() {
        Some(value) => value,
        None => return ok("Сессия уже авторизована или код ещё не нужен."),
    };

    match engine.runtime.block_on(engine.client.sign_in(token, code.trim())) {
        Ok(user) => {
            engine.login_token = None;
            ok(format!("Вход выполнен: {}", user.first_name().unwrap_or("пользователь")))
        }
        Err(SignInError::PasswordRequired(password_token)) => {
            engine.password_token = Some(password_token);
            ok("Нужен пароль двухэтапной проверки Telegram.")
        }
        Err(SignInError::InvalidCode) => err("неверный код. Проверь код и попробуй ещё раз"),
        Err(SignInError::SignUpRequired) => err("этот номер ещё не зарегистрирован в Telegram"),
        Err(e) => err(e.to_string()),
    }
}

fn submit_password(password: String) -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала запроси код"),
    };
    let token = match engine.password_token.take() {
        Some(value) => value,
        None => return err("пароль сейчас не требуется"),
    };

    match engine
        .runtime
        .block_on(engine.client.check_password(token, password))
    {
        Ok(user) => {
            engine.login_token = None;
            ok(format!("Вход выполнен: {}", user.first_name().unwrap_or("пользователь")))
        }
        Err(SignInError::InvalidPassword(_)) => err("неверный пароль Telegram"),
        Err(e) => err(e.to_string()),
    }
}

fn dialogs() -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };

    let result = engine.runtime.block_on(async {
        let mut dialogs = engine.client.iter_dialogs();
        let mut lines = Vec::new();
        while let Some(dialog) = dialogs.next().await.map_err(|e| e.to_string())? {
            let peer = dialog.peer();
            lines.push(format!("• {}", peer.name().unwrap_or("Без названия")));
            if lines.len() >= 30 {
                break;
            }
        }
        Ok::<_, String>(lines)
    });

    match result {
        Ok(lines) if lines.is_empty() => ok("Диалогов пока не найдено."),
        Ok(lines) => lines.join("\n"),
        Err(e) => err(e),
    }
}

fn jstring(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value).unwrap().into_raw()
}

fn take_string(env: &mut JNIEnv, value: JString) -> Result<String, String> {
    env.get_string(&value)
        .map(|s| s.to_string_lossy().into_owned())
        .map_err(|e| e.to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeBuildInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstring(&mut env, ok("Rust-ядро подключено успешно"))
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeRequestCode(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    api_hash: JString,
    phone: JString,
    session_path: JString,
) -> jstring {
    let values = (
        take_string(&mut env, api_hash),
        take_string(&mut env, phone),
        take_string(&mut env, session_path),
    );
    let value = match values {
        (Ok(api_hash), Ok(phone), Ok(session_path)) if api_id > 0 && !api_hash.is_empty() && !phone.is_empty() => {
            start_login(api_id, api_hash, phone, session_path)
        }
        _ => err("проверь API ID, API hash и номер телефона"),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSubmitCode(
    mut env: JNIEnv,
    _class: JClass,
    code: JString,
) -> jstring {
    let value = match take_string(&mut env, code) {
        Ok(code) => submit_code(code),
        Err(e) => err(e),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSubmitPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
) -> jstring {
    let value = match take_string(&mut env, password) {
        Ok(password) => submit_password(password),
        Err(e) => err(e),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeDialogs(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstring(&mut env, dialogs())
}
