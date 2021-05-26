package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.util.HexUtil;

import java.nio.ByteBuffer;

public class TestRun {

    public static void main(String[] args) {
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
    }
}
