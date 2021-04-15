package com.bloxbean.cardano.client.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name="account",
        description = "Account specific commands",
        subcommands = {
                AccountGeneration.class,
                FromMnemonic.class
        }
)
public class AccountCommand implements Runnable{
    @Override
    public void run() {
        System.exit(-1);
    }
}
