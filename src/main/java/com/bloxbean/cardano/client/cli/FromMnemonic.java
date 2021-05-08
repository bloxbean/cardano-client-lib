package com.bloxbean.cardano.client.cli;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
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

    @CommandLine.Option(names = {"-ea", "--enterprise-address"}, description = "Generate enterprise address")
    private boolean entAddress;

    @Override
    public void run() {
        if (network == null || network.trim().length() == 0)
            network = "mainnet";

        Account account = null;
        if("testnet".equals(network)) {
            account = new Account(Networks.testnet(), mnemonic);
        } else {
            account = new Account(mnemonic);
        }

        for (int i = 0; i <= total; i++) {
            account = null;

            if("testnet".equals(network)) {
                account = new Account(Networks.testnet(), mnemonic, i);
            } else {
                account = new Account(mnemonic, i);
            }
            System.out.println(" ");
            System.out.println("Base Address-" + i + ": " + account.baseAddress());
            if(entAddress) {
                System.out.println("Ent Address -" + i +  ": " + account.enterpriseAddress());
            }
        }
    }
}
