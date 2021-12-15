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

use crate::address::{get_baseaddress_from_mnemonic, get_baseaddress_from_mnemonic_by_networkInfo, get_enterpriseaddress_from_mnemonic_by_networkInfo, get_enterpriseaddress_from_mnemonic, get_private_key_from_mnemonic, get_private_key_bytes_from_mnemonic, get_public_key_bytes_from_mnemonic};

use std::os::raw::c_char;
use std::ffi::{CStr,CString};
use std::{mem, panic};
use cardano_serialization_lib::address::NetworkInfo;

use serde::{Deserialize, Serialize};
use serde_json::Result;

mod address;
mod transaction;

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

#[no_mangle]
#[allow(non_snake_case)]
pub fn getPrivateKeyFromMnemonic(phrase: *const c_char, index: u32) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);

        let pvtKeyInBech32 = get_private_key_from_mnemonic(s.as_str(), index);

        to_ptr(pvtKeyInBech32)
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
pub fn getPrivateKeyBytesFromMnemonic(phrase: *const c_char, index: u32) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);

        let pvtKeyBytes = get_private_key_bytes_from_mnemonic(s.as_str(), index);

        let hexStr = hex::encode(pvtKeyBytes);
        to_ptr(hexStr)
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
pub fn getPublicKeyBytesFromMnemonic(phrase: *const c_char, index: u32) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let s =  to_string(phrase);

        let pvtKeyBytes = get_public_key_bytes_from_mnemonic(s.as_str(), index);

        let hexStr = hex::encode(pvtKeyBytes);
        to_ptr(hexStr)
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
pub fn bech32AddressToBytes(bech32Address: *const c_char)  -> *const c_char {
    let result = panic::catch_unwind(|| {
        let _address =  to_string(bech32Address);
        let bytes = address::bech32_address_to_bytes(_address.as_str());

        let hexStr = hex::encode(bytes.as_slice());

        to_ptr(hexStr)
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
pub fn hexBytesToBech32Address(hexAddress: *const c_char)  -> *const c_char {
    let result = panic::catch_unwind(|| {
        let _hexAddress =  to_string(hexAddress);
        let byteAddress = hex::decode(_hexAddress).unwrap();

        let bech32Address = address::bytes_to_bech32_address(byteAddress);

        to_ptr(bech32Address)
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
pub fn base58AddressToBytes(base58Address: *const c_char) -> *const c_char { //byron address
    let result = panic::catch_unwind(|| {
        let _address =  to_string(base58Address);
        let bytes = address::base58_address_to_bytes(_address.as_str());

        let hexStr = hex::encode(bytes.as_slice());

        to_ptr(hexStr)
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
pub fn hexBytesToBase58Address(hexAddress: *const c_char)  -> *const c_char { //byron address
    let result = panic::catch_unwind(|| {
        let _hexAddress =  to_string(hexAddress);
        let byteAddress = hex::decode(_hexAddress).unwrap();

        let base58Address = address::bytes_to_base58_address(byteAddress);

        to_ptr(base58Address)
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
pub fn sign(rawTxnHex: *const c_char, privateKey: *const c_char) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let rawTxnInHexStr =  to_string(rawTxnHex);
        let pvtKeyStr = to_string(privateKey);

        let signedTxnBytes = transaction::add_witness_and_sign(&rawTxnInHexStr, &pvtKeyStr);

        let signTxnHex = hex::encode(signedTxnBytes);

        to_ptr(signTxnHex)
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
pub fn signWithSecretKey(rawTxnHex: *const c_char, privateKey: *const c_char) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let rawTxnInHexStr =  to_string(rawTxnHex);
        let pvtKeyStr = to_string(privateKey);

        let signedTxnBytes = transaction::sign_txn_with_secretkey(&rawTxnInHexStr, &pvtKeyStr);

        let signTxnHex = hex::encode(signedTxnBytes);

        to_ptr(signTxnHex)
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
pub fn validateTransactionCBOR(rawTxnHex: *const c_char) -> bool {
    let result = panic::catch_unwind(|| {
        let rawTxnInHexStr =  to_string(rawTxnHex);
        transaction::validate_txn_cbor(&rawTxnInHexStr)
    });

    match result {
        Ok(c) => c,
        Err(cause) => {
            false
        }
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub fn signMsg(msgHex: *const c_char, privateKey: *const c_char) -> *const c_char {
    let result = panic::catch_unwind(|| {
        let msgStr =  to_string(msgHex);
        let pvtKeyStr = to_string(privateKey);

        let body = hex::decode(msgStr).unwrap();
        let pvtKeyBytes = hex::decode(pvtKeyStr).unwrap();

        let signature = transaction::sign(body.as_ref(), pvtKeyBytes.as_ref());

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


