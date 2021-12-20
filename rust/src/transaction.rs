use ed25519_dalek::{ExpandedSecretKey, PublicKey, Signature};

pub fn sign_extended(body: &[u8], pvtKey: &[u8], pubKey: &[u8]) -> Signature {
    let expanded_secret_key = ExpandedSecretKey::from_bytes(pvtKey).unwrap();
    let pub_key = PublicKey::from_bytes(pubKey).unwrap();

    let signature = expanded_secret_key.sign(&body, &pub_key);
    return signature;
}

#[cfg(test)]
mod tests {
    use crate::transaction::sign_extended;

    #[test]
    fn sign_extended_test() {
        let msg = "hello";
        let pvtKeyHex = "78bfcc962ce4138fba00ea6e46d4eca6ae9457a058566709b52941aaf026fe53dede3f2ddde7762821c2f957aac77b80a3c36beab75881cc83c600695806f1dd";
        let pubkeyHex = "9518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a8";

        let msgBytes = msg.as_bytes();
        let pvtKeyBytes = hex::decode(pvtKeyHex).unwrap();
        let pubKeyBytes = hex::decode(pubkeyHex).unwrap();

        let signature = sign_extended(msgBytes, &pvtKeyBytes, &pubKeyBytes);
        let signatureHex = hex::encode(signature.to_bytes());

        let expected = "f13fa9acffb108114ec060561b58005fb2d69184de0a2d7400b2ea1f111c0794831cc832c92daf4807820dd9458324935e90bec855e8bf076bbbc4e42b727b07";
        assert_eq!(expected, signatureHex)
    }

}
