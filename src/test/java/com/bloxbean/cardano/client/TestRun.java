package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;

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
    }
}
