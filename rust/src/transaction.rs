use cardano_serialization_lib::fees::LinearFee;
use cardano_serialization_lib::utils::{to_bignum, Value};
use cardano_serialization_lib::tx_builder::TransactionBuilder;
use crate::address::harden;
use cardano_serialization_lib::address::{StakeCredential, NetworkInfo, BaseAddress};
use cardano_serialization_lib::{TransactionInput, TransactionOutput};

#[cfg(test)]
mod tests {
    use super::*;
    // use fees::*;
    use cardano_serialization_lib::{TransactionBody, Transaction};
    use cardano_serialization_lib::crypto::{TransactionHash, Bip32PrivateKey};
    use serde_json::Serializer;
    use cbor_event::Serialize;
    use std::io::LineWriter;
    use cbor_event::de::Deserializer;

    fn genesis_id() -> TransactionHash {
        TransactionHash::from([0u8; TransactionHash::BYTE_COUNT])
    }

    fn root_key_15() -> Bip32PrivateKey {
        // art forum devote street sure rather head chuckle guard poverty release quote oak craft enemy
        let entropy = [0x0c, 0xcb, 0x74, 0xf3, 0x6b, 0x7d, 0xa1, 0x64, 0x9a, 0x81, 0x44, 0x67, 0x55, 0x22, 0xd4, 0xd8, 0x09, 0x7c, 0x64, 0x12];
        Bip32PrivateKey::from_bip39_entropy(&entropy, &[])
    }

    fn harden(index: u32) -> u32 {
        index | 0x80_00_00_00
    }

    #[test]
    fn build_tx_with_change() {
        let linear_fee = LinearFee::new(&to_bignum(500), &to_bignum(2));
        let mut tx_builder =
            TransactionBuilder::new(&linear_fee, &to_bignum(1), &to_bignum(1), &to_bignum(1));
        let spend = root_key_15()
            .derive(harden(1852))
            .derive(harden(1815))
            .derive(harden(0))
            .derive(0)
            .derive(0)
            .to_public();
        let change_key = root_key_15()
            .derive(harden(1852))
            .derive(harden(1815))
            .derive(harden(0))
            .derive(1)
            .derive(0)
            .to_public();
        let stake = root_key_15()
            .derive(harden(1852))
            .derive(harden(1815))
            .derive(harden(0))
            .derive(2)
            .derive(0)
            .to_public();

        let spend_cred = StakeCredential::from_keyhash(&spend.to_raw_key().hash());
        let stake_cred = StakeCredential::from_keyhash(&stake.to_raw_key().hash());
        let addr_net_0 = BaseAddress::new(NetworkInfo::testnet().network_id(), &spend_cred, &stake_cred).to_address();
        tx_builder.add_key_input(
            &spend.to_raw_key().hash(),
            &TransactionInput::new(&genesis_id(), 0),
            &Value::new(&to_bignum(1_000_000))
        );
        tx_builder.add_output(&TransactionOutput::new(
            &addr_net_0,
            &Value::new(&to_bignum(10))
        )).unwrap();
        tx_builder.set_ttl(1000);

        let change_cred = StakeCredential::from_keyhash(&change_key.to_raw_key().hash());
        let change_addr = BaseAddress::new(NetworkInfo::testnet().network_id(), &change_cred, &stake_cred).to_address();
        let added_change = tx_builder.add_change_if_needed(
            &change_addr
        );

        let final_tx = tx_builder.build();

        let mut serializer = cbor_event::se::Serializer::new_vec();

       // let enc = serializer.write_bytes(final_tx.unwrap().to_bytes());
        let e = serializer.serialize(&final_tx.unwrap());


        println!("{}", "hello");
        // assert!(added_change.unwrap());
        // assert_eq!(tx_builder.outputs.len(), 2);
        // assert_eq!(
        //     tx_builder.get_explicit_input().unwrap().checked_add(&tx_builder.get_implicit_input().unwrap()).unwrap(),
        //     tx_builder.get_explicit_output().unwrap().checked_add(&Value::new(&tx_builder.get_fee_if_set().unwrap())).unwrap()
        // );
        // let _final_tx = tx_builder.build(); // just test that it doesn't throw
    }

    #[test]
    fn parse_b64() {
        let str = "pQCBglggQSPXD2ZBTMkh9v/Cmomar8cTepmg/UU9ayAIY+9XAtYFAYGCWC8MARESDhMTERcMCBgcDA8KCwAZDwcfAQ0WBgYBGwwdAQYIEgYOGQ4EHRUeCwAQCBkH0AIZHngDGDwHWCBIZWxsSGVsbEhlbGxIZWxsSGVsbEhlbGxIZWxsSGVsbA==";
        let bytes = base64::decode(str).unwrap();


        let transaction = TransactionBody::from_bytes(bytes).unwrap();
        let coin = transaction.fee();

        // let txIdb64 = base64::encode(transaction.transaction_id().to_bytes());
        // println!("{}", transaction.index());
        //  println!("{}", txIdb64);
        println!("{}", coin.to_str());
        println!("{}", transaction.ttl().unwrap());
    }
}
