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

use crate::address::{get_baseaddress_from_mnemonic, get_baseaddress_from_mnemonic_by_networkInfo};

use std::os::raw::c_char;
use std::ffi::{CStr,CString};
use std::mem;
use cardano_serialization_lib::address::NetworkInfo;

use serde::{Deserialize, Serialize};
use serde_json::Result;

mod address;

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_com_bloxbean_cardano_client_CardanoNative_getBaseAddressFromMnemonic(env: JNIEnv,
// This is the class that owns our static method. It's not going to be used,
// but still must be present to match the expected signature of a static
// native method.
                                             class: JClass,
                                             input: JString, index: u32, is_testnet: bool)
                                             -> jstring {
    // First, we have to get the string out of Java. Check out the `strings`
    // module for more info on how this works.
    let input: String =
        env.get_string(input).expect("Couldn't get java string!").into();

    let add = get_baseaddress_from_mnemonic(input.as_str(), index, is_testnet);

    let output = env.new_string(format!("{}", add))
        .expect("Couldn't create java string!");

    output.into_inner()
}

#[no_mangle]
pub fn get_address(phrase: *const c_char, index: u32, is_testnet: bool) -> *const c_char {
    let s =  to_string(phrase);

    let add = get_baseaddress_from_mnemonic(s.as_str(), index, is_testnet);

    to_ptr(add)
}

#[no_mangle]
#[repr(C)]
#[allow(missing_copy_implementations)]
pub struct Network {
    network_id: u8,
    protocol_magic: u64
}

#[no_mangle]
pub fn get_address_by_network(phrase: *const c_char, index: u32, network: &Network) -> *const c_char {
    let s =  to_string(phrase);

    println!("{}", network.network_id);
    println!("{}", network.protocol_magic);

    let netInfo = NetworkInfo::new(network.network_id,
                                    network.protocol_magic as u32);
    let add = get_baseaddress_from_mnemonic_by_networkInfo(s.as_str(), index, netInfo);

    to_ptr(add)
}

// #[no_mangle]
// pub fn get_address_by_network_json(phrase: *const c_char, index: u32, network: *const c_char) -> *const c_char {
//     let s =  to_string(phrase);
//
//     let netStr = to_string(network);
//
//     let nt: NetworkJava = serde_json::from_str(netStr.as_str()).unwrap();
//     println!("{}", nt.network_id);
//     println!("{}", nt.protocol_magic);
//
//    let netInfo = NetworkInfo::new(nt.network_id, nt.protocol_magic);
//    let add = get_baseaddress_from_mnemonic_by_networkInfo(s.as_str(), index, netInfo);
//
//    to_ptr(add)
// }

#[no_mangle]
pub fn generate_mnemonic() -> *const c_char {
    let mnemonic = address::generate_mnemonic();

    to_ptr(mnemonic)
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


