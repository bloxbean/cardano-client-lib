package com.bloxbean.cardano.client.cli;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.jna.CardanoJNA;
import com.bloxbean.cardano.client.util.Networks;
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

    @CommandLine.Option(names = {"-ea", "--enterprise-address"}, description = "Generate enterprise address")
    private boolean entAddress;

    @Override
    public void run() {
        if (network == null || network.trim().length() == 0)
            network = "mainnet";

        Account account = null;

        if("testnet".equals(network)) {
            account = new Account(Networks.testnet());
        } else {
            account = new Account();
        }

        String mnemonic = account.mnemonic();
        System.out.println("Mnemonic  : " + mnemonic);

        for (int i = 0; i <= total; i++) {
            System.out.println(" ");
            System.out.println("Base Address-" + i + ": " + account.baseAddress(i));
            if(entAddress) {
                System.out.println("Ent Address -" + i + ": " + account.enterpriseAddress(i));
            }
        }
    }
}
