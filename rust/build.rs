use std::env;
use std::path::{Path, PathBuf};

fn find_library_dirs(root: &Path, result: &mut Vec<PathBuf>, depth: usize) {
    if depth > 7 { return; }
    let Ok(entries) = std::fs::read_dir(root) else { return; };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            find_library_dirs(&path, result, depth + 1);
        } else if let Some(name) = path.file_name().and_then(|x| x.to_str()) {
            if name == "libssl.a" || name == "libcrypto.a" || name == "libssl.so" || name == "libcrypto.so" {
                if let Some(parent) = path.parent() { result.push(parent.to_path_buf()); }
            }
        }
    }
}

fn main() {
    let root = env::var("TDLIB_ANDROID_LIB_ROOT").expect("TDLIB_ANDROID_LIB_ROOT is missing");
    let abi = match env::var("CARGO_CFG_TARGET_ARCH").as_deref() {
        Ok("aarch64") => "arm64-v8a",
        Ok("arm") => "armeabi-v7a",
        Ok("x86_64") => "x86_64",
        other => panic!("unsupported Android ABI: {other:?}"),
    };
    let tdlib_dir = PathBuf::from(&root).join(abi);
    println!("cargo:rustc-link-search=native={}", tdlib_dir.display());

    // build-openssl.sh places libssl/libcrypto outside tdlib/libs. Find the
    // ABI-specific directory instead of assuming one fixed OpenSSL layout.
    let workspace = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let mut openssl_dirs = Vec::new();
    find_library_dirs(&workspace.join("../third_party/openssl"), &mut openssl_dirs, 0);
    for dir in openssl_dirs { println!("cargo:rustc-link-search=native={}", dir.display()); }

    for lib in ["tdjson", "ssl", "crypto", "c++_shared"] {
        println!("cargo:rustc-link-lib=dylib={lib}");
    }
    println!("cargo:rerun-if-env-changed=TDLIB_ANDROID_LIB_ROOT");
}
