//Using https://github.com/Emurgo/message-signing
//This is just here for reference. So that we can compare the output of the rust code with the output of the java code.
use cardano_message_signing as ms;
use cardano_message_signing::builders::COSESign1Builder;
use cardano_message_signing::cbor::CBORValue;
use cardano_message_signing::utils::{Int, ToBytes};
use cardano_message_signing::{HeaderMap, Headers, Label, ProtectedHeaderMap};
use cardano_serialization_lib as csl;

fn main() {
    use ms::utils::ToBytes;
    let pvt_key_hex = "a09afd74a50ea23fe8607f71766cd56fd78e44e4ee7563e53a690de1d74da15e41802f82cfe0718ade23369800857b885abc1c512aa43059be6e4ce2e8f43ce73d16a13b6998d256de5835612f44fdd56e2f24ab2694b7b70c68f8fd77325c83";
    let sk_bytes = hex::decode(pvt_key_hex).unwrap();

    let sk = csl::crypto::Bip32PrivateKey::from_bytes(&sk_bytes).unwrap();
    let pk = sk.to_public();
    let mut headerMap = HeaderMap::new();
    headerMap.set_algorithm_id(&Label::new_int(&Int::new_i32(14)));
    headerMap.set_content_type(&Label::new_int(&Int::new_i32(-1000)));
    let protected = ProtectedHeaderMap::new(&headerMap);

    let mut unprotected = HeaderMap::new();
    unprotected.set_header(&Label::new_int(&Int::new_i32(-100)), &CBORValue::new_text(String::from("Some header value")));
    let headers = Headers::new(&protected, &unprotected);

    let payload = String::from("Hello World").into_bytes();

    let mut builder = COSESign1Builder::new(&headers, payload, false);
    builder.hash_payload();

    let sig_structure = builder.make_data_to_sign();

    let signed_sig_struct = sk.to_raw_key().sign(&sig_structure.to_bytes()).to_bytes();
    let cose_sign1 = builder.build(signed_sig_struct);

    let serialized = cose_sign1.to_bytes();
    let hex = hex::encode(serialized.clone());
    println!("serialized = {:?}", hex);
}
