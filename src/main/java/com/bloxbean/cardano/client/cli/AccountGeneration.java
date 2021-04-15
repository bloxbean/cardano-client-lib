package com.bloxbean.cardano.client.cli;

import com.bloxbean.cardano.client.jna.CardanoJNA;
import picocli.CommandLine;

@CommandLine.Command(
        name = "generate",
        description = "Generate accounts"
)
public class AccountGeneration implements Runnable {

    @CommandLine.Option(names = {"-n", "--network"}, description = "Network [mainnet | testnet]")
    private String network;

    @CommandLine.Option(names = {"-t", "--total"}, description = "Total number of accounts to generate")
    private int total;

    @Override
    public void run() {
        if (network == null || network.trim().length() == 0)
            network = "mainnet";

        String mnemonic = CardanoJNA.INSTANCE.generate_mnemonic();
        System.out.println("Mnemonic  : " + mnemonic);

        for (int i = 0; i <= total; i++) {
            if ("testnet".equals(network)) {
                String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, true);
                System.out.println("Address-" + i + ": " + baseAddress);
            } else {
                String baseAddress = CardanoJNA.INSTANCE.get_address(mnemonic, i, false);
                System.out.println("Address-" + i + ": " + baseAddress);
            }
        }
    }
}
