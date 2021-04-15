package com.bloxbean.cardano.client.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        subcommands = {
                AccountCommand.class
        }
)
public class CLIMain implements Runnable {

    @Override
    public void run() {

    }

    public static void main(String[] args) {
        CommandLine.run(new CLIMain(), args);
    }
}
