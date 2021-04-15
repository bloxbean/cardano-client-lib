package com.bloxbean.cardano.client;

import com.bloxbean.cardano.client.jna.CardanoJNA;

public class Main {

    public static void main(String[] args) {
        if(args[0].equals("from-mnemonic")) {
            //String phrase24W = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";
            String mnemonic = args[1];
            int index = 0;
            if(args.length > 2)
                index = Integer.parseInt(args[2]);

            String network = "mainnet";
            if(args.length > 3)
                network = args[3];

            System.out.println(mnemonic);

            for(int i=0; i <= index; i++) {
                if("testnet".equals(network)) {
                    String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, true);
                    System.out.println(baseAddress);
                } else {
                    String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, false);
                    System.out.println(baseAddress);
                }
            }
        } else if(args[0].equals("generate")) {
            int index = 0;
            if(args.length > 1)
                index = Integer.parseInt(args[1]);

            String network = "mainnet";
            if(args.length > 2)
                network = args[2];

            String mnemonic = CardanoJNA.INSTANCE.generate_mnemonic();
            System.out.println(mnemonic);

            for(int i=0; i <= index; i++) {
                if("testnet".equals(network)) {
                    String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, true);
                    System.out.println(baseAddress);
                } else {
                    String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, false);
                    System.out.println(baseAddress);
                }
            }
        }
    }
}
