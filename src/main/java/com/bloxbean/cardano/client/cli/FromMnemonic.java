package com.bloxbean.cardano.client.cli;

import com.bloxbean.cardano.client.jna.CardanoJNA;
import picocli.CommandLine;

@CommandLine.Command(
        name = "from-mnemonic",
        description = "Generate account(s) from mnemonic"
)
public class FromMnemonic implements Runnable {

    @CommandLine.Option(names = {"-mn", "--mnemonic"}, description = "Mnemonic", required = true)
    private String mnemonic;

    @CommandLine.Option(names = {"-n", "--network"}, description = "Network [mainnet | testnet]")
    private String network;

    @CommandLine.Option(names = {"-t", "--total"}, description = "Total number of accounts to generate")
    private int total;

    @Override
    public void run() {
        if (network == null || network.trim().length() == 0)
            network = "mainnet";

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
