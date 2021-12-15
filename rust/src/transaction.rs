use cardano_serialization_lib::fees::LinearFee;
use cardano_serialization_lib::utils::{to_bignum, Value, hash_transaction, make_vkey_witness};
use cardano_serialization_lib::tx_builder::TransactionBuilder;
use crate::address::{harden, get_root_key_from_mnemonic, get_private_key_from_mnemonic};
use cardano_serialization_lib::address::{StakeCredential, NetworkInfo, BaseAddress};
use cardano_serialization_lib::{TransactionInput, TransactionOutput, TransactionBody, Transaction, TransactionWitnessSet};
use cardano_serialization_lib::crypto::{TransactionHash, Vkeywitness, Vkeywitnesses, PrivateKey, Bip32PrivateKey, Ed25519Signature};
use cbor_event::{self as cbor};

pub fn add_witness_and_sign(rawTxnInHex: &str, bech32PvtKey: &str) -> Vec<u8> {
    let bytesTxn = hex::decode(rawTxnInHex).unwrap();

    let prvKey = Bip32PrivateKey::from_bech32(bech32PvtKey).unwrap().to_raw_key();
    let mut transaction = Transaction::from_bytes(bytesTxn).unwrap();

    let txnBody = transaction.body();
    let txnBodyHash = hash_transaction(&txnBody);

    let metadata = transaction.auxiliary_data();

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

    let metadata = transaction.auxiliary_data();

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

pub fn sign(body: &[u8], pvtKeyBytes: &[u8]) -> Ed25519Signature {

    let result;
    if (pvtKeyBytes.len() == 32) {
        result = PrivateKey::from_normal_bytes(&pvtKeyBytes);
    } else {
        result = PrivateKey::from_extended_bytes(&pvtKeyBytes);
    }
    let sk;
    if(result.is_ok()) {
        sk = result.unwrap();
    } else {
        sk = PrivateKey::from_normal_bytes(&pvtKeyBytes).unwrap();
    }

    let signature = sk.sign(&body.as_ref());

    signature
}

#[cfg(test)]
mod tests {
    use crate::transaction::{add_witness_and_sign, sign, sign_txn_with_secretkey};
    use cardano_serialization_lib::{Transaction, PolicyID};
    use cardano_serialization_lib::crypto::{ScriptHash, PrivateKey};

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

    #[test]
    fn parse_and_sign_plutus_script_txn() {
        let str = "84a8008182582070463295d7bd7493ac9caad5e9dbf1127584d6b8c1dfe3a4abb3cb2baf119879000181825839006d80ab5fafa4a486b81b7e599e22dcdc7a8061d560da3b49766005e907834bbc597fd787352df85dd541913f150a449be6d475884d1ebf5d1a0020e530021a0004ef80031a0294e2aa075820cc9a635a116b7766fa90c355e5e7201e11b9027591d098deb074137d669fc4a90b582037d2cb7c299b3642435cbe87c9e7af0f1783d51867805e5d7d2a3b14e8f7557f0d8182582006568935be80d4484485ec902676b0001a935a9b8d677fdfa2674dd6c4022479000f00a303814e4d0100003322222005120012001104811907e505818400001907e5821906a41a00074534f5d90103a200a11902a2a1636d7367816d436f6e74726163742063616c6c02814e4d01000033222220051200120011";

        let pvtKeyHash = "xprv10zlue93vusfclwsqafhyd48v56hfg4aqtptxwzd499q64upxlefaah3l9hw7wa3gy8p0j4a2caacpg7rd04twkypejpuvqrftqr0rh24rn8ay6kadm00t0h878l2fwhcpw6c87v2q746d4u7x6uxsnn84ugncknq";

        let cborTxn = add_witness_and_sign(&str, pvtKeyHash);

        let pvtKeyHash2 = "xprv17qvknep0qlfzxzwm7nhdukkr2ez00yhhf5tztqml4hun8yume30yedlqzlfvcg48v8xqx0a5q5us90pc09ct50d4938echyj6lvp0gvx5yasjh9w02vgaplsh9t892hc2gwvhjz5qv0l4jwq4hjj7pdgeg25rhsq";

        let txnHex = hex::encode(cborTxn);
        let finalCborTxn = add_witness_and_sign(&txnHex, pvtKeyHash2);

        let finalTxn = Transaction::from_bytes(finalCborTxn).unwrap();

        assert_eq!(2, finalTxn.witness_set().vkeys().unwrap().len());
        assert_eq!("323456", finalTxn.body().fee().to_str());
        assert_eq!(1, finalTxn.body().inputs().len());
        assert_eq!(1, finalTxn.body().outputs().len());
    }

    #[test]
    fn sign_test() {
        let msg = "hello";
        let pvtKeyHex = "78bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";

        let msgBytes = msg.as_bytes();
        let pvtKeyBytes = hex::decode(pvtKeyHex).unwrap();

        let signature = sign(&msgBytes, &pvtKeyBytes);
        let signatureHex = hex::encode(signature.to_bytes());

        let expected = "f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07";
        assert_eq!(expected, signatureHex)
    }

    #[test]
    fn sign_with_secret_key() {
        let txnHex = "83a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa0f6";
        let sk = "ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807";

        let serTxn = sign_txn_with_secretkey(txnHex, sk);

        let serTxnHex = hex::encode(serTxn.to_vec());

        let signedTxn = Transaction::from_bytes(serTxn).unwrap();
        let vkWitness = signedTxn.witness_set().vkeys().unwrap().get(0);
        let vkey = vkWitness.vkey();
        let signature = vkWitness.signature();

        let result =  PrivateKey::from_normal_bytes(&hex::decode(sk).unwrap());
        let secPvtKey = result.unwrap();

        let pubKey = secPvtKey.to_public();
        let pubKeyHex = hex::encode(pubKey.as_bytes());

        let expectedSignedTxn = "84a4008282582073198b7ad003862b9798106b88fbccfca464b1a38afb34958275c4a7d7d8d002018258208e03a93578dc0acd523a4dd861793068a06a68b8a6c7358d0c965d2864067b68000184825839000916a5fed4589d910691b85addf608dceee4d9d60d4c9a4d2a925026c3229b212ba7ef8643cd8f7e38d6279336d61a40d228b036f40feed61a004c4b40825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a3aa51029a2581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e190fa0581c6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7a14019232882583900c93b6cac143fe60f8914f44a899f5329433ccec3d53721ef350a0fd8cb873402c73ad8f239f76fb559bb4e3bcff22b310b01eadd3ce205e71a007a1200825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a3b1b1aa3021a000b3aba031a018fb29aa1008182582060209269377f220cdecdc6d5ad42d9b04e58ce74b349efb396ee46adaeb956f35840cd9f8e70a09f24328ee6c14053a38a6a654d31e9e58a9c6c44848e4592265237ce3604eda0cb1812028c3e6b04c66ccc64a1d2685d98e0567477cbc33a4c2f0ff5f6";
        assert_eq!(expectedSignedTxn, serTxnHex);
    }

}
