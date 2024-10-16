//Using https://github.com/Emurgo/message-signing
//This is just here for reference. So that we can compare the output of the rust code with the output of the java code.
use cardano_message_signing as ms;
use cardano_serialization_lib as csl;
use cardano_message_signing::{COSESignature, COSESignatures, HeaderMap, Headers, Label, ProtectedHeaderMap};
use cardano_message_signing::builders::{COSESign1Builder, COSESignBuilder};
use cardano_message_signing::cbor::CBORValue;
use cardano_message_signing::utils::{Int, ToBytes};

fn main() {
    use ms::utils::ToBytes;
    //Mnemonic: "nice orient enjoy teach jump office alert inquiry apart unaware seat tumble unveil device have bullet morning eyebrow time image embody divide version uniform"
    let pvt_key_hex1 = "a09afd74a50ea23fe8607f71766cd56fd78e44e4ee7563e53a690de1d74da15e41802f82cfe0718ade23369800857b885abc1c512aa43059be6e4ce2e8f43ce73d16a13b6998d256de5835612f44fdd56e2f24ab2694b7b70c68f8fd77325c83";
    let sk_bytes1 = hex::decode(pvt_key_hex1).unwrap();
    let bip32SK1 = csl::crypto::Bip32PrivateKey::from_bytes(&sk_bytes1).unwrap();

    //Mnemonic: ""carbon time empty obey bicycle choice mind kitchen shadow call strike skull check flag series deal garlic wing uphold problem bamboo winner install price"
    let pvt_key_hex2 = "a0bcf0bee440970644e0f7850776c38040e6be001a8847f07b2cc1f982153b55cf1b9e9c1ed5881bcb1546e1176e4852c41df5f72ccc876dcc5e227328e103a64d992baad3ce8e9b4d97541423535db5f82edf5b19e3a2ed602466824cfae9ce";
    let sk_bytes2 = hex::decode(pvt_key_hex2).unwrap();
    let bip32SK2 = csl::crypto::Bip32PrivateKey::from_bytes(&sk_bytes2).unwrap();

    let mut headerMap = HeaderMap::new();
    headerMap.set_algorithm_id(&Label::new_int(&Int::new_i32(14)));
    headerMap.set_content_type(&Label::new_int(&Int::new_i32(-1000)));
    let protected = ProtectedHeaderMap::new(&headerMap);

    let mut unprotected = HeaderMap::new();
    unprotected.set_header(&Label::new_int(&Int::new_i32(-100)), &CBORValue::new_text(String::from("Some header value")));
    let headers = Headers::new(&protected, &unprotected);

    let payload = String::from("Hello World").into_bytes();

    let mut builder = COSESignBuilder::new(&headers, payload, false);
    builder.hash_payload();

    //Build sig structure
    let sig_structure = builder.make_data_to_sign();

    //cose_signature1
    let sk1 = bip32SK1.to_raw_key();
    let signature1 = sk1.sign(&sig_structure.to_bytes()).to_bytes();

    let mut cose_sig_unprotected_header1 = HeaderMap::new();
    cose_sig_unprotected_header1.set_header(&Label::new_int(&Int::new_i32(-200)), &CBORValue::new_text(String::from("another additional header")));
    cose_sig_unprotected_header1.set_header(&Label::new_text(String::from("key1")), &CBORValue::new_text(String::from("key1 value")));

    let cose_sig_header1 = Headers::new(&ProtectedHeaderMap::new(&HeaderMap::new()), &cose_sig_unprotected_header1);

    let coseSignature1 = COSESignature::new(&cose_sig_header1,  signature1);

    //cose_signature2
    let sk2 = bip32SK2.to_raw_key();
    let signature2 = sk2.sign(&sig_structure.to_bytes()).to_bytes();

    let mut cose_sig_unprotected_header2 = HeaderMap::new();
    cose_sig_unprotected_header2.set_header(&Label::new_int(&Int::new_i32(-400)), &CBORValue::new_text(String::from("another additional header2")));
    cose_sig_unprotected_header2.set_header(&Label::new_text(String::from("key2")), &CBORValue::new_text(String::from("key2 value")));

    let cose_sig_header2 = Headers::new(&ProtectedHeaderMap::new(&HeaderMap::new()), &cose_sig_unprotected_header2);

    let coseSignature2 = COSESignature::new(&cose_sig_header2,  signature2);

    //Build cose_signatures
    let mut cose_signatures = COSESignatures::new();
    cose_signatures.add(&coseSignature1);
    cose_signatures.add(&coseSignature2);

    //Build cose_sign
    let cose_sign = builder.build(&cose_signatures);

    let serialized = cose_sign.to_bytes();
    let hex = hex::encode(serialized.clone());
    println!("serialized = {:?}", hex);
}
