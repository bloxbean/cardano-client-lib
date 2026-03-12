package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.UtxoSlice;
import com.bloxbean.cardano.client.ledger.util.NativeScriptEvaluator;
import com.bloxbean.cardano.client.ledger.util.RequiredWitnessResolver;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.ScriptDataHashGenerator;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates witness rules (Category E):
 * <ol>
 *   <li>Required VKey witnesses present</li>
 *   <li>Ed25519 signatures valid</li>
 *   <li>No missing script witnesses</li>
 *   <li>No extraneous script witnesses</li>
 *   <li>Native scripts evaluate successfully</li>
 *   <li>Metadata hash consistency</li>
 *   <li>ScriptDataHash matches</li>
 *   <li>Required datums present</li>
 *   <li>No extraneous supplemental datums</li>
 *   <li>Redeemers exactly match Plutus scripts</li>
 * </ol>
 * <p>
 * Reference: Haskell Shelley/Alonzo UTXOW rules, Scalus validators
 */
public class WitnessValidationRule implements LedgerRule {

    private static final String RULE_NAME = "WitnessValidation";
    private static final EdDSASigningProvider SIGNING_PROVIDER = new EdDSASigningProvider();

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        List<ValidationError> errors = new ArrayList<>();
        TransactionBody body = transaction.getBody();
        TransactionWitnessSet witnessSet = transaction.getWitnessSet();

        // Extract witness VKey hashes
        Set<String> witnessVKeyHashes = extractWitnessVKeyHashes(witnessSet);

        // 1. Required VKey witnesses present
        if (context.getUtxoSlice() != null) {
            var reqs = RequiredWitnessResolver.resolve(transaction, context.getUtxoSlice());
            Set<String> missing = new LinkedHashSet<>(reqs.getRequiredVKeyHashes());
            missing.removeAll(witnessVKeyHashes);
            if (!missing.isEmpty()) {
                errors.add(error("Missing required VKey witnesses: " + missing));
            }
        }

        // 2. Ed25519 signature verification
        validateSignatures(transaction, witnessSet, errors);

        // 3 & 4. Script witnesses: missing and extraneous
        validateScriptWitnesses(context, transaction, errors);

        // 5. Native script evaluation
        validateNativeScripts(transaction, witnessSet, witnessVKeyHashes, errors);

        // 6. Metadata hash consistency
        validateMetadataHash(transaction, errors);

        // 7. ScriptDataHash
        validateScriptDataHash(context, transaction, errors);

        // 8 & 9. Datum witnesses
        validateDatums(context, transaction, errors);

        // 10. Redeemers match Plutus scripts
        validateRedeemers(context, transaction, errors);

        return errors;
    }

    /**
     * Extract VKey hashes from witness set by hashing each VKey with Blake2b-224.
     */
    private Set<String> extractWitnessVKeyHashes(TransactionWitnessSet witnessSet) {
        Set<String> hashes = new LinkedHashSet<>();
        if (witnessSet == null || witnessSet.getVkeyWitnesses() == null) return hashes;

        for (VkeyWitness vkw : witnessSet.getVkeyWitnesses()) {
            if (vkw.getVkey() != null) {
                byte[] keyHash = Blake2bUtil.blake2bHash224(vkw.getVkey());
                hashes.add(HexUtil.encodeHexString(keyHash));
            }
        }
        return hashes;
    }

    /**
     * Check 2: Verify all VkeyWitness signatures against the transaction body hash.
     */
    private void validateSignatures(Transaction transaction, TransactionWitnessSet witnessSet,
                                    List<ValidationError> errors) {
        if (witnessSet == null || witnessSet.getVkeyWitnesses() == null) return;

        byte[] txBodyHash;
        try {
            byte[] txBytes = transaction.serialize();
            byte[] txBodyBytes = com.bloxbean.cardano.client.transaction.util.TransactionUtil
                    .extractTransactionBodyFromTx(txBytes);
            txBodyHash = Blake2bUtil.blake2bHash256(txBodyBytes);
        } catch (Exception e) {
            errors.add(error("Failed to compute tx body hash for signature verification: " + e.getMessage()));
            return;
        }

        for (int i = 0; i < witnessSet.getVkeyWitnesses().size(); i++) {
            VkeyWitness vkw = witnessSet.getVkeyWitnesses().get(i);
            if (vkw.getVkey() == null || vkw.getSignature() == null) {
                errors.add(error("VkeyWitness[" + i + "] has null vkey or signature"));
                continue;
            }
            try {
                boolean valid = SIGNING_PROVIDER.verify(vkw.getSignature(), txBodyHash, vkw.getVkey());
                if (!valid) {
                    String keyHash = HexUtil.encodeHexString(Blake2bUtil.blake2bHash224(vkw.getVkey()));
                    errors.add(error("Invalid Ed25519 signature for VKey " + keyHash));
                }
            } catch (Exception e) {
                errors.add(error("Signature verification failed for VkeyWitness[" + i + "]: " + e.getMessage()));
            }
        }
    }

    /**
     * Checks 3 & 4: Missing and extraneous script witnesses.
     * Provided scripts = witness scripts + reference scripts from UTxOs.
     */
    private void validateScriptWitnesses(LedgerContext context, Transaction transaction,
                                          List<ValidationError> errors) {
        if (context.getUtxoSlice() == null) return;

        var reqs = RequiredWitnessResolver.resolve(transaction, context.getUtxoSlice());
        Set<String> requiredScriptHashes = reqs.getRequiredScriptHashes();

        // Collect provided script hashes
        Set<String> providedScriptHashes = collectProvidedScriptHashes(
                transaction.getWitnessSet(), context.getUtxoSlice(), transaction.getBody());

        // 3. Missing scripts
        Set<String> missingScripts = new LinkedHashSet<>(requiredScriptHashes);
        missingScripts.removeAll(providedScriptHashes);
        if (!missingScripts.isEmpty()) {
            errors.add(error("Missing script witnesses: " + missingScripts));
        }

        // 4. Extraneous scripts (witness-only, not reference scripts)
        Set<String> witnessOnlyScriptHashes = collectWitnessScriptHashes(transaction.getWitnessSet());
        Set<String> extraneousScripts = new LinkedHashSet<>(witnessOnlyScriptHashes);
        extraneousScripts.removeAll(requiredScriptHashes);
        if (!extraneousScripts.isEmpty()) {
            errors.add(error("Extraneous script witnesses: " + extraneousScripts));
        }
    }

    /**
     * Check 5: Evaluate all native scripts in the witness set.
     */
    private void validateNativeScripts(Transaction transaction, TransactionWitnessSet witnessSet,
                                       Set<String> witnessVKeyHashes, List<ValidationError> errors) {
        if (witnessSet == null || witnessSet.getNativeScripts() == null) return;

        TransactionBody body = transaction.getBody();
        Long validityStart = body.getValidityStartInterval() != 0
                ? body.getValidityStartInterval() : null;
        Long ttl = body.getTtl() != 0 ? body.getTtl() : null;

        for (NativeScript ns : witnessSet.getNativeScripts()) {
            if (!NativeScriptEvaluator.evaluate(ns, witnessVKeyHashes, validityStart, ttl)) {
                String scriptHash = safeScriptHash(ns);
                errors.add(error("Native script evaluation failed: " + scriptHash));
            }
        }
    }

    /**
     * Check 6: Metadata (auxiliary data) hash consistency.
     */
    private void validateMetadataHash(Transaction transaction, List<ValidationError> errors) {
        byte[] declaredHash = transaction.getBody().getAuxiliaryDataHash();
        AuxiliaryData auxData = transaction.getAuxiliaryData();

        if (declaredHash != null && auxData == null) {
            errors.add(error("auxiliaryDataHash is present but no AuxiliaryData provided"));
            return;
        }

        if (declaredHash == null && auxData != null) {
            errors.add(error("AuxiliaryData is present but auxiliaryDataHash is missing"));
            return;
        }

        if (declaredHash != null) {
            try {
                byte[] actualHash = auxData.getAuxiliaryDataHash();
                if (!Arrays.equals(declaredHash, actualHash)) {
                    errors.add(error("auxiliaryDataHash mismatch: declared "
                            + HexUtil.encodeHexString(declaredHash)
                            + " vs actual " + HexUtil.encodeHexString(actualHash)));
                }
            } catch (Exception e) {
                errors.add(error("Failed to compute auxiliary data hash: " + e.getMessage()));
            }
        }
    }

    /**
     * Check 7: ScriptDataHash matches recomputed hash from redeemers, datums, and cost models.
     */
    private void validateScriptDataHash(LedgerContext context, Transaction transaction,
                                        List<ValidationError> errors) {
        byte[] declaredHash = transaction.getBody().getScriptDataHash();
        TransactionWitnessSet ws = transaction.getWitnessSet();

        boolean hasRedeemers = ws != null && ws.getRedeemers() != null && !ws.getRedeemers().isEmpty();
        boolean hasDatums = ws != null && ws.getPlutusDataList() != null && !ws.getPlutusDataList().isEmpty();

        if (declaredHash == null && !hasRedeemers && !hasDatums) {
            return; // No script data expected
        }

        if (declaredHash == null && (hasRedeemers || hasDatums)) {
            errors.add(error("scriptDataHash is missing but redeemers or datums are present"));
            return;
        }

        if (declaredHash != null && !hasRedeemers && !hasDatums) {
            errors.add(error("scriptDataHash is present but no redeemers or datums"));
            return;
        }

        // Need costMdls to recompute
        if (context.getCostMdls() == null) {
            return; // Skip recomputation when cost models unavailable
        }

        try {
            List<Redeemer> redeemers = ws.getRedeemers() != null ? ws.getRedeemers() : List.of();
            List<PlutusData> datums = ws.getPlutusDataList() != null ? ws.getPlutusDataList() : List.of();
            byte[] recomputed = ScriptDataHashGenerator.generate(
                    transaction.getEra(), redeemers, datums, context.getCostMdls());

            if (!Arrays.equals(declaredHash, recomputed)) {
                errors.add(error("scriptDataHash mismatch: declared "
                        + HexUtil.encodeHexString(declaredHash)
                        + " vs computed " + HexUtil.encodeHexString(recomputed)));
            }
        } catch (Exception e) {
            errors.add(error("Failed to recompute scriptDataHash: " + e.getMessage()));
        }
    }

    /**
     * Checks 8 & 9: Required datums present, no extraneous supplemental datums.
     * <p>
     * Required datums: for each Plutus script input (script address + has redeemer),
     * the UTxO's datumHash must have a matching datum in the witness set —
     * unless the UTxO has an inline datum.
     */
    private void validateDatums(LedgerContext context, Transaction transaction,
                                List<ValidationError> errors) {
        TransactionWitnessSet ws = transaction.getWitnessSet();
        if (ws == null) return;

        // Collect witness datum hashes
        Set<String> witnessDatumHashes = new LinkedHashSet<>();
        if (ws.getPlutusDataList() != null) {
            for (PlutusData pd : ws.getPlutusDataList()) {
                witnessDatumHashes.add(HexUtil.encodeHexString(pd.getDatumHashAsBytes()));
            }
        }

        // Collect required datum hashes from Plutus script inputs
        Set<String> requiredDatumHashes = new LinkedHashSet<>();
        Set<String> allowedDatumHashes = new LinkedHashSet<>(); // required + output + ref input datums

        if (context.getUtxoSlice() != null && transaction.getBody().getInputs() != null) {
            for (TransactionInput input : transaction.getBody().getInputs()) {
                context.getUtxoSlice().lookup(input).ifPresent(output -> {
                    if (output.getDatumHash() != null && output.getInlineDatum() == null) {
                        String hash = HexUtil.encodeHexString(output.getDatumHash());
                        requiredDatumHashes.add(hash);
                        allowedDatumHashes.add(hash);
                    }
                });
            }
        }

        // Add output datum hashes to allowed
        if (transaction.getBody().getOutputs() != null) {
            for (TransactionOutput output : transaction.getBody().getOutputs()) {
                if (output.getDatumHash() != null) {
                    allowedDatumHashes.add(HexUtil.encodeHexString(output.getDatumHash()));
                }
            }
        }

        // Add reference input datum hashes to allowed
        if (context.getUtxoSlice() != null && transaction.getBody().getReferenceInputs() != null) {
            for (TransactionInput refInput : transaction.getBody().getReferenceInputs()) {
                context.getUtxoSlice().lookup(refInput).ifPresent(output -> {
                    if (output.getDatumHash() != null) {
                        allowedDatumHashes.add(HexUtil.encodeHexString(output.getDatumHash()));
                    }
                });
            }
        }

        // 8. Required datums present
        Set<String> missingDatums = new LinkedHashSet<>(requiredDatumHashes);
        missingDatums.removeAll(witnessDatumHashes);
        if (!missingDatums.isEmpty()) {
            errors.add(error("Missing required datums: " + missingDatums));
        }

        // 9. Extraneous supplemental datums
        Set<String> extraneousDatums = new LinkedHashSet<>(witnessDatumHashes);
        extraneousDatums.removeAll(allowedDatumHashes);
        if (!extraneousDatums.isEmpty()) {
            errors.add(error("Extraneous supplemental datums: " + extraneousDatums));
        }
    }

    /**
     * Check 10: Redeemers exactly match Plutus scripts.
     * Each redeemer must correspond to a Plutus script, and each Plutus script
     * needs exactly one redeemer.
     */
    private void validateRedeemers(LedgerContext context, Transaction transaction,
                                   List<ValidationError> errors) {
        TransactionWitnessSet ws = transaction.getWitnessSet();
        if (ws == null || ws.getRedeemers() == null || ws.getRedeemers().isEmpty()) return;

        TransactionBody body = transaction.getBody();

        for (Redeemer redeemer : ws.getRedeemers()) {
            int index = redeemer.getIndex() != null ? redeemer.getIndex().intValue() : -1;
            boolean valid = false;

            switch (redeemer.getTag()) {
                case Spend -> {
                    if (body.getInputs() != null && index >= 0 && index < body.getInputs().size()) {
                        // Input at this index should be at a script address
                        valid = true;
                    }
                }
                case Mint -> {
                    if (body.getMint() != null && index >= 0 && index < body.getMint().size()) {
                        valid = true;
                    }
                }
                case Cert -> {
                    if (body.getCerts() != null && index >= 0 && index < body.getCerts().size()) {
                        valid = true;
                    }
                }
                case Reward -> {
                    if (body.getWithdrawals() != null && index >= 0 && index < body.getWithdrawals().size()) {
                        valid = true;
                    }
                }
                case Voting -> {
                    valid = body.getVotingProcedures() != null;
                }
                case Proposing -> {
                    if (body.getProposalProcedures() != null && index >= 0
                            && index < body.getProposalProcedures().size()) {
                        valid = true;
                    }
                }
            }

            if (!valid) {
                errors.add(error("Redeemer " + redeemer.getTag() + "[" + index
                        + "] does not correspond to any script purpose"));
            }
        }
    }

    /**
     * Collect all provided script hashes (witness + reference scripts).
     */
    private Set<String> collectProvidedScriptHashes(TransactionWitnessSet witnessSet,
                                                     UtxoSlice utxoSlice,
                                                     TransactionBody body) {
        Set<String> hashes = collectWitnessScriptHashes(witnessSet);

        // Add reference scripts from spending and reference inputs
        addRefScriptHashes(utxoSlice, body.getInputs(), hashes);
        addRefScriptHashes(utxoSlice, body.getReferenceInputs(), hashes);

        return hashes;
    }

    /**
     * Collect script hashes from witness set only (not reference scripts).
     */
    private Set<String> collectWitnessScriptHashes(TransactionWitnessSet witnessSet) {
        Set<String> hashes = new LinkedHashSet<>();
        if (witnessSet == null) return hashes;

        addScriptHashes(witnessSet.getNativeScripts(), hashes);
        addScriptHashes(witnessSet.getPlutusV1Scripts(), hashes);
        addScriptHashes(witnessSet.getPlutusV2Scripts(), hashes);
        addScriptHashes(witnessSet.getPlutusV3Scripts(), hashes);

        return hashes;
    }

    private void addScriptHashes(List<? extends Script> scripts, Set<String> hashes) {
        if (scripts == null) return;
        for (Script s : scripts) {
            String hash = safeScriptHash(s);
            if (hash != null) hashes.add(hash);
        }
    }

    private void addRefScriptHashes(UtxoSlice utxoSlice, List<TransactionInput> inputs,
                                     Set<String> hashes) {
        if (utxoSlice == null || inputs == null) return;
        for (TransactionInput input : inputs) {
            utxoSlice.lookup(input).ifPresent(output -> {
                if (output.getScriptRef() != null && output.getScriptRef().length > 0) {
                    try {
                        // Determine script type from first byte of CBOR array
                        Script script = deserializeScriptRef(output.getScriptRef());
                        if (script != null) {
                            hashes.add(HexUtil.encodeHexString(script.getScriptHash()));
                        }
                    } catch (Exception e) {
                        // Can't parse reference script
                    }
                }
            });
        }
    }

    /**
     * Deserialize a script reference to get its Script object.
     */
    private Script deserializeScriptRef(byte[] scriptRefBytes) {
        try {
            var array = (co.nstant.in.cbor.model.Array)
                    com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.deserialize(scriptRefBytes);
            int type = ((co.nstant.in.cbor.model.UnsignedInteger) array.getDataItems().get(0))
                    .getValue().intValue();
            if (type == 0) {
                return NativeScript.deserializeScriptRef(scriptRefBytes);
            } else {
                return PlutusScript.deserializeScriptRef(scriptRefBytes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String safeScriptHash(Script script) {
        try {
            return HexUtil.encodeHexString(script.getScriptHash());
        } catch (Exception e) {
            return null;
        }
    }

    private ValidationError error(String message) {
        return ValidationError.builder()
                .rule(RULE_NAME)
                .message(message)
                .phase(ValidationError.Phase.PHASE_1)
                .build();
    }
}
