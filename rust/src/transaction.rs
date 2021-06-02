use cardano_serialization_lib::fees::LinearFee;
use cardano_serialization_lib::utils::{to_bignum, Value, hash_transaction, make_vkey_witness};
use cardano_serialization_lib::tx_builder::TransactionBuilder;
use crate::address::{harden, get_root_key_from_mnemonic, get_private_key_from_mnemonic};
use cardano_serialization_lib::address::{StakeCredential, NetworkInfo, BaseAddress};
use cardano_serialization_lib::{TransactionInput, TransactionOutput, TransactionBody, Transaction, TransactionWitnessSet};
use cardano_serialization_lib::crypto::{TransactionHash, Vkeywitness, Vkeywitnesses, PrivateKey, Bip32PrivateKey};
use cbor_event::{self as cbor};

pub fn add_witness_and_sign(rawTxnInHex: &str, bech32PvtKey: &str) -> Vec<u8> {
    let bytesTxn = hex::decode(rawTxnInHex).unwrap();

    let prvKey = Bip32PrivateKey::from_bech32(bech32PvtKey).unwrap().to_raw_key();
    let mut transaction = Transaction::from_bytes(bytesTxn).unwrap();

    let txnBody = transaction.body();
    let txnBodyHash = hash_transaction(&txnBody);

    let metadata = transaction.metadata();

    let mut txnWithnewssSet = match transaction.witness_set() {
        tws => tws,
        _ => TransactionWitnessSet::new()
    };

    let mut vkey_witnesses = match txnWithnewssSet.vkeys() {
        Some(vkws) => vkws,
        None => Vkeywitnesses::new()
    };

    let vkey_witness = make_vkey_witness(&txnBodyHash, &prvKey);
    vkey_witnesses.add(&vkey_witness);

    txnWithnewssSet.set_vkeys(&vkey_witnesses);

    let wns = transaction.witness_set().vkeys();
    let finalTxn = Transaction::new(&txnBody, &txnWithnewssSet, metadata);

    cbor::cbor!(&finalTxn).unwrap()
}

pub fn sign_txn_with_secretkey(rawTxnInHex: &str, secretKey: &str) -> Vec<u8> {
    let bytesTxn = hex::decode(rawTxnInHex).unwrap();

    let skeyBytes = hex::decode(secretKey).unwrap();

    let prvKey;
    let result = PrivateKey::from_normal_bytes(&skeyBytes);
    if(result.is_ok()) {
        prvKey = result.unwrap();
    } else {
        prvKey = Bip32PrivateKey::from_bytes(&skeyBytes).unwrap().to_raw_key();
    }

    let mut transaction = Transaction::from_bytes(bytesTxn).unwrap();

    let txnBody = transaction.body();
    let txnBodyHash = hash_transaction(&txnBody);

    let metadata = transaction.metadata();

    let mut txnWithnewssSet = match transaction.witness_set() {
        tws => tws,
        _ => TransactionWitnessSet::new()
    };

    let mut vkey_witnesses = match txnWithnewssSet.vkeys() {
        Some(vkws) => vkws,
        None => Vkeywitnesses::new()
    };

    let vkey_witness = make_vkey_witness(&txnBodyHash, &prvKey);
    vkey_witnesses.add(&vkey_witness);

    txnWithnewssSet.set_vkeys(&vkey_witnesses);

    let wns = transaction.witness_set().vkeys();
    let finalTxn = Transaction::new(&txnBody, &txnWithnewssSet, metadata);

    cbor::cbor!(&finalTxn).unwrap()
}

pub fn validate_txn_cbor(rawTxnInHex: &str) -> bool {
    let bytesTxn = hex::decode(rawTxnInHex).unwrap();
    let finalTxn = Transaction::from_bytes(bytesTxn).unwrap();

    return true;
}

#[cfg(test)]
mod tests {
    use crate::transaction::add_witness_and_sign;
    use cardano_serialization_lib::{Transaction, PolicyID};
    use cardano_serialization_lib::crypto::ScriptHash;

    #[test]
    fn parse_and_sign_txn() {
        let str = "83a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa0f6";

        let mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        let pvtKeyHash = "xprv10zlue93vusfclwsqafhyd48v56hfg4aqtptxwzd499q64upxlefaah3l9hw7wa3gy8p0j4a2caacpg7rd04twkypejpuvqrftqr0rh24rn8ay6kadm00t0h878l2fwhcpw6c87v2q746d4u7x6uxsnn84ugncknq";

        let cborTxn = add_witness_and_sign(&str, pvtKeyHash);

        let finalTxn = Transaction::from_bytes(cborTxn).unwrap();

        // assert_eq!(1, finalTxn.witness_set().vkeys().unwrap().len());
        // assert_eq!("367965", finalTxn.body().fee().to_str());
        // assert_eq!(26194586, finalTxn.body().ttl().unwrap());
        assert_eq!(2, finalTxn.body().inputs().len());
        assert_eq!(4, finalTxn.body().outputs().len());

        let policyId = finalTxn.body().outputs().get(1).amount().multiasset().unwrap().keys().get(1).to_bytes();
        let policyIdStr = hex::encode(&policyId);

        let policyIdObj = PolicyID::from_bytes(policyId);

        let asset = finalTxn.body().outputs().get(1).amount().multiasset().unwrap().get(&policyIdObj.unwrap());
        let assetObj = asset.unwrap();
        let assetName = assetObj.keys().get(0);
        let assetValue = assetObj.get(&assetName).unwrap();
        let assetNameInHex = hex::encode(assetName.to_bytes());

        println!("Policy Id : {}", policyIdStr);
        println!("Asset Name: {}", assetNameInHex);
        println!("Asset value : {}", assetValue.to_str())
    }

    #[test]
    fn parse_and_sign_txn_multi_times() {
        let str = "83a40081825820dcac27eed284adfa6ec02a6e8fa41f886faf267bff7a6e615df44ab8a311360d000182825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f81a3af6f8c6021a00059d5d031a018fb29aa0f6";

        let pvtKeyHash = "xprv10zlue93vusfclwsqafhyd48v56hfg4aqtptxwzd499q64upxlefaah3l9hw7wa3gy8p0j4a2caacpg7rd04twkypejpuvqrftqr0rh24rn8ay6kadm00t0h878l2fwhcpw6c87v2q746d4u7x6uxsnn84ugncknq";

        let cborTxn = add_witness_and_sign(&str, pvtKeyHash);

        let pvtKeyHash2 = "xprv17qvknep0qlfzxzwm7nhdukkr2ez00yhhf5tztqml4hun8yume30yedlqzlfvcg48v8xqx0a5q5us90pc09ct50d4938echyj6lvp0gvx5yasjh9w02vgaplsh9t892hc2gwvhjz5qv0l4jwq4hjj7pdgeg25rhsq";

        let txnHex = hex::encode(cborTxn);
        let finalCborTxn = add_witness_and_sign(&txnHex, pvtKeyHash2);

        let finalTxn = Transaction::from_bytes(finalCborTxn).unwrap();

        assert_eq!(2, finalTxn.witness_set().vkeys().unwrap().len());
        assert_eq!("367965", finalTxn.body().fee().to_str());
        assert_eq!(26194586, finalTxn.body().ttl().unwrap());
        assert_eq!(1, finalTxn.body().inputs().len());
        assert_eq!(2, finalTxn.body().outputs().len());
    }

    #[test]
    fn parse_and_sign_txn_multi_multiasset() {
        let str = "83a400818258202a95e941761fa6187d0eaeec3ea0a8f68f439ec806ebb0e4550e640e8e0d189c010182825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c42344821a000f4240a1581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e194e2082583900017af0b748a2eed298f1f1b100dc20a97ee02b70e3dd3a9c2952a19bb232b8896e2328fabbd9a423b18937e31b0dfbb0e6d5683f79e03c96821a3a8ac183a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a247736174636f696e1a11e154e047926174636f766e1a29b92700581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a1401a000105b8021a0001d4c0031a00030d3fa0f6";

        let mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        let pvtKeyHash = "xprv10zlue93vusfclwsqafhyd48v56hfg4aqtptxwzd499q64upxlefaah3l9hw7wa3gy8p0j4a2caacpg7rd04twkypejpuvqrftqr0rh24rn8ay6kadm00t0h878l2fwhcpw6c87v2q746d4u7x6uxsnn84ugncknq";

        let cborTxn = add_witness_and_sign(&str, pvtKeyHash);

        let finalTxn = Transaction::from_bytes(cborTxn).unwrap();

        assert_eq!(1, finalTxn.body().inputs().len());
        assert_eq!(2, finalTxn.body().outputs().len());

        let policyId = finalTxn.body().outputs().get(1).amount().multiasset().unwrap().keys().get(1).to_bytes();
        let policyIdStr = hex::encode(&policyId);

        let policyIdObj = PolicyID::from_bytes(policyId);

        let asset = finalTxn.body().outputs().get(1).amount().multiasset().unwrap().get(&policyIdObj.unwrap());
        let assetObj = asset.unwrap();
        let assetName = assetObj.keys().get(0);
        let assetValue = assetObj.get(&assetName).unwrap();
        let assetNameInHex = hex::encode(assetName.to_bytes());

        println!("Policy Id : {}", policyIdStr);
        println!("Asset Name: {}", assetNameInHex);
        println!("Asset value : {}", assetValue.to_str())
    }

    #[test]
    fn parse_and_sign_mint_txn() {
        let str = "83a5008182582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002010182825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed6199c40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a00053020a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a140192328021a00059d5d031a018fb29a09a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a140192328a0f6";

        let mnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";

        let pvtKeyHash = "xprv10zlue93vusfclwsqafhyd48v56hfg4aqtptxwzd499q64upxlefaah3l9hw7wa3gy8p0j4a2caacpg7rd04twkypejpuvqrftqr0rh24rn8ay6kadm00t0h878l2fwhcpw6c87v2q746d4u7x6uxsnn84ugncknq";

        let cborTxn = add_witness_and_sign(&str, pvtKeyHash);

        let finalTxn = Transaction::from_bytes(cborTxn).unwrap();

        assert_eq!(1, finalTxn.body().inputs().len());
        assert_eq!(2, finalTxn.body().outputs().len());

        let policyId = finalTxn.body().multiassets().unwrap().keys().get(0).to_bytes();
        let policyIdStr = hex::encode(&policyId);
        let policyIdObj = PolicyID::from_bytes(policyId);

        let asset = finalTxn.body().multiassets().unwrap().get(&policyIdObj.unwrap());
        let assetObj = asset.unwrap();
        let assetName = assetObj.keys().get(0);
        let assetValue = assetObj.get(&assetName).unwrap().as_positive().unwrap();
        let assetNameInHex = hex::encode(assetName.to_bytes());

        let policyId2 = finalTxn.body().multiassets().unwrap().keys().get(1).to_bytes();
        let policyIdStr2 = hex::encode(&policyId2);

        println!("Policy Id1 : {}", policyIdStr);
        println!("Asset Name1 : {}", assetNameInHex);
        println!("Asset Qty1 : {}", assetValue.to_str());

        println!("Policy Id2 : {}", policyIdStr2);
    }

}
