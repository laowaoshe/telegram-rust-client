use std::collections::HashMap;
use std::ffi::{CStr, CString};
use std::fs;
use std::os::raw::{c_char, c_double, c_int, c_void};
use std::path::Path;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use serde_json::{json, Value};

#[link(name = "tdjson")]
extern "C" {
    fn td_json_client_create() -> *mut c_void;
    fn td_json_client_send(client: *mut c_void, request: *const c_char);
    fn td_json_client_receive(client: *mut c_void, timeout: c_double) -> *const c_char;
    fn td_json_client_destroy(client: *mut c_void);
    fn td_set_log_verbosity_level(level: c_int);
}

struct Engine { client: *mut c_void, api_id: i32, api_hash: String, serial: u64, chats: Vec<i64>, names: HashMap<i64, String> }
unsafe impl Send for Engine {}
unsafe impl Sync for Engine {}
impl Drop for Engine { fn drop(&mut self) { if !self.client.is_null() { unsafe { td_json_client_destroy(self.client) } } } }
static ENGINE: OnceLock<Mutex<Option<Engine>>> = OnceLock::new();
fn slot() -> &'static Mutex<Option<Engine>> { ENGINE.get_or_init(|| Mutex::new(None)) }
fn error(s: impl Into<String>) -> String { format!("Ошибка: {}", s.into()) }
fn cstr(s: &str) -> Result<CString, String> { CString::new(s).map_err(|_| "недопустимый нулевой символ".into()) }
fn receive(e: &Engine, timeout: f64) -> Option<Value> {
    let p = unsafe { td_json_client_receive(e.client, timeout) }; if p.is_null() { return None; }
    serde_json::from_str(unsafe { CStr::from_ptr(p) }.to_string_lossy().as_ref()).ok()
}
fn send(e: &Engine, mut value: Value) -> Result<String, String> {
    let extra = format!("rustgram-{}", e.serial);
    if let Value::Object(m) = &mut value { m.insert("@extra".into(), Value::String(extra.clone())); }
    let req = cstr(&serde_json::to_string(&value).map_err(|x| x.to_string())?)?;
    unsafe { td_json_client_send(e.client, req.as_ptr()) }; Ok(extra)
}
fn cache_chat(e: &mut Engine, chat: &Value) {
    let id = chat.get("id").and_then(Value::as_i64).unwrap_or(0); if id == 0 { return; }
    if !e.chats.contains(&id) { e.chats.push(id); }
    e.names.insert(id, chat.get("title").and_then(Value::as_str).unwrap_or("Без названия").to_string());
}
fn request(e: &mut Engine, value: Value) -> Result<Value, String> {
    e.serial = e.serial.wrapping_add(1); let wanted = send(e, value)?; let until = Instant::now() + Duration::from_secs(25);
    while Instant::now() < until {
        if let Some(v) = receive(e, 1.0) {
            if v.get("@type").and_then(Value::as_str) == Some("updateNewChat") { cache_chat(e, &v["chat"]); }
            if v.get("@extra").and_then(Value::as_str) == Some(wanted.as_str()) {
                if v.get("@type").and_then(Value::as_str) == Some("error") { return Err(v.get("message").and_then(Value::as_str).unwrap_or("TDLib error").into()); }
                return Ok(v);
            }
        }
    }
    Err("TDLib не ответил вовремя".into())
}
fn new_engine(id: i32, hash: String, session: &str) -> Result<Engine, String> {
    let _ = fs::create_dir_all(Path::new(session)); let client = unsafe { td_json_client_create() };
    if client.is_null() { return Err("не удалось создать TDLib client".into()); } unsafe { td_set_log_verbosity_level(0); }
    let mut e = Engine { client, api_id: id, api_hash: hash, serial: 0, chats: Vec::new(), names: HashMap::new() };
    let configured_api_id = e.api_id;
    let configured_api_hash = e.api_hash.clone();
    request(&mut e, json!({"@type":"setTdlibParameters","parameters":{"@type":"tdlibParameters","database_directory":session,"files_directory":session,"use_file_database":true,"use_chat_info_database":true,"use_message_database":true,"use_secret_chats":false,"api_id":configured_api_id,"api_hash":configured_api_hash,"system_language_code":"ru","device_model":"Android","system_version":"Android","application_version":"RustGram TDLib","enable_storage_optimizer":true,"use_test_dc":false}}))?;
    request(&mut e, json!({"@type":"checkDatabaseEncryptionKey","encryption_key":""}))?; Ok(e)
}
fn with_engine<F: FnOnce(&mut Engine) -> Result<String, String>>(f: F) -> String {
    let mut g = match slot().lock() { Ok(x) => x, Err(_) => return error("внутреннее состояние Rust повреждено") };
    match g.as_mut() { Some(e) => f(e).unwrap_or_else(error), None => error("сначала подключись к Telegram") }
}
fn ensure(id: i32, hash: String, session: String) -> Result<(), String> {
    let mut g = slot().lock().map_err(|_| "внутреннее состояние Rust повреждено".to_string())?;
    if g.is_none() { *g = Some(new_engine(id, hash, &session)?); } Ok(())
}
fn state(e: &mut Engine) -> Result<String, String> { Ok(request(e, json!({"@type":"getAuthorizationState"}))?.get("@type").and_then(Value::as_str).unwrap_or("").into()) }
fn request_code(id: i32, hash: String, phone: String, session: String) -> String {
    if let Err(x) = ensure(id, hash, session) { return error(x); }
    with_engine(|e| { if state(e)? == "authorizationStateReady" { return Ok("Уже выполнен вход".into()); }
        request(e, json!({"@type":"setAuthenticationPhoneNumber","phone_number":phone,"settings":{"@type":"phoneNumberAuthenticationSettings","allow_flash_call":false,"allow_missed_call":false,"is_current_phone_number":false,"has_unknown_phone_number":false,"allow_sms_retriever_api":false}}))?;
        Ok("Код отправлен в Telegram или SMS".into()) })
}
fn qr(id: i32, hash: String, session: String) -> String {
    if let Err(x) = ensure(id, hash, session) { return error(x); }
    with_engine(|e| { e.serial = e.serial.wrapping_add(1); let wanted = send(e, json!({"@type":"requestQrCodeAuthentication","other_user_ids":[]}))?; let until = Instant::now()+Duration::from_secs(15);
        while Instant::now() < until { if let Some(v)=receive(e,1.0) { if let Some(link)=v.pointer("authorization_state.link").and_then(Value::as_str) { return Ok(link.into()); } if v.get("@extra").and_then(Value::as_str)==Some(wanted.as_str()) && v.get("@type").and_then(Value::as_str)==Some("error") { return Err(v.get("message").and_then(Value::as_str).unwrap_or("TDLib error").into()); } } }
        Err("TDLib не вернул QR-ссылку вовремя".into()) })
}
fn check_qr() -> String { with_engine(|e| { let s=state(e)?; if s=="authorizationStateReady" { Ok("QR-вход подтверждён".into()) } else { Ok("QR ещё не подтверждён".into()) } }) }
fn code(v: String) -> String { with_engine(|e| { request(e,json!({"@type":"checkAuthenticationCode","code":v}))?; Ok("Код принят. Если включён 2FA, введи пароль".into()) }) }
fn password(v: String) -> String { with_engine(|e| { request(e,json!({"@type":"checkAuthenticationPassword","password":v}))?; Ok("Вход выполнен".into()) }) }
fn dialogs() -> String { with_engine(|e| { let r=request(e,json!({"@type":"getChats","chat_list":{"@type":"chatListMain"},"limit":100}))?; let ids=r.get("chat_ids").and_then(Value::as_array).cloned().unwrap_or_default(); let mut out=Vec::new(); for (i,v) in ids.iter().enumerate() { let id=v.as_i64().unwrap_or(0); if id==0 {continue} let c=request(e,json!({"@type":"getChat","chat_id":id}))?; cache_chat(e,&c); let title=c.get("title").and_then(Value::as_str).unwrap_or("Без названия").replace('|'," "); let user=c.pointer("usernames.editable_username").and_then(Value::as_str).unwrap_or("").replace('|'," "); let date=c.pointer("last_message.date").and_then(Value::as_i64).unwrap_or(0); out.push(format!("{}|{}|Последние сообщения|{}|{}",i,title,date,user)); } Ok(out.join("\n")) }) }
fn messages(index: usize, offset_id: i64, limit: i32) -> String { with_engine(|e| { let id=*e.chats.get(index).ok_or_else(||"диалог не найден".to_string())?; let r=request(e,json!({"@type":"getChatHistory","chat_id":id,"from_message_id":offset_id,"offset":0,"limit":if limit>0 {limit.min(100)} else {100},"only_local":false}))?; let list=r.get("messages").and_then(Value::as_array).cloned().unwrap_or_default(); let mut out=Vec::new(); for m in &list { let mid=m.get("id").and_then(Value::as_i64).unwrap_or(0); let date=m.get("date").and_then(Value::as_i64).unwrap_or(0); let dir=if m.get("is_outgoing").and_then(Value::as_bool).unwrap_or(false){"OUT"}else{"IN"}; let text=m.pointer("content.text.text").and_then(Value::as_str).unwrap_or("Медиа").replace('\t'," ").replace('\n'," ").replace('\r'," "); out.push(format!("{}\t{}\t#{} {}",dir,date,mid,text)); } out.reverse(); Ok(out.join("\n")) }) }
fn send_message(index: usize, text: String) -> String { with_engine(|e| { let id=*e.chats.get(index).ok_or_else(||"диалог не найден".to_string())?; request(e,json!({"@type":"sendMessage","chat_id":id,"options":{"@type":"messageSendOptions","disable_notification":false,"from_background":false,"protect_content":false,"update_order_of_installed_sticker_sets":false},"input_message_content":{"@type":"inputMessageText","text":{"@type":"formattedText","text":text,"entities":[]},"clear_draft":true,"link_preview_options":null}}))?; Ok("Сообщение отправлено".into()) }) }
fn send_file(index: usize, path: String, caption: String) -> String { with_engine(|e| { let id=*e.chats.get(index).ok_or_else(||"диалог не найден".to_string())?; request(e,json!({"@type":"sendMessage","chat_id":id,"options":{"@type":"messageSendOptions","disable_notification":false,"from_background":false,"protect_content":false,"update_order_of_installed_sticker_sets":false},"input_message_content":{"@type":"inputMessageDocument","document":{"@type":"inputFileLocal","path":path},"thumbnail":null,"disable_content_type_detection":false,"caption":{"@type":"formattedText","text":caption,"entities":[]}}}))?; Ok("Файл отправлен".into()) }) }
fn search(v: String) -> String { with_engine(|e| { let u=v.trim().trim_start_matches('@'); let c=request(e,json!({"@type":"searchPublicChat","username":u}))?; Ok(format!("{} (@{})",c.get("title").and_then(Value::as_str).unwrap_or("Пользователь"),u)) }) }
fn take(e:&mut JNIEnv,s:JString)->Result<String,String>{e.get_string(&s).map(|x|x.into()).map_err(|x|x.to_string())} fn js(e:&mut JNIEnv,s:String)->jstring{e.new_string(s).unwrap().into_raw()}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeBuildInfo(mut e:JNIEnv,_:JClass)->jstring{js(&mut e,"Rust + TDLib JSON".into())}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeRequestCode(mut e:JNIEnv,_:JClass,id:jint,h:JString,p:JString,s:JString)->jstring{let v=match(take(&mut e,h),take(&mut e,p),take(&mut e,s)){(Ok(h),Ok(p),Ok(s))=>request_code(id,h,p,s),(Err(x),_,_)|(_,Err(x),_)|(_,_,Err(x))=>error(x)};js(&mut e,v)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeRequestQr(mut e:JNIEnv,_:JClass,id:jint,h:JString,s:JString)->jstring{let v=match(take(&mut e,h),take(&mut e,s)){(Ok(h),Ok(s))=>qr(id,h,s),(Err(x),_)|(_,Err(x))=>error(x)};js(&mut e,v)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeCheckQr(mut e:JNIEnv,_:JClass)->jstring{js(&mut e,check_qr())}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSubmitCode(mut e:JNIEnv,_:JClass,v:JString)->jstring{let x=take(&mut e,v).map(code).unwrap_or_else(error);js(&mut e,x)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSubmitPassword(mut e:JNIEnv,_:JClass,v:JString)->jstring{let x=take(&mut e,v).map(password).unwrap_or_else(error);js(&mut e,x)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeDialogs(mut e:JNIEnv,_:JClass)->jstring{js(&mut e,dialogs())}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSearchUsername(mut e:JNIEnv,_:JClass,v:JString)->jstring{let x=take(&mut e,v).map(search).unwrap_or_else(error);js(&mut e,x)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeMessages(mut e:JNIEnv,_:JClass,index:jint,offset:jint,limit:jint)->jstring{let x=if index<0{error("неверный диалог")}else{messages(index as usize,offset as i64,limit)};js(&mut e,x)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSendMessage(mut e:JNIEnv,_:JClass,index:jint,v:JString)->jstring{let x=match take(&mut e,v){Ok(v)if index>=0=>send_message(index as usize,v),Ok(_)=>error("неверный диалог"),Err(x)=>error(x)};js(&mut e,x)}
#[no_mangle] pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSendFile(mut e:JNIEnv,_:JClass,index:jint,p:JString,c:JString)->jstring{let x=match(take(&mut e,p),take(&mut e,c)){(Ok(p),Ok(c))if index>=0=>send_file(index as usize,p,c),(Ok(_),Ok(_))=>error("неверный диалог"),(Err(x),_)|(_,Err(x))=>error(x)};js(&mut e,x)}
