Tx tx = new Tx()
        .payToAddress(receiver1, Amount.ada(1.5))
        .payToAddress(receiver2, Amount.ada(2.5))
        .from(sender1Addr);
