package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TransactionBytes#withVkeyWitness(byte[], byte[])} and the signer that delegates to it (ADR 0001 D5): the new
 * vkey witness is spliced into witness set field 0 and every other byte is kept.
 */
class TransactionBytesWitnessTest {
    private static final byte[] VKEY = decodeHexString("60209269377f220cdecdc6d5ad42d9b04e58ce74b349efb396ee46adaeb956f3");
    private static final byte[] SIGNATURE = decodeHexString("9b14acd2799e8b97e4be5b02ee344d9620af9d5c24cdf6b50aa4e706f996e9a1"
            + "1747128f62219bc5a5e3b8a3c4fa6881e9d63144ec8448a413edefe018d6a706");
    private static final String WITNESS = "825820" + encodeHexString(VKEY) + "5840" + encodeHexString(SIGNATURE);
    private static final String OTHER = witness(1);
    private static final String BODY = "a3008001800200";

    @Test
    void matchesTheOldSignerWhereItsReencodingWasLossless() {
        int compared = 0;
        int total = 0;
        for (RealCborFixtures.Tx tx : RealCborFixtures.txs()) {
            TransactionBytes transactionBytes = new TransactionBytes(tx.cbor());
            byte[] signed = transactionBytes.withVkeyWitness(VKEY, SIGNATURE).getTxBytes();
            assertOnlyVkeyWitnessAdded(tx.cbor(), signed);
            total++;

            byte[] witnesses = transactionBytes.getTxWitnessBytes();
            boolean lossless = Arrays.equals(witnesses,
                    serialize(CborSerializationUtil.deserialize(witnesses)));
            if (lossless) {
                assertThat(signed).as(tx.toString())
                        .isEqualTo(LegacyTransactionSigner.addWitnessToTransaction(transactionBytes, VKEY, SIGNATURE));
                compared++;
            }
        }
        assertThat(compared).as("real txs whose witness set re-encodes losslessly").isGreaterThan(total / 2);
    }

    @ParameterizedTest(name = "{0} vkey witnesses")
    @ValueSource(ints = {0, 1, 22, 23, 24, 254, 255, 256, 65_534, 65_535, 65_536})
    void vkeyWitnessCountCrossesEveryHeaderWidth(int count) {
        byte[] tx = tx("a100" + distinctWitnesses(count));
        byte[] signed = new TransactionBytes(tx).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();

        assertOnlyVkeyWitnessAdded(tx, signed);
        assertThat(signed).isEqualTo(LegacyTransactionSigner.addWitnessToTransaction(new TransactionBytes(tx), VKEY, SIGNATURE));
        CborSpan field0 = CborSpan.of(new TransactionBytes(signed).getTxWitnessBytes()).field(0).orElseThrow();
        assertThat(field0.headerLength()).isEqualTo(headerLength(count + 1));
    }

    @ParameterizedTest(name = "{0} witness set entries without field 0")
    @ValueSource(ints = {0, 1, 22, 23, 254, 255, 65_534, 65_535})
    void witnessSetCountCrossesEveryHeaderWidth(int entries) {
        StringBuilder map = new StringBuilder(head(5, entries));
        for (int key = 1; key <= entries; key++)
            map.append(head(0, key)).append("80");
        byte[] tx = tx(map.toString());
        byte[] signed = new TransactionBytes(tx).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();

        assertOnlyVkeyWitnessAdded(tx, signed);
        assertThat(signed).isEqualTo(LegacyTransactionSigner.addWitnessToTransaction(new TransactionBytes(tx), VKEY, SIGNATURE));
        CborSpan witnessSet = CborSpan.of(new TransactionBytes(signed).getTxWitnessBytes());
        assertThat(witnessSet.headerLength()).isEqualTo(headerLength(entries + 1));
        List<Map.Entry<CborSpan, CborSpan>> entryList = witnessSet.entries();
        assertThat(encodeHexString(entryList.get(entryList.size() - 1).getKey().bytes())).isEqualTo("00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d90102", "da00000102", "db0000000000000102"})
    void setTagIsKeptAsEncoded(String tag) {
        byte[] tx = tx("a100" + tag + "81" + OTHER);
        byte[] signed = new TransactionBytes(tx).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();

        assertOnlyVkeyWitnessAdded(tx, signed);
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes())).isEqualTo("a100" + tag + "82" + OTHER + WITNESS);
    }

    @Test
    void indefiniteContainersGetTheWitnessBeforeTheirBreak() {
        byte[] indefiniteArray = tx("a1009f" + OTHER + "ff");
        byte[] signed = new TransactionBytes(indefiniteArray).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();
        assertOnlyVkeyWitnessAdded(indefiniteArray, signed);
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes())).isEqualTo("a1009f" + OTHER + WITNESS + "ff");

        byte[] indefiniteMap = tx("bf0180ff");
        signed = new TransactionBytes(indefiniteMap).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();
        assertOnlyVkeyWitnessAdded(indefiniteMap, signed);
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes())).isEqualTo("bf0180" + "0081" + WITNESS + "ff");
    }

    @Test
    void nonCanonicalWitnessFieldsKeepTheirBytes() {
        // {1: [_ native script], 4: [_ datum with chunked bytes and a non-minimal int], 5: [[0, 0, datum, [1, 2]]],
        //  0 (non-minimal key, last): [witness]}
        String nativeScripts = "019f8200581c" + "00".repeat(28) + "ff";
        String datums = "049fd8799f5f41aa41bbff1800ffff";
        String redeemers = "0581840000d87980820102";
        byte[] tx = tx("a4" + nativeScripts + datums + redeemers + "180081" + OTHER);
        byte[] signed = new TransactionBytes(tx).withVkeyWitness(VKEY, SIGNATURE).getTxBytes();

        assertOnlyVkeyWitnessAdded(tx, signed);
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes()))
                .isEqualTo("a4" + nativeScripts + datums + redeemers + "180082" + OTHER + WITNESS);
        // the old signer re-encoded the datum (chunks joined, 18 00 shortened), changing the script data hash preimage
        assertThat(LegacyTransactionSigner.addWitnessToTransaction(new TransactionBytes(tx), VKEY, SIGNATURE))
                .isNotEqualTo(signed);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "field 0 tagged 259, a100d9010381" + "f6",
            "field 0 is a map, a100a0",
            "field 0 is bytes, a10040",
            "duplicate key 0, a2008000" + "80",
            "duplicate key 0 non-minimal, a20080180080",
            "witness set is an array, 80",
            "witness set is tagged, d90102a0",
    })
    void malformedWitnessSetsAreRejected(String scenario, String witnessSet) {
        TransactionBytes transactionBytes = new TransactionBytes(tx(witnessSet));
        assertThatThrownBy(() -> transactionBytes.withVkeyWitness(VKEY, SIGNATURE))
                .as(scenario)
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void withVkeyWitnessSkipsAKeyThatAlreadySigned() {
        byte[] tx = tx("a100" + "82" + OTHER + WITNESS);
        byte[] otherSignature = decodeHexString("ff".repeat(64));
        assertThat(new TransactionBytes(tx).withVkeyWitness(VKEY, otherSignature).getTxBytes()).isEqualTo(tx);
        assertThat(new TransactionBytes(tx).withVkeyWitness(VKEY, SIGNATURE).getTxBytes()).isEqualTo(tx);
    }

    /**
     * Each real transaction is taken apart into the unsigned transaction (its witness set without fields 0 and 2) and
     * the signatures, as a CIP-30 wallet would return them. Adding them back keeps the tx id and every other witness
     * field, and every real signature verifies against the body. A witness repeated for the same vkey (mainnet Mary tx
     * f3ba8c19… carries one three times) is added once.
     */
    @Test
    void withSignaturesFromReassemblesRealTransactionsFromTheirOwnSignatures() {
        int verified = 0;
        for (RealCborFixtures.Tx tx : RealCborFixtures.allTxs()) {
            TransactionBytes original = new TransactionBytes(tx.cbor());
            CborSpan witnessSet = CborSpan.of(original.getTxWitnessBytes());
            byte[] unsigned = original.withNewWitnessSetBytes(witnessSetWith(witnessSet, false)).getTxBytes();
            byte[] signatures = witnessSetWith(witnessSet, true);

            byte[] signed = new TransactionBytes(unsigned).withSignaturesFrom(signatures).getTxBytes();

            assertThat(TransactionUtil.getTxHash(signed)).as(tx.toString()).isEqualTo(tx.txHash());
            CborSpan signedWitnesses = CborSpan.of(new TransactionBytes(signed).getTxWitnessBytes());
            assertThat(otherFields(signedWitnesses)).containsExactlyElementsOf(otherFields(witnessSet));
            for (long field : new long[]{0, 2}) {
                assertThat(itemsHex(signedWitnesses, field)).as(tx + " field " + field)
                        .isEqualTo(firstPerVkey(witnessSet, field));
                verified += verifyAll(signed, field);
            }
        }
        assertThat(verified).isGreaterThan(80);
    }

    @Test
    void withSignaturesFromKeepsExistingWitnesses() {
        String nativeScripts = "01818200581c" + "00".repeat(28);
        String datums = "049fd8799f5f41aa41bbff1800ffff";
        String redeemers = "05a182000082d8798082190400190400";
        byte[] tx = tx("a4" + nativeScripts + datums + redeemers + "0081" + OTHER);

        byte[] signed = new TransactionBytes(tx)
                .withSignaturesFrom(decodeHexString("a10082" + witness(2) + witness(3)))
                .getTxBytes();

        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes()))
                .isEqualTo("a4" + nativeScripts + datums + redeemers + "0083" + OTHER + witness(2) + witness(3));
        assertThat(TransactionUtil.getTxHash(signed)).isEqualTo(TransactionUtil.getTxHash(tx));
    }

    @Test
    void withSignaturesFromWorksForSeveralSignersInAnyOrder() {
        byte[] tx = tx("a1" + nativeScriptField());
        byte[] walletA = decodeHexString("a10081" + witness(2));
        byte[] walletB = decodeHexString("a100d9010282" + witness(3) + witness(4));

        byte[] ab = new TransactionBytes(tx).withSignaturesFrom(walletA).withSignaturesFrom(walletB).getTxBytes();
        byte[] ba = new TransactionBytes(tx).withSignaturesFrom(walletB).withSignaturesFrom(walletA).getTxBytes();

        assertThat(TransactionUtil.getTxHash(ab)).isEqualTo(TransactionUtil.getTxHash(ba)).isEqualTo(TransactionUtil.getTxHash(tx));
        assertThat(itemsHex(CborSpan.of(new TransactionBytes(ab).getTxWitnessBytes()), 0))
                .containsExactly(witness(2), witness(3), witness(4));
        assertThat(itemsHex(CborSpan.of(new TransactionBytes(ba).getTxWitnessBytes()), 0))
                .containsExactly(witness(3), witness(4), witness(2));
        // adding the same signatures again changes nothing
        assertThat(new TransactionBytes(ab).withSignaturesFrom(walletA).withSignaturesFrom(walletB).getTxBytes()).isEqualTo(ab);
        assertThat(new TransactionBytes(ab).withSignaturesFrom(decodeHexString("a0")).getTxBytes()).isEqualTo(ab);
    }

    @Test
    void withSignaturesFromKeepsTheTransactionsSetTag() {
        byte[] tx = tx("a100da0000010281" + OTHER);
        byte[] signed = new TransactionBytes(tx).withSignaturesFrom(decodeHexString("a100d9010281" + witness(2))).getTxBytes();
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes()))
                .isEqualTo("a100da0000010282" + OTHER + witness(2));
    }

    @Test
    void withSignaturesFromAddsBootstrapWitnesses() {
        String bootstrap = "845820" + "11".repeat(32) + "5840" + "22".repeat(64) + "5820" + "33".repeat(32) + "41a0";
        byte[] tx = tx("a10081" + OTHER);
        byte[] signed = new TransactionBytes(tx).withSignaturesFrom(decodeHexString("a10281" + bootstrap)).getTxBytes();
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes()))
                .isEqualTo("a2" + "0081" + OTHER + "0281" + bootstrap);
        assertThat(new TransactionBytes(signed).withSignaturesFrom(decodeHexString("a10281" + bootstrap)).getTxBytes())
                .isEqualTo(signed);
    }

    @Test
    void withSignaturesFromSkipsAnIdenticalEchoedFieldAndRejectsOthers() {
        byte[] tx = tx("a2" + nativeScriptField() + "0081" + OTHER);
        // a wallet that returns the whole witness set: the native script field is identical, so it is skipped
        byte[] signed = new TransactionBytes(tx)
                .withSignaturesFrom(decodeHexString("a2" + nativeScriptField() + "0082" + OTHER + witness(2)))
                .getTxBytes();
        assertThat(encodeHexString(new TransactionBytes(signed).getTxWitnessBytes()))
                .isEqualTo("a2" + nativeScriptField() + "0082" + OTHER + witness(2));

        for (String rejected : new String[]{
                "a101818200581c" + "ff".repeat(28),   // a different native script
                "a10481d87980",                       // datums the transaction does not have
                "a161780100",                         // a key that is not an unsigned integer
                "a200800080",                         // duplicate key 0
                "a100a0",                             // field 0 is not an array
                "80"}) {                              // not a map
            TransactionBytes transactionBytes = new TransactionBytes(tx);
            assertThatThrownBy(() -> transactionBytes.withSignaturesFrom(decodeHexString(rejected)))
                    .as(rejected)
                    .isInstanceOf(CborRuntimeException.class);
        }
        // an empty signature field adds nothing
        assertThat(new TransactionBytes(tx).withSignaturesFrom(decodeHexString("a10080")).getTxBytes()).isEqualTo(tx);
    }

    @Test
    void signerAddsAVerifiableWitness() throws Exception {
        SecretKey secretKey = SecretKey.create(decodeHexString("ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807"));
        for (RealCborFixtures.Tx tx : RealCborFixtures.allTxs()) {
            byte[] signed = TransactionSigner.INSTANCE.sign(tx.cbor(), secretKey);
            assertOnlyVkeyWitnessAdded(tx.cbor(), signed);
            assertThat(TransactionUtil.getTxHash(signed)).isEqualTo(tx.txHash());
            assertThat(verifiesLastWitness(signed)).as(tx.toString()).isTrue();
        }
    }

    /**
     * Signing a {@link Transaction} model serializes it with cbor-java, signs those bytes and deserializes the result.
     * The body hash is the hash of the model's own serialization, as before, and the signed model serializes to the same
     * bytes as with the old decode/re-encode signer.
     */
    @Test
    void signingATransactionModelGivesTheSameTransactionAsBefore() throws Exception {
        SecretKey secretKey = SecretKey.create(decodeHexString("ede3104b2f4ff32daa3b620a9a272cd962cf504da44cf1cf0280aff43b65f807"));
        int signed = 0;
        for (RealCborFixtures.Tx tx : RealCborFixtures.txs()) {
            Transaction model;
            try {
                model = Transaction.deserialize(tx.cbor());
            } catch (Exception e) {
                continue; // model gaps out of scope here, e.g. Shelley 3-element txs with metadata
            }
            byte[] modelBytes = model.serialize();

            Transaction newSigned = TransactionSigner.INSTANCE.sign(model, secretKey);

            byte[] newSignedBytes = TransactionSigner.INSTANCE.sign(modelBytes, secretKey);
            List<CborSpan> vkeys = CborSpan.of(new TransactionBytes(newSignedBytes).getTxWitnessBytes())
                    .field(0).orElseThrow().untagIf(258).items();
            CborSpan witness = vkeys.get(vkeys.size() - 1);
            Transaction oldSigned = Transaction.deserialize(LegacyTransactionSigner.addWitnessToTransaction(
                    new TransactionBytes(modelBytes), witness.get(0).byteString(), witness.get(1).byteString()));

            assertThat(newSigned.serialize()).as(tx.toString()).isEqualTo(oldSigned.serialize());
            assertThat(TransactionUtil.getTxHash(newSigned)).isEqualTo(TransactionUtil.getTxHash(model));
            assertThat(verifiesLastWitness(newSigned.serialize())).isTrue();
            signed++;
        }
        assertThat(signed).isGreaterThan(50);
    }

    /**
     * Checks that {@code signed} is {@code original} with exactly one vkey witness appended to witness set field 0, and
     * that every other byte (envelope, body, validity, aux data, other witness fields, the field 0 tag) is unchanged.
     */
    static void assertOnlyVkeyWitnessAdded(byte[] original, byte[] signed) {
        TransactionBytes before = new TransactionBytes(original);
        TransactionBytes after = new TransactionBytes(signed);
        assertThat(after.getInitialBytes()).isEqualTo(before.getInitialBytes());
        assertThat(after.getTxBodyBytes()).isEqualTo(before.getTxBodyBytes());
        assertThat(after.getValidBytes()).isEqualTo(before.getValidBytes());
        assertThat(after.getAuxiliaryDataBytes()).isEqualTo(before.getAuxiliaryDataBytes());

        CborSpan witnessesBefore = CborSpan.of(before.getTxWitnessBytes());
        CborSpan witnessesAfter = CborSpan.of(after.getTxWitnessBytes());
        assertThat(otherFields(witnessesAfter)).containsExactlyElementsOf(otherFields(witnessesBefore));

        List<String> vkeysBefore = new ArrayList<>();
        String tagBefore = "";
        if (witnessesBefore.field(0).isPresent()) {
            CborSpan field0 = witnessesBefore.field(0).get();
            tagBefore = tagPrefix(field0);
            field0.untagIf(258).items().forEach(item -> vkeysBefore.add(encodeHexString(item.bytes())));
        }
        CborSpan field0After = witnessesAfter.field(0).orElseThrow();
        assertThat(tagPrefix(field0After)).isEqualTo(tagBefore);
        List<CborSpan> vkeysAfter = field0After.untagIf(258).items();
        assertThat(vkeysAfter).hasSize(vkeysBefore.size() + 1);
        for (int i = 0; i < vkeysBefore.size(); i++)
            assertThat(encodeHexString(vkeysAfter.get(i).bytes())).isEqualTo(vkeysBefore.get(i));
    }

    static boolean verifiesLastWitness(byte[] signedTx) {
        TransactionBytes transactionBytes = new TransactionBytes(signedTx);
        List<CborSpan> vkeys = CborSpan.of(transactionBytes.getTxWitnessBytes()).field(0).orElseThrow().untagIf(258).items();
        CborSpan witness = vkeys.get(vkeys.size() - 1);
        return CryptoConfiguration.INSTANCE.getSigningProvider().verify(witness.get(1).byteString(),
                Blake2bUtil.blake2bHash256(transactionBytes.getTxBodyBytes()), witness.get(0).byteString());
    }

    private static List<String> otherFields(CborSpan witnessSet) {
        List<String> fields = new ArrayList<>();
        for (Map.Entry<CborSpan, CborSpan> entry : witnessSet.entries()) {
            if (entry.getKey().majorType() == 0 && entry.getKey().asLong() == 0)
                continue;
            fields.add(encodeHexString(entry.getKey().bytes()) + ":" + encodeHexString(entry.getValue().bytes()));
        }
        return fields;
    }

    private static String tagPrefix(CborSpan span) {
        CborSpan untagged = span.untagIf(258);
        return encodeHexString(Arrays.copyOfRange(span.buffer(), span.offset(), untagged.offset()));
    }

    // A witness set with only the signature fields (0 and 2) of the given one, or with everything else.
    private static byte[] witnessSetWith(CborSpan witnessSet, boolean signatures) {
        List<Map.Entry<CborSpan, CborSpan>> kept = new ArrayList<>();
        for (Map.Entry<CborSpan, CborSpan> entry : witnessSet.entries()) {
            long key = entry.getKey().asLong();
            if ((key == 0 || key == 2) == signatures)
                kept.add(entry);
        }
        StringBuilder out = new StringBuilder(head(5, kept.size()));
        kept.forEach(e -> out.append(encodeHexString(e.getKey().bytes())).append(encodeHexString(e.getValue().bytes())));
        return decodeHexString(out.toString());
    }

    private static List<String> itemsHex(CborSpan witnessSet, long field) {
        List<String> items = new ArrayList<>();
        witnessSet.field(field).ifPresent(f -> f.untagIf(258).items().forEach(item -> items.add(encodeHexString(item.bytes()))));
        return items;
    }

    private static List<String> firstPerVkey(CborSpan witnessSet, long field) {
        List<String> items = new ArrayList<>();
        java.util.Set<String> vkeys = new java.util.HashSet<>();
        witnessSet.field(field).ifPresent(f -> f.untagIf(258).items().forEach(item -> {
            if (vkeys.add(encodeHexString(item.get(0).byteString())))
                items.add(encodeHexString(item.bytes()));
        }));
        return items;
    }

    // Verifies every vkey or bootstrap signature in a field against the transaction body hash.
    private static int verifyAll(byte[] signedTx, long field) {
        TransactionBytes transactionBytes = new TransactionBytes(signedTx);
        byte[] txHash = Blake2bUtil.blake2bHash256(transactionBytes.getTxBodyBytes());
        int verified = 0;
        for (CborSpan witness : CborSpan.of(transactionBytes.getTxWitnessBytes()).field(field)
                .map(f -> f.untagIf(258).items()).orElse(List.of())) {
            assertThat(CryptoConfiguration.INSTANCE.getSigningProvider()
                    .verify(witness.get(1).byteString(), txHash, witness.get(0).byteString())).isTrue();
            verified++;
        }
        return verified;
    }

    private static String nativeScriptField() {
        return "01818200581c" + "00".repeat(28);
    }

    // A vkey witness with a vkey derived from n, distinct for every n.
    private static String witness(int n) {
        String vkey = String.format("%08x", n) + "ab".repeat(28);
        return "825820" + vkey + "5840" + "cd".repeat(64);
    }

    private static String distinctWitnesses(int count) {
        StringBuilder out = new StringBuilder(head(4, count));
        for (int i = 0; i < count; i++)
            out.append(witness(1_000 + i));
        return out.toString();
    }

    private static byte[] tx(String witnessSetHex) {
        return BytesUtil.merge(decodeHexString("84" + BODY), decodeHexString(witnessSetHex), decodeHexString("f5f6"));
    }

    private static String head(int major, long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int type = major << 5;
        if (value < 24) {
            out.write(type | (int) value);
        } else {
            int width = value < 0x100 ? 1 : value < 0x10000 ? 2 : 4;
            out.write(type | (24 + Integer.numberOfTrailingZeros(width)));
            for (int i = width - 1; i >= 0; i--)
                out.write((int) (value >> (8 * i)));
        }
        return encodeHexString(out.toByteArray());
    }

    private static int headerLength(long count) {
        return head(4, count).length() / 2;
    }

    private static byte[] serialize(co.nstant.in.cbor.model.DataItem item) {
        try {
            return CborSerializationUtil.serialize(item, false);
        } catch (co.nstant.in.cbor.CborException e) {
            throw new IllegalStateException(e);
        }
    }
}
