// This is the interface to the JVM that we'll call the majority of our
// methods on.
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

use std::os::raw::c_char;
use std::ffi::{CStr,CString};
use std::{mem, panic};
use ed25519_dalek::{ExpandedSecretKey, PublicKey};

mod transaction;

#[no_mangle]
#[allow(non_snake_case)]
pub fn signExtended(msgHex: *const c_char, expandedSecretKeyHex: *const c_char, publicKeyHex: *const c_char) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let msgStr =  to_string(msgHex);
        let pvtKeyStr = to_string(expandedSecretKeyHex);
        let pubKeyStr = to_string(publicKeyHex);

        let body = hex::decode(msgStr).unwrap();
        let pvtKeyBytes = hex::decode(pvtKeyStr).unwrap();
        let pubKeyBytes = hex::decode(pubKeyStr).unwrap();

        let expanded_secret_key = ExpandedSecretKey::from_bytes(&pvtKeyBytes).unwrap();
        let pub_key = PublicKey::from_bytes(&pubKeyBytes).unwrap();

        let signature = expanded_secret_key.sign(&body, &pub_key);
        let signatureHex = hex::encode(signature.to_bytes());

        to_ptr(signatureHex)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            to_ptr(String::new())
        }
    }
}

/// Convert a native string to a Rust string
fn to_string(pointer: *const c_char) -> String {
    let c_str: &CStr = unsafe { CStr::from_ptr(pointer) };
    c_str.to_str().unwrap().to_string()
}

/// Convert a Rust string to a native string
fn to_ptr(string: String) -> *const c_char {
    let cs = CString::new(string.as_bytes()).unwrap();
    let ptr = cs.as_ptr();
    // Tell Rust not to clean up the string while we still have a pointer to it.
    // Otherwise, we'll get a segfault.
    mem::forget(cs);
    ptr
}

#[no_mangle]
#[allow(non_snake_case)]
fn dropCharPointer(pointer: *const c_char) {
    unsafe {
        mem::drop(pointer);
    }
}

#[no_mangle]
#[allow(non_snake_case)]
fn printPointer(pointer: *const c_char) {
    println!("Print pointer >>> {}", to_string(pointer));
}

#[cfg(test)]
mod tests {

}


