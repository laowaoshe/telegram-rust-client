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
    println!("cargo:rustc-link-search=native={}", PathBuf::from(root).join(abi).display());
    // libtdjson.so is already produced by TDLib's Android build and carries
    // its OpenSSL linkage. Rust must not link -lssl/-lcrypto a second time:
    // the OpenSSL build creates static archives, not shared libraries.
    println!("cargo:rustc-link-lib=dylib=tdjson");
    println!("cargo:rustc-link-lib=dylib=c++_shared");
    println!("cargo:rerun-if-env-changed=TDLIB_ANDROID_LIB_ROOT");
}
