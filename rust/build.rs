use std::env;
use std::path::PathBuf;
fn main() {
    let root = env::var("TDLIB_ANDROID_LIB_ROOT").expect("TDLIB_ANDROID_LIB_ROOT is missing");
    let abi = match env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "arm64-v8a",
        Ok("arm") => "armeabi-v7a",
        Ok("x86_64") => "x86_64",
        other => panic!("unsupported Android ABI: {other:?}"),
    };
    let dir = PathBuf::from(root).join(abi);
    println!("cargo:rustc-link-search=native={}", dir.display());
    for lib in ["tdjson", "ssl", "crypto", "c++_shared"] { println!("cargo:rustc-link-lib=dylib={lib}"); }
    println!("cargo:rerun-if-env-changed=TDLIB_ANDROID_LIB_ROOT");
}
