package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class WitnessValidationRuleTest {

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    // A testnet VKey base address
    private static final String VKEY_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
    private static final String POLICY_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01";

    private WitnessValidationRule rule;

    @BeforeEach
    void setUp() {
        rule = new WitnessValidationRule();
    }

    private LedgerContext contextWithUtxos(Map<TransactionInput, TransactionOutput> utxoMap) {
        return LedgerContext.builder()
                .protocolParams(ProtocolParams.builder().build())
                .currentSlot(200)
                .utxoSlice(new SimpleUtxoSlice(utxoMap))
                .build();
    }

    private LedgerContext contextWithoutUtxos() {
        return LedgerContext.builder()
                .protocolParams(ProtocolParams.builder().build())
                .currentSlot(200)
                .build();
    }

    // --- Check 1: Required VKey witnesses present ---

    @Test
    void validate_missingVKeyWitness_shouldFail() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        TransactionOutput output = TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5_000_000)).build())
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, output);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithUtxos(utxoMap), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Missing required VKey witnesses"));
    }

    @Test
    void validate_noUtxoSlice_skipsVKeyCheck() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .build();

        // With no utxoSlice, VKey check should be skipped (no error about missing VKey witnesses)
        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("Missing required VKey witnesses"));
    }

    // --- Check 2: Ed25519 signature verification ---

    @Test
    void validate_invalidSignature_shouldFail() {
        // Create a VkeyWitness with random bytes that will fail Ed25519 verification
        byte[] fakeVkey = new byte[32];
        Arrays.fill(fakeVkey, (byte) 0x01);
        byte[] fakeSignature = new byte[64];
        Arrays.fill(fakeSignature, (byte) 0x02);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .vkeyWitnesses(List.of(new VkeyWitness(fakeVkey, fakeSignature)))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("Invalid Ed25519 signature")
                        || e.getMessage().contains("Signature verification failed"));
    }

    @Test
    void validate_nullVkeyOrSignature_shouldFail() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .vkeyWitnesses(List.of(new VkeyWitness(null, null)))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("null vkey or signature"));
    }

    // --- Checks 3 & 4: Script witnesses ---

    @Test
    void validate_missingScriptWitness_shouldFail() {
        // Mint requires a script witness for the policy ID
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID)
                .assets(List.of(Asset.builder().name("token").value(BigInteger.valueOf(100)).build()))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .mint(List.of(mint))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(
                contextWithUtxos(Collections.emptyMap()), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Missing script witnesses"));
    }

    @Test
    void validate_extraneousScriptWitness_shouldFail() {
        // Add a native script in the witness set that isn't required by the transaction
        ScriptPubkey extraScript = new ScriptPubkey("aabbccdd00112233445566778899aabb00112233445566778899aabb");

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .nativeScripts(List.of(extraScript))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(
                contextWithUtxos(Collections.emptyMap()), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Extraneous script witnesses"));
    }

    // --- Check 5: Native script evaluation ---

    @Test
    void validate_nativeScriptEvaluationFails_shouldFail() {
        // A ScriptPubkey that requires KEY_HASH_1, but we provide no VKey witnesses
        String keyHash = "aabbccdd00112233445566778899aabb00112233445566778899aabb";
        ScriptPubkey script = new ScriptPubkey(keyHash);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .nativeScripts(List.of(script))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Native script evaluation failed"));
    }

    @Test
    void validate_nativeScriptEvaluationPasses_noError() {
        String keyHash = "aabbccdd00112233445566778899aabb00112233445566778899aabb";
        ScriptPubkey script = new ScriptPubkey(keyHash);

        // Provide VKey witness that hashes to the required keyHash
        // We need a VKey whose blake2b-224 hash equals keyHash
        // This is hard to forge, so we'll do it the other way:
        // Create a random VKey, compute its hash, then use that hash as the script's keyHash
        byte[] vkey = new byte[32];
        Arrays.fill(vkey, (byte) 0xAB);
        byte[] vkeyHash = Blake2bUtil.blake2bHash224(vkey);
        String vkeyHashHex = HexUtil.encodeHexString(vkeyHash);

        ScriptPubkey scriptWithCorrectKey = new ScriptPubkey(vkeyHashHex);

        // We need a valid signature for the VkeyWitness, but for native script evaluation
        // the sig verification is a separate check. Native script eval only checks
        // if the key hash is in the witness VKey hashes. The sig will fail, but that's check 2.
        byte[] fakeSig = new byte[64];
        Arrays.fill(fakeSig, (byte) 0x01);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .nativeScripts(List.of(scriptWithCorrectKey))
                        .vkeyWitnesses(List.of(new VkeyWitness(vkey, fakeSig)))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        // No native script evaluation failure
        assertThat(errors).noneMatch(e -> e.getMessage().contains("Native script evaluation failed"));
        // But invalid signature is expected
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("Invalid Ed25519 signature")
                        || e.getMessage().contains("Signature verification failed"));
    }

    // --- Check 6: Metadata hash consistency ---

    @Test
    void validate_auxiliaryDataHashWithoutData_shouldFail() {
        byte[] fakeHash = new byte[32];
        Arrays.fill(fakeHash, (byte) 0xFF);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .auxiliaryDataHash(fakeHash)
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("auxiliaryDataHash is present but no AuxiliaryData"));
    }

    @Test
    void validate_auxiliaryDataWithMatchingHash_noError() {
        // When AuxiliaryData is present and auxiliaryDataHash is auto-computed during
        // serialization (which happens in signature validation), the hashes match.
        AuxiliaryData auxData = AuxiliaryData.builder().build();
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .auxiliaryData(auxData)
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        // No metadata-related errors because serialize() auto-computes the hash
        assertThat(errors).noneMatch(e -> e.getMessage().contains("auxiliaryData"));
    }

    @Test
    void validate_auxiliaryDataHashMismatch_shouldFail() {
        // Provide AuxiliaryData but with wrong hash in body
        byte[] wrongHash = new byte[32];
        Arrays.fill(wrongHash, (byte) 0xAA);

        AuxiliaryData auxData = AuxiliaryData.builder().build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .auxiliaryDataHash(wrongHash)
                        .build())
                .auxiliaryData(auxData)
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("auxiliaryDataHash mismatch"));
    }

    @Test
    void validate_noAuxiliaryDataNoHash_noError() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("auxiliaryData"));
    }

    // --- Check 7: ScriptDataHash consistency ---

    @Test
    void validate_scriptDataHashMissing_withRedeemers_shouldFail() {
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(BigIntPlutusData.of(42))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1000))
                        .steps(BigInteger.valueOf(2000))
                        .build())
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("scriptDataHash is missing but redeemers or datums are present"));
    }

    @Test
    void validate_scriptDataHashPresent_noRedeemersOrDatums_shouldFail() {
        byte[] fakeHash = new byte[32];
        Arrays.fill(fakeHash, (byte) 0xBB);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .scriptDataHash(fakeHash)
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e ->
                e.getMessage().contains("scriptDataHash is present but no redeemers or datums"));
    }

    @Test
    void validate_noScriptData_noHash_noError() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("scriptDataHash"));
    }

    // --- Checks 8 & 9: Datum validation ---

    @Test
    void validate_missingRequiredDatum_shouldFail() {
        byte[] datumHash = new byte[32];
        Arrays.fill(datumHash, (byte) 0xCC);

        TransactionInput input = new TransactionInput(TX_HASH, 0);
        TransactionOutput output = TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5_000_000)).build())
                .datumHash(datumHash)
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, output);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder().build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithUtxos(utxoMap), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Missing required datums"));
    }

    @Test
    void validate_extraneousDatum_shouldFail() {
        // Provide a datum in witness set that doesn't match any input/output datum hash
        PlutusData extraDatum = BigIntPlutusData.of(9999);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .plutusDataList(List.of(extraDatum))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(
                contextWithUtxos(Collections.emptyMap()), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Extraneous supplemental datums"));
    }

    @Test
    void validate_datumMatchesOutputHash_notExtraneous() {
        // Datum in witness matches a datum hash on a tx output — should NOT be extraneous
        PlutusData datum = BigIntPlutusData.of(42);
        byte[] datumHash = datum.getDatumHashAsBytes();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of(TransactionOutput.builder()
                                .address(VKEY_ADDR)
                                .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).build())
                                .datumHash(datumHash)
                                .build()))
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .plutusDataList(List.of(datum))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(
                contextWithUtxos(Collections.emptyMap()), tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("Extraneous supplemental datums"));
    }

    // --- Check 10: Redeemers match scripts ---

    @Test
    void validate_redeemerWithValidSpendIndex_noError() {
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.ZERO)
                .data(BigIntPlutusData.of(1))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1000))
                        .steps(BigInteger.valueOf(2000))
                        .build())
                .build();

        byte[] sdh = new byte[32];
        Arrays.fill(sdh, (byte) 0x11);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .scriptDataHash(sdh)
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).noneMatch(e -> e.getMessage().contains("does not correspond"));
    }

    @Test
    void validate_redeemerWithOutOfBoundsIndex_shouldFail() {
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .index(BigInteger.valueOf(5)) // Only 1 input (index 0)
                .data(BigIntPlutusData.of(1))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1000))
                        .steps(BigInteger.valueOf(2000))
                        .build())
                .build();

        byte[] sdh = new byte[32];
        Arrays.fill(sdh, (byte) 0x11);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .scriptDataHash(sdh)
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("does not correspond"));
    }

    @Test
    void validate_mintRedeemerWithoutMint_shouldFail() {
        Redeemer redeemer = Redeemer.builder()
                .tag(RedeemerTag.Mint)
                .index(BigInteger.ZERO)
                .data(BigIntPlutusData.of(1))
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(1000))
                        .steps(BigInteger.valueOf(2000))
                        .build())
                .build();

        byte[] sdh = new byte[32];
        Arrays.fill(sdh, (byte) 0x11);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .scriptDataHash(sdh)
                        .build())
                .witnessSet(TransactionWitnessSet.builder()
                        .redeemers(List.of(redeemer))
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("Mint[0] does not correspond"));
    }

    // --- Combined checks ---

    @Test
    void validate_cleanTransaction_noWitnesses_noErrors() {
        // A bare-minimum transaction with no witnesses, no scripts, no metadata
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .build())
                .build();

        // Without utxo slice, most checks are skipped
        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).isEmpty();
    }

    @Test
    void validate_allRuleNamesAreWitnessValidation() {
        // Verify all errors produced by the rule carry the correct rule name
        byte[] fakeHash = new byte[32];
        Arrays.fill(fakeHash, (byte) 0xFF);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200_000))
                        .auxiliaryDataHash(fakeHash)
                        .build())
                .build();

        List<ValidationError> errors = rule.validate(contextWithoutUtxos(), tx);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allMatch(e -> "WitnessValidation".equals(e.getRule()));
    }
}
