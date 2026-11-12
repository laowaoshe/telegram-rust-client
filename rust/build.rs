use std::env;
use std::path::PathBuf;

fn main() {
    let root = env::var("TDLIB_ANDROID_LIB_ROOT")
        .expect("TDLIB_ANDROID_LIB_ROOT must point to TDLib's tdlib/libs directory");
    let abi = match env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "arm64-v8a",
        Ok("arm") => "armeabi-v7a",
        Ok("x86_64") => "x86_64",
        Ok("x86") => "x86",
        _ => panic!("unsupported Android target architecture"),
    };
    let path = PathBuf::from(root).join(abi);
    if !path.exists() {
        panic!("TDLib libraries not found at {}", path.display());
    }
    println!("cargo:rustc-link-search=native={}", path.display());
    println!("cargo:rustc-link-lib=dylib=tdjson");
    println!("cargo:rustc-link-lib=dylib=ssl");
    println!("cargo:rustc-link-lib=dylib=crypto");
    println!("cargo:rustc-link-lib=dylib=c++_shared");
    println!("cargo:rerun-if-env-changed=TDLIB_ANDROID_LIB_ROOT");
}
