package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

import java.nio.ByteBuffer;

public class TestRun {

    public static void main(String[] args) throws CborDeserializationException {
        Account account = new Account(Networks.testnet());
        System.out.println(account.mnemonic());
        System.out.println(account.baseAddress());
        System.out.println(account.enterpriseAddress());

//        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
//        String baseAddress = CardanoJNA.INSTANCE.get_address_by_network(phrase24W, 0, Networks.mainnet());
//        String baseAddress1 = CardanoJNA.INSTANCE.get_address_by_network(phrase24W, 0, Networks.testnet());
//        System.out.println("***" +baseAddress);
//        System.out.println("***" +baseAddress1);

        byte[] bytes = HexUtil.decodeHexString("6b8d07d69639e9413dd637a1a815a7323c69c86abbafb66dbfdb1aa7");
        System.out.println(bytes.length);

        ByteBuffer bb = ByteBuffer.wrap(bytes);

        byte[] policyId = new byte[28];
        byte[] asset = new byte[bytes.length - 28];

        bb.get(policyId, 0, policyId.length);
        bb.get(asset, 0, asset.length);

        System.out.println("Policy id: " + HexUtil.encodeHexString(policyId));
        System.out.println("Asset name: " + HexUtil.encodeHexString(asset));

        String txn = "83a4008182582064e450c5db698397c2379911122833cd60a53b58061d62561a49c17764dad090040182825839001c1ffaf141ebbb8e3a7072bb15f50f938b994c82de2d175f358fc942441f00edfe1b8d6a84f0d19c25a9c8829442160c0b5c758094c423441a0016e360825839008c5bf0f2af6f1ef08bb3f6ec702dd16e1c514b7e1d12f7549b47db9f4d943c7af0aaec774757d4745d1a2c8dd3220e6ec2c9df23f757a2f8821a2fce85d6a3581c329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96a147736174636f696e1b000000012a0416f4581c4c0ba27aaa43124c6205dcc1314cce1f297ad734877c6523ae7ff6aaa14974657374746f6b656e1a00030d40581ce1ae600b1e0e2dde90ff4749d192524b6f7d6e08ba8b99afceacab2ba14873656c66676966741a000186a0021a0002a82d031a01a465f2a100818258209518c18103cbdab9c6e60b58ecc3e2eb439fef6519bb22570f391327381900a858401df6d9ef80403377ee0ec8b8fec4d614f2f5e20fe26327755952dfc9edf88719b85b501e7d404c0b3ea6467cfa6fc1e4efe390ffddaf4cf49e4e7ca5dbe4df07f6";
        Transaction txnO = Transaction.deserialize(HexUtil.decodeHexString(txn));
        System.out.println(txnO);
    }
}
