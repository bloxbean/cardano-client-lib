use std::ffi::{CStr, CString};
use std::mem;
use std::os::raw::c_char;
use std::str;

use bip39::{Language, Mnemonic, MnemonicType};
use cardano_serialization_lib::address::{BaseAddress, NetworkInfo, Pointer, PointerAddress, StakeCredential, EnterpriseAddress, Address};
use cardano_serialization_lib::chain_core::property::FromStr;
use cardano_serialization_lib::crypto::{Bip32PrivateKey, PrivateKey};
use rand::prelude::*;

pub fn get_root_key(entropy: &[u8]) -> Bip32PrivateKey {
    Bip32PrivateKey::from_bip39_entropy(&entropy, &[])
}

pub fn get_root_key_from_mnemonic(phrase: &str) -> Bip32PrivateKey {
    let result = Mnemonic::from_phrase(phrase, Language::English).unwrap();

    let entropy = result.entropy();
    let root_key = get_root_key(&entropy);

    root_key
}

pub fn harden(index: u32) -> u32 {
    index | 0x80_00_00_00
}

pub fn get_baseaddress_from_mnemonic_by_networkInfo(phrase: &str, index: u32, network: NetworkInfo) -> String {
    let result = Mnemonic::from_phrase(phrase, Language::English).unwrap();

    let entropy = result.entropy();

    let root_key = get_root_key(&entropy);
    let spend = root_key
        .derive(harden(1852))
        .derive(harden(1815))
        .derive(harden(0))
        .derive(0)
        .derive(index)
        .to_public();

    let stake = root_key
        .derive(harden(1852))
        .derive(harden(1815))
        .derive(harden(0))
        .derive(2)
        .derive(0)
        .to_public();
    let spend_cred = StakeCredential::from_keyhash(&spend.to_raw_key().hash());
    let stake_cred = StakeCredential::from_keyhash(&stake.to_raw_key().hash());

    let addr_net_0 = BaseAddress::new(network.network_id(), &spend_cred, &stake_cred).to_address();

    addr_net_0.to_bech32(None).unwrap()
}

pub fn get_baseaddress_from_mnemonic(phrase: &str, index: u32, is_testnet: bool) -> String {
    let network = if is_testnet {
        NetworkInfo::testnet()
    } else {
        NetworkInfo::mainnet()
    };

    get_baseaddress_from_mnemonic_by_networkInfo(phrase, index, network)
}

pub fn get_enterpriseaddress_from_mnemonic_by_networkInfo(phrase: &str, index: u32, network: NetworkInfo) -> String {
    let result = Mnemonic::from_phrase(phrase, Language::English).unwrap();

    let entropy = result.entropy();

    let root_key = get_root_key(&entropy);
    let spend = root_key
        .derive(harden(1852))
        .derive(harden(1815))
        .derive(harden(0))
        .derive(0)
        .derive(index)
        .to_public();

    let spend_cred = StakeCredential::from_keyhash(&spend.to_raw_key().hash());
    let addr_net_0 = EnterpriseAddress::new(network.network_id(), &spend_cred).to_address();

    addr_net_0.to_bech32(None).unwrap()
}

pub fn get_enterpriseaddress_from_mnemonic(phrase: &str, index: u32, is_testnet: bool) -> String {
    let network = if is_testnet {
        NetworkInfo::testnet()
    } else {
        NetworkInfo::mainnet()
    };

    get_enterpriseaddress_from_mnemonic_by_networkInfo(phrase, index, network)
}

pub fn generate_mnemonic() -> String {
    let mnemonic = Mnemonic::new(MnemonicType::Words24, Language::English);
    let phrase = mnemonic.phrase();

    phrase.to_string()
}

pub fn bech32_address_to_bytes(address: &str) -> Vec<u8> {
    let address = Address::from_bech32(address).unwrap();
    address.to_bytes()
}

pub fn bytes_to_bech32_address(bytes: Vec<u8>) -> String {
    let address = Address::from_bytes(bytes);
    address.unwrap().to_bech32(None).unwrap()
}

pub fn get_private_key_from_mnemonic(phrase: &str, index: u32) -> String {
    let result = Mnemonic::from_phrase(phrase, Language::English).unwrap();

    let entropy = result.entropy();

    let root_key = get_root_key(&entropy);
    let spendKey = root_key
        .derive(harden(1852))
        .derive(harden(1815))
        .derive(harden(0))
        .derive(0)
        .derive(index)
        .to_bech32();

    spendKey
}

#[cfg(test)]
mod tests {
    use crate::address::{generate_mnemonic, get_baseaddress_from_mnemonic, get_enterpriseaddress_from_mnemonic, bech32_address_to_bytes, get_private_key_from_mnemonic};

    #[test]
    fn get_baseaddress_from_mnemonic_15words() {
        //15 words
        let phrase = "stairs same wheel damage taste amused dutch fly end tiger benefit leopard purity work year";
        // let entropy = [0xdf, 0x9e, 0xd2, 0x5e, 0xd1, 0x46, 0xbf, 0x43, 0x33, 0x6a, 0x5d, 0x7c, 0xf7, 0x39, 0x59, 0x94];
        let str = get_baseaddress_from_mnemonic(phrase, 0, false);
        println!(" Hello: {}", str);
    }

    #[test]
    fn get_baseaddress_from_mnemonic_mainnet() {
        //24 words
        let phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        let add0 = get_baseaddress_from_mnemonic(phrase, 0, false);
        let add1 = get_baseaddress_from_mnemonic(phrase, 1, false);
        let add2 = get_baseaddress_from_mnemonic(phrase, 2, false);

        //Actual
        //addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra
        //addr1q93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4zthxn
        //addr1q8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4qp6cs
        //addr1qxa5pll82u8lqtzqjqhdr828medvfvezv4509nzyuhwt5aql5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psy8jsmy
        //addr1qy8xr2tgn07lsp3jzn2vy22mvlpykn0zmggmfsuvcup3uwcl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psdvx58m

        assert_eq!(add0, "addr1qxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps7zwsra");
        assert_eq!(add1, "addr1q93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4zthxn");
        assert_eq!(add2, "addr1q8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8ps4qp6cs");
    }

    #[test]
    fn get_baseaddress_from_mnemonic_testnet() {
        //24 words
        let phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        let add0 = get_baseaddress_from_mnemonic(phrase, 0, true);
        let add1 = get_baseaddress_from_mnemonic(phrase, 1, true);
        let add2 = get_baseaddress_from_mnemonic(phrase, 2, true);

        //Actual
        // addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z
        // addr_test1qp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psk5kh2v
        // addr_test1qrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8pskku650

        assert_eq!(add0, "addr_test1qzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvsl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psa5ns0z");
        assert_eq!(add1, "addr_test1qp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8psk5kh2v");
        assert_eq!(add2, "addr_test1qrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgl5mr3vkp82y2kcwxxtu4zjcxvm80ttmx2hyeyjka4v8pskku650");
    }

    #[test]
    fn get_enterpriseaddress_from_mnemonic_mainnet() {
        //24 words
        let phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        let add0 = get_enterpriseaddress_from_mnemonic(phrase, 0, false);
        let add1 = get_enterpriseaddress_from_mnemonic(phrase, 1, false);
        let add2 = get_enterpriseaddress_from_mnemonic(phrase, 2, false);

        assert_eq!(add0, "addr1vxsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvstf7k4n");
        assert_eq!(add1, "addr1v93jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg7davae");
        assert_eq!(add2, "addr1v8pr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgvddj89");
     }

    #[test]
    fn get_enterpriseaddress_from_mnemonic_testnet() {
        //24 words
        let phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        let add0 = get_enterpriseaddress_from_mnemonic(phrase, 0, true);
        let add1 = get_enterpriseaddress_from_mnemonic(phrase, 1, true);
        let add2 = get_enterpriseaddress_from_mnemonic(phrase, 2, true);

        assert_eq!(add0, "addr_test1vzsaa6czesrzwp45rd5flg86n5hnwhz5setqfyt39natwvssp226k");
        assert_eq!(add1, "addr_test1vp3jwnn3hvgcuv02tqe08lpdkxxpmvapxgjxwewya47tqsg99fsju");
        assert_eq!(add2, "addr_test1vrpr30ykyfa3pw6qkkun3dyyxsvftq3xukuyxdt58pxcpxgh9ewgq");
    }

    #[test]
    fn generate_mnemonic_daedalus() {
        let mnemonic = generate_mnemonic();
        println!("{}", mnemonic);
    }

    #[test]
    fn test_bech32_address_to_bytes() {
        let add = "addr_test1qpu5vlrf4xkxv2qpwngf6cjhtw542ayty80v8dyr49rf5ewvxwdrt70qlcpeeagscasafhffqsxy36t90ldv06wqrk2qum8x5w";
        let bytes = bech32_address_to_bytes(add);
        assert_ne!(0, bytes.len())
    }

    #[test]
    fn test_get_private_key_from_mnemonic() {
        let mnemonic = "moment antenna hover credit bracket excess deny trial inspire sketch foster unable sphere toilet embody kit answer banner float position citizen bitter orphan can";
        let pvtKey = get_private_key_from_mnemonic(mnemonic, 0);

        let expected = "xprv17qvknep0qlfzxzwm7nhdukkr2ez00yhhf5tztqml4hun8yume30yedlqzlfvcg48v8xqx0a5q5us90pc09ct50d4938echyj6lvp0gvx5yasjh9w02vgaplsh9t892hc2gwvhjz5qv0l4jwq4hjj7pdgeg25rhsq";
        assert_eq!(expected, pvtKey);
    }
}
