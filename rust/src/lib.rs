use std::sync::{Arc, Mutex, OnceLock};

use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as Base64Engine};
use grammers_client::client::{LoginToken, PasswordToken};
use grammers_client::message::InputMessage;
use grammers_client::tl;
use grammers_client::{Client, SignInError};
use grammers_mtsender::SenderPool;
use grammers_session::storages::SqliteSession;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use tokio::runtime::{Builder, Runtime};

struct Engine {
    api_id: i32,
    api_hash: String,
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

fn create_client(runtime: &Runtime, api_id: i32, session_path: &str) -> Result<Client, String> {
    runtime.block_on(async {
        let session = Arc::new(
            SqliteSession::open(session_path)
                .await
                .map_err(|e| e.to_string())?,
        );
        let grammers_mtsender::SenderPool { runner, handle, .. } =
            SenderPool::new(Arc::clone(&session), api_id);
        let client = Client::new(handle);
        tokio::spawn(runner.run());
        Ok::<_, String>(client)
    })
}

fn new_engine(api_id: i32, api_hash: String, session_path: String) -> Result<Engine, String> {
    let runtime = make_runtime()?;
    let client = create_client(&runtime, api_id, &session_path)?;
    Ok(Engine {
        api_id,
        api_hash,
        runtime,
        client,
        login_token: None,
        password_token: None,
    })
}

fn start_login(api_id: i32, api_hash: String, phone: String, session_path: String) -> String {
    let runtime = match make_runtime() {
        Ok(value) => value,
        Err(e) => return err(format!("не удалось запустить Rust runtime: {e}")),
    };
    let client = match create_client(&runtime, api_id, &session_path) {
        Ok(value) => value,
        Err(e) => return err(e),
    };

    let result = runtime.block_on(async {
        if client.is_authorized().await.map_err(|e| e.to_string())? {
            return Ok::<_, String>((client.clone(), None));
        }
        let token = client
            .request_login_code(&phone, &api_hash)
            .await
            .map_err(|e| e.to_string())?;
        Ok((client.clone(), Some(token)))
    });

    match result {
        Ok((client, token)) => {
            let mut slot = match engine_slot().lock() {
                Ok(value) => value,
                Err(_) => return err("внутреннее состояние Rust повреждено"),
            };
            *slot = Some(Engine {
                api_id,
                api_hash,
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

fn export_qr_link(engine: &mut Engine) -> String {
    let result = engine.runtime.block_on(engine.client.invoke(
        &tl::functions::auth::ExportLoginToken {
            api_id: engine.api_id,
            api_hash: engine.api_hash.clone(),
            except_ids: Vec::new(),
        },
    ));

    match result {
        Ok(tl::enums::auth::LoginToken::Token(token)) => {
            let encoded = URL_SAFE_NO_PAD.encode(token.token);
            format!("tg://login?token={encoded}")
        }
        Ok(tl::enums::auth::LoginToken::MigrateTo(_)) => {
            err("Telegram попросил другой сервер. Повтори QR-вход ещё раз.")
        }
        Ok(tl::enums::auth::LoginToken::Success(_)) => {
            ok("QR уже подтверждён. Нажми «Проверить QR».")
        }
        Err(e) => err(e.to_string()),
    }
}

fn request_qr(api_id: i32, api_hash: String, session_path: String) -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };

    if slot.is_none() {
        *slot = match new_engine(api_id, api_hash, session_path) {
            Ok(value) => Some(value),
            Err(e) => return err(e),
        };
    }

    match slot.as_mut() {
        Some(engine) => export_qr_link(engine),
        None => err("не удалось создать Telegram-сессию"),
    }
}

fn check_qr() -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала нажми «Войти через QR»"),
    };

    let result = engine.runtime.block_on(engine.client.invoke(
        &tl::functions::auth::ExportLoginToken {
            api_id: engine.api_id,
            api_hash: engine.api_hash.clone(),
            except_ids: Vec::new(),
        },
    ));

    match result {
        Ok(tl::enums::auth::LoginToken::Success(_)) => {
            match engine.runtime.block_on(engine.client.is_authorized()) {
                Ok(true) => ok("QR подтверждён. Вход выполнен."),
                Ok(false) => {
                    let password_result = engine.runtime.block_on(engine.client.invoke(
                        &tl::functions::account::GetPassword {},
                    ));
                    match password_result {
                        Ok(password) => {
                            let password: tl::types::account::Password = password.into();
                            engine.password_token = Some(PasswordToken::new(password));
                            ok("QR подтверждён. Введи пароль 2FA и нажми «Войти по паролю 2FA».")
                        }
                        Err(e) => err(format!("QR подтверждён, но нужен пароль 2FA: {e}")),
                    }
                }
                Err(e) => err(e.to_string()),
            }
        }
        Ok(tl::enums::auth::LoginToken::Token(_)) => {
            err("QR ещё не подтверждён. Открой ссылку Telegram и подтверди вход.")
        }
        Ok(tl::enums::auth::LoginToken::MigrateTo(_)) => {
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

    match engine.runtime.block_on(engine.client.check_password(token, password)) {
        Ok(user) => {
            engine.login_token = None;
            ok(format!("Вход выполнен: {}", user.first_name().unwrap_or("пользователь")))
        }
        Err(SignInError::InvalidPassword(_)) => err("неверный пароль Telegram"),
        Err(e) => err(e.to_string()),
    }
}


fn search_username(query: String) -> String {
    let username = query.trim().trim_start_matches('@').to_string();
    if username.is_empty() {
        return err("введи username пользователя");
    }
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };
    match engine.runtime.block_on(engine.client.resolve_username(&username)) {
        Ok(Some(peer)) => ok(peer.name().unwrap_or("Без имени").to_string()),
        Ok(None) => err("пользователь не найден"),
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
            let title = peer.name().unwrap_or("Без названия").replace('|', " ");
            let username = peer.username().unwrap_or("").replace('|', " ");
            let (preview, date) = dialog
                .last_message
                .as_ref()
                .map(|message| {
                    (
                        message
                            .text()
                            .replace('|', " ")
                            .replace('\n', " ")
                            .replace('\r', " ")
                            .replace('\t', " ")
                            .trim()
                            .to_string(),
                        message.date().to_string(),
                    )
                })
                .unwrap_or_else(|| ("Нет сообщений".to_string(), String::new()));
            lines.push(format!(
                "{}|{}|{}|{}|{}",
                lines.len(),
                title,
                if preview.is_empty() { "Медиа" } else { &preview },
                date,
                username
            ));
        }
        Ok::<_, String>(lines)
    });

    match result {
        Ok(lines) if lines.is_empty() => ok("Диалогов пока не найдено."),
        Ok(lines) => lines.join("\n"),
        Err(e) => err(e),
    }
}

fn with_dialog<F, Fut, T>(engine: &mut Engine, index: usize, action: F) -> Result<T, String>
where
    F: FnOnce(grammers_session::types::PeerRef, Client) -> Fut,
    Fut: std::future::Future<Output = Result<T, String>>,
{
    engine.runtime.block_on(async {
        let mut dialogs = engine.client.iter_dialogs();
        let mut current = 0usize;
        while let Some(dialog) = dialogs.next().await.map_err(|e| e.to_string())? {
            if current == index {
                return action(dialog.peer_ref(), engine.client.clone()).await;
            }
            current += 1;
        }
        Err(format!("диалог с номером {} не найден", index + 1))
    })
}

fn messages(index: usize) -> String {
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };

    let result = with_dialog(engine, index, |peer, client| async move {
        // Без лимита: Telegram сам отдаёт историю страницами, а iterator
        // продолжает запрашивать следующие страницы до конца диалога.
        let mut iterator = client.iter_messages(peer);
        let mut lines = Vec::new();
        while let Some(message) = iterator.next().await.map_err(|e| e.to_string())? {
            let text = message.text().trim();
            if !text.is_empty() || message.media().is_some() {
                let direction = if message.outgoing() { "OUT" } else { "IN" };
                let body = if text.is_empty() { "Медиа" } else { text };
                let body = body
                    .replace('\t', " ")
                    .replace('\n', " ")
                    .replace('\r', " ");
                lines.push(format!(
                    "{}\t{}\t#{} {}",
                    direction,
                    message.date(),
                    message.id(),
                    body
                ));
            }
        }
        lines.reverse();
        Ok::<_, String>(lines)
    });

    match result {
        Ok(lines) if lines.is_empty() => ok("В этом диалоге пока нет текстовых сообщений."),
        Ok(lines) => lines.join("\n"),
        Err(e) => err(e),
    }
}

fn send_message(index: usize, text: String) -> String {
    let text = text.trim().to_string();
    if text.is_empty() {
        return err("сообщение пустое");
    }

    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };

    let result = with_dialog(engine, index, |peer, client| async move {
        client
            .send_message(peer, text)
            .await
            .map(|_| "Сообщение отправлено.".to_string())
            .map_err(|e| e.to_string())
    });

    match result {
        Ok(value) => ok(value),
        Err(e) => err(e),
    }
}

fn send_file(index: usize, path: String, caption: String) -> String {
    if path.trim().is_empty() {
        return err("файл не выбран");
    }
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };

    let result = with_dialog(engine, index, |peer, client| async move {
        let uploaded = client.upload_file(&path).await.map_err(|e| e.to_string())?;
        client
            .send_message(peer, InputMessage::new().file(uploaded).text(caption))
            .await
            .map(|_| "Файл отправлен.".to_string())
            .map_err(|e| e.to_string())
    });

    match result {
        Ok(value) => ok(value),
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
        (Ok(api_hash), Ok(phone), Ok(session_path))
            if api_id > 0 && !api_hash.is_empty() && !phone.is_empty() =>
        {
            start_login(api_id, api_hash, phone, session_path)
        }
        _ => err("проверь API ID, API hash и номер телефона"),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeRequestQr(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    api_hash: JString,
    session_path: JString,
) -> jstring {
    let values = (take_string(&mut env, api_hash), take_string(&mut env, session_path));
    let value = match values {
        (Ok(api_hash), Ok(session_path)) if api_id > 0 && !api_hash.is_empty() => {
            request_qr(api_id, api_hash, session_path)
        }
        _ => err("проверь API ID и API hash"),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeCheckQr(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstring(&mut env, check_qr())
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
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSearchUsername(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
) -> jstring {
    let value = match take_string(&mut env, query) {
        Ok(query) => search_username(query),
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

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeMessages(
    mut env: JNIEnv,
    _class: JClass,
    index: jint,
) -> jstring {
    let value = if index < 0 {
        err("неверный номер диалога")
    } else {
        messages(index as usize)
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSendMessage(
    mut env: JNIEnv,
    _class: JClass,
    index: jint,
    text: JString,
) -> jstring {
    let value = match take_string(&mut env, text) {
        Ok(text) if index >= 0 => send_message(index as usize, text),
        Ok(_) => err("неверный номер диалога"),
        Err(e) => err(e),
    };
    jstring(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSendFile(
    mut env: JNIEnv,
    _class: JClass,
    index: jint,
    path: JString,
    caption: JString,
) -> jstring {
    let values = (take_string(&mut env, path), take_string(&mut env, caption));
    let value = match values {
        (Ok(path), Ok(caption)) if index >= 0 => send_file(index as usize, path, caption),
        (Ok(_), Ok(_)) => err("неверный номер диалога"),
        (Err(e), _) | (_, Err(e)) => err(e),
    };
    jstring(&mut env, value)
}
