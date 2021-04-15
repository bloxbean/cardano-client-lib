package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.jna.CardanoJNA;
import com.bloxbean.cardano.client.util.Networks;

public class TestRun {

    public static void main(String[] args) {
        System.out.println(System.getProperty("jna.library.path"));

        String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
        String baseAddress = CardanoJNA.INSTANCE.get_address_by_network(phrase24W, 0, Networks.mainnet());
        String baseAddress1 = CardanoJNA.INSTANCE.get_address_by_network(phrase24W, 0, Networks.testnet());
        System.out.println("***" +baseAddress);
        System.out.println("***" +baseAddress1);
    }
}
