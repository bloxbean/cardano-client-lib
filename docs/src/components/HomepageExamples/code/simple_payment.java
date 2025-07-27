// Create a simple payment transaction
String senderAddress = "addr_test1qp...";
String receiver1 = "addr_test1qr...";
String receiver2 = "addr_test1qs...";

Tx paymentTx = new Tx()
    .payToAddress(receiver1, Amount.ada(1.5))
    .payToAddress(receiver2, Amount.ada(2.5))
    .from(senderAddress);
