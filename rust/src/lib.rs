use jni::objects::JClass;
use jni::sys::jstring;
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeBuildInfo(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    env.new_string("Rust-ядро подключено успешно").unwrap().into_raw()
}
