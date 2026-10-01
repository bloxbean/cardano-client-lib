package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;

/**
 * Transactions at the maximum size (16,384 bytes) carrying the deepest nesting each position allows (ADR 0001 section
 * 6.7): the real preprod trigger and synthetic witness scripts, datums, redeemers and metadata. {@link TransactionBytes}
 * and {@link TransactionUtil} must slice and hash each one, and {@link TransactionSigner} must sign it, on any thread
 * and in any JVM mode.
 * {@link #main(String[])} runs them all so that a forked JVM (for example with {@code -Xint}) can run them too.
 */
final class TransactionBytesDepthFixtures {
    private static final int MAX_TX_SIZE = 16_384;
    private static final String BODY = "a3008001800200"; // {0: [], 1: [], 2: 0}
    private static final SecretKey SECRET_KEY = secretKey("ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807");

    private TransactionBytesDepthFixtures() {
    }

    static Map<String, byte[]> all() {
        Map<String, byte[]> txs = new LinkedHashMap<>();
        txs.put("preprod trigger (5,383-level native script)", RealCborFixtures.trigger().cbor());
        String script = "820181".repeat(5_410) + "8200581c" + "00".repeat(28);
        txs.put("witness native script (5,410 levels)", tx("a10181" + script, "f6"));
        txs.put("witness list datum (16,250 levels)", tx("a10481" + list(16_250), "f6"));
        txs.put("redeemer data, array form (16,240 levels)", tx("a105818400" + "00" + list(16_240) + "820000", "f6"));
        txs.put("redeemer data, map form (16,240 levels)", tx("a105a1820000" + "82" + list(16_240) + "820000", "f6"));
        txs.put("metadata (16,250 levels)", tx("a0", "a100" + list(16_250)));
        txs.put("indefinite witness list datum (8,000 levels)",
                tx("a10481" + "9f".repeat(8_000) + "00" + "ff".repeat(8_000), "f6"));
        return txs;
    }

    static void verify(String name, byte[] tx) {
        check(tx.length <= MAX_TX_SIZE, name + ": larger than the maximum transaction size");
        TransactionBytes transactionBytes = new TransactionBytes(tx);
        check(java.util.Arrays.equals(transactionBytes.getTxBytes(), tx), name + ": round trip");
        String expectedHash = encodeHexString(blake2bHash256(transactionBytes.getTxBodyBytes()));
        check(TransactionUtil.getTxHash(tx).equals(expectedHash), name + ": tx hash");
        check(java.util.Arrays.equals(TransactionUtil.extractTransactionBodyFromTx(tx), transactionBytes.getTxBodyBytes()),
                name + ": body");

        byte[] signed = TransactionSigner.INSTANCE.sign(tx, SECRET_KEY);
        TransactionBytesWitnessTest.assertOnlyVkeyWitnessAdded(tx, signed);
        check(TransactionUtil.getTxHash(signed).equals(expectedHash), name + ": signed tx hash");
        check(TransactionBytesWitnessTest.verifiesLastWitness(signed), name + ": signature");
    }

    public static void main(String[] args) {
        for (Map.Entry<String, byte[]> tx : all().entrySet()) {
            verify(tx.getKey(), tx.getValue());
            System.out.println("ok: " + tx.getKey());
        }
    }

    private static SecretKey secretKey(String hex) {
        try {
            return SecretKey.create(decodeHexString(hex));
        } catch (CborSerializationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] tx(String witnessesHex, String auxHex) {
        return BytesUtil.merge(decodeHexString("84" + BODY), decodeHexString(witnessesHex), decodeHexString("f5" + auxHex));
    }

    private static String list(int levels) {
        return "81".repeat(levels) + "00";
    }

    private static void check(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("depth fixture check failed: " + what);
    }
}
