// This is the interface to the JVM that we'll call the majority of our
// methods on.
use jni::JNIEnv;
// These objects are what you should use as arguments to your native
// function. They carry extra lifetime information to prevent them escaping
// this context and getting used after being GC'd.
use jni::objects::{JClass, JString};
// This is just a pointer. We'll be returning it from our function. We
// can't return one of the objects with lifetime information because the
// lifetime checker won't let us.
use jni::sys::jstring;

use crate::address::{get_baseaddress_from_mnemonic, get_baseaddress_from_mnemonic_by_networkInfo, get_enterpriseaddress_from_mnemonic_by_networkInfo, get_enterpriseaddress_from_mnemonic};

use std::os::raw::c_char;
use std::ffi::{CStr,CString};
use std::{mem, panic};
use cardano_serialization_lib::address::NetworkInfo;

use serde::{Deserialize, Serialize};
use serde_json::Result;

mod address;

#[no_mangle]
#[repr(C)]
#[allow(missing_copy_implementations)]
pub struct Network {
    network_id: u8,
    protocol_magic: u64
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn getBaseAddress(phrase: *const c_char, index: u32, is_testnet: bool) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);
        let add = get_baseaddress_from_mnemonic(s.as_str(), index, is_testnet);

        to_ptr(add)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            to_ptr(String::new())
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn getBaseAddressByNetwork(phrase: *const c_char, index: u32, network: &Network) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);

        let netInfo = NetworkInfo::new(network.network_id,
                                       network.protocol_magic as u32);

        let add = get_baseaddress_from_mnemonic_by_networkInfo(s.as_str(), index, netInfo);

        to_ptr(add)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            to_ptr(String::new())
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn getEnterpriseAddress(phrase: *const c_char, index: u32, is_testnet: bool) -> *const c_char {

    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);
        let add = get_enterpriseaddress_from_mnemonic(s.as_str(), index, is_testnet);

        to_ptr(add)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            to_ptr(String::new())
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn getEnterpriseAddressByNetwork(phrase: *const c_char, index: u32, network: &Network) -> *const c_char {

    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);

        let netInfo = NetworkInfo::new(network.network_id,
                                       network.protocol_magic as u32);
        let add = get_enterpriseaddress_from_mnemonic_by_networkInfo(s.as_str(), index, netInfo);

        to_ptr(add)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            to_ptr(String::new())
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn generateMnemonic() -> *const c_char {
    let result = panic::catch_unwind(|| {
        let mnemonic = address::generate_mnemonic();

        to_ptr(mnemonic)
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

#[cfg(test)]
mod tests {

}


