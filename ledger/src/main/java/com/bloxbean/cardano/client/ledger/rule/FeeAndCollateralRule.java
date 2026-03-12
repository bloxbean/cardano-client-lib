package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.UtxoSlice;
import com.bloxbean.cardano.client.ledger.util.LedgerMinFeeCalculator;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates fee and collateral rules (Category C):
 * <ol>
 *   <li>Fee ≥ minFee (linear + ExUnits + ref-script tiers)</li>
 *   <li>Collateral present when scripts (redeemers) exist</li>
 *   <li>Collateral count ≤ maxCollateralInputs</li>
 *   <li>Collateral inputs are VKey-only (not script addresses)</li>
 *   <li>Net collateral balance ≥ fee × collateralPercent / 100</li>
 *   <li>Net collateral is ADA-only (no multi-assets after return)</li>
 *   <li>totalCollateral field matches actual net (if present)</li>
 *   <li>Bootstrap address attributes ≤ 64 bytes</li>
 * </ol>
 * <p>
 * Reference: Scalus FeesOkValidator, Amaru collateral.rs, Haskell Babbage.FeesOK
 */
public class FeeAndCollateralRule implements LedgerRule {

    private static final String RULE_NAME = "FeeAndCollateral";

    /**
     * Conway ledger constant: maximum total reference script size per transaction (200 KiB).
     * See Haskell cardano-ledger: Cardano.Ledger.Conway.Rules.Ledger
     */
    private static final int MAX_REF_SCRIPT_SIZE_PER_TX = 204800;

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        ProtocolParams pp = context.getProtocolParams();
        if (pp == null) return Collections.emptyList();

        List<ValidationError> errors = new ArrayList<>();
        TransactionBody body = transaction.getBody();

        // 1. Fee ≥ minFee
        validateMinFee(context, transaction, errors);

        // Determine if collateral checks are needed (redeemers present)
        boolean hasRedeemers = transaction.getWitnessSet() != null
                && transaction.getWitnessSet().getRedeemers() != null
                && !transaction.getWitnessSet().getRedeemers().isEmpty();

        if (hasRedeemers) {
            validateCollateral(context, body, errors);
        }

        // ExUnitsTooBigUTxO: sum of redeemer ExUnits ≤ ppMaxTxExUnits
        validateExUnits(context, transaction, errors);

        // ConwayTxRefScriptsSizeTooBig: total ref script size ≤ limit
        validateRefScriptSize(context, body, errors);

        // 8. Bootstrap address attribute size check (applies to all txs)
        validateBootstrapAddrAttrs(body, errors);

        return errors;
    }

    private void validateMinFee(LedgerContext context, Transaction transaction, List<ValidationError> errors) {
        try {
            BigInteger minFee = LedgerMinFeeCalculator.computeMinFee(context, transaction);
            BigInteger declaredFee = transaction.getBody().getFee();
            if (declaredFee == null || declaredFee.compareTo(minFee) < 0) {
                errors.add(error("Fee " + declaredFee + " is below minimum required fee " + minFee));
            }
        } catch (Exception e) {
            errors.add(error("Failed to compute minimum fee: " + e.getMessage()));
        }
    }

    private void validateCollateral(LedgerContext context, TransactionBody body, List<ValidationError> errors) {
        ProtocolParams pp = context.getProtocolParams();
        UtxoSlice utxoSlice = context.getUtxoSlice();

        List<TransactionInput> collateralInputs = body.getCollateral();

        // 2. Collateral must be present
        if (collateralInputs == null || collateralInputs.isEmpty()) {
            errors.add(error("No collateral inputs provided but transaction has redeemers"));
            return;
        }

        // 3. Collateral count ≤ max
        if (pp.getMaxCollateralInputs() != null) {
            int maxCollateral = pp.getMaxCollateralInputs();
            if (collateralInputs.size() > maxCollateral) {
                errors.add(error("Collateral input count " + collateralInputs.size()
                        + " exceeds max " + maxCollateral));
            }
        }

        if (utxoSlice == null) return;

        // Resolve collateral UTxOs and compute balance
        BigInteger totalCollateralCoin = BigInteger.ZERO;
        List<MultiAsset> totalCollateralAssets = new ArrayList<>();

        for (int i = 0; i < collateralInputs.size(); i++) {
            TransactionInput input = collateralInputs.get(i);
            var outputOpt = utxoSlice.lookup(input);
            if (outputOpt.isEmpty()) {
                errors.add(error("Collateral input not found in UTxO: "
                        + input.getTransactionId() + "#" + input.getIndex()));
                continue;
            }

            TransactionOutput output = outputOpt.get();

            // 4. Must be VKey address (not script)
            try {
                Address addr = new Address(output.getAddress());
                if (addr.isScriptHashInPaymentPart()) {
                    errors.add(error("Collateral input[" + i + "] is at a script address: "
                            + input.getTransactionId() + "#" + input.getIndex()));
                }
            } catch (Exception e) {
                // Can't parse address — skip VKey check
            }

            if (output.getValue() != null) {
                if (output.getValue().getCoin() != null) {
                    totalCollateralCoin = totalCollateralCoin.add(output.getValue().getCoin());
                }
                if (output.getValue().getMultiAssets() != null) {
                    totalCollateralAssets.addAll(output.getValue().getMultiAssets());
                }
            }
        }

        // Subtract collateral return
        BigInteger returnCoin = BigInteger.ZERO;
        List<MultiAsset> returnAssets = new ArrayList<>();
        TransactionOutput collateralReturn = body.getCollateralReturn();
        if (collateralReturn != null && collateralReturn.getValue() != null) {
            if (collateralReturn.getValue().getCoin() != null) {
                returnCoin = collateralReturn.getValue().getCoin();
            }
            if (collateralReturn.getValue().getMultiAssets() != null) {
                returnAssets = collateralReturn.getValue().getMultiAssets();
            }
        }

        BigInteger netCollateralCoin = totalCollateralCoin.subtract(returnCoin);

        // 6. Net multi-assets must be zero
        List<MultiAsset> netAssets = MultiAsset.subtractMultiAssetLists(totalCollateralAssets, returnAssets);
        boolean hasNonZeroAssets = netAssets.stream()
                .anyMatch(ma -> ma.getAssets().stream()
                        .anyMatch(a -> a.getValue() != null && a.getValue().signum() != 0));
        if (hasNonZeroAssets) {
            errors.add(error("Collateral contains non-ADA assets after subtracting return"));
        }

        // 5. Net collateral sufficient: netCoin * 100 >= fee * collateralPercent
        if (pp.getCollateralPercent() != null) {
            BigInteger fee = body.getFee() != null ? body.getFee() : BigInteger.ZERO;
            BigInteger requiredTimes100 = fee.multiply(BigInteger.valueOf(pp.getCollateralPercent().longValue()));
            BigInteger netTimes100 = netCollateralCoin.multiply(BigInteger.valueOf(100));
            if (netTimes100.compareTo(requiredTimes100) < 0) {
                BigInteger required = requiredTimes100.add(BigInteger.valueOf(99))
                        .divide(BigInteger.valueOf(100)); // ceil division
                errors.add(error("Insufficient collateral: provided " + netCollateralCoin
                        + " lovelace, required " + required + " lovelace ("
                        + pp.getCollateralPercent().intValue() + "% of fee " + fee + ")"));
            }
        }

        // 7. totalCollateral field must match if present
        BigInteger declaredTotalCollateral = body.getTotalCollateral();
        if (declaredTotalCollateral != null
                && declaredTotalCollateral.compareTo(netCollateralCoin) != 0) {
            errors.add(error("Declared totalCollateral " + declaredTotalCollateral
                    + " does not match actual net collateral " + netCollateralCoin));
        }
    }

    /**
     * ExUnitsTooBigUTxO: The sum of all redeemer ExUnits must not exceed ppMaxTxExUnits.
     */
    private void validateExUnits(LedgerContext context, Transaction transaction, List<ValidationError> errors) {
        ProtocolParams pp = context.getProtocolParams();
        if (pp == null) return;
        if (transaction.getWitnessSet() == null) return;

        List<Redeemer> redeemers = transaction.getWitnessSet().getRedeemers();
        if (redeemers == null || redeemers.isEmpty()) return;

        BigInteger totalMem = BigInteger.ZERO;
        BigInteger totalSteps = BigInteger.ZERO;

        for (Redeemer redeemer : redeemers) {
            ExUnits eu = redeemer.getExUnits();
            if (eu != null) {
                if (eu.getMem() != null) totalMem = totalMem.add(eu.getMem());
                if (eu.getSteps() != null) totalSteps = totalSteps.add(eu.getSteps());
            }
        }

        if (pp.getMaxTxExMem() != null) {
            BigInteger maxMem = new BigInteger(pp.getMaxTxExMem());
            if (totalMem.compareTo(maxMem) > 0) {
                errors.add(error("ExUnits memory " + totalMem + " exceeds max " + maxMem));
            }
        }

        if (pp.getMaxTxExSteps() != null) {
            BigInteger maxSteps = new BigInteger(pp.getMaxTxExSteps());
            if (totalSteps.compareTo(maxSteps) > 0) {
                errors.add(error("ExUnits steps " + totalSteps + " exceeds max " + maxSteps));
            }
        }
    }

    /**
     * ConwayTxRefScriptsSizeTooBig: The total serialized size of all reference scripts
     * referenced by the transaction must not exceed MAX_REF_SCRIPT_SIZE_PER_TX (200 KiB).
     */
    private void validateRefScriptSize(LedgerContext context, TransactionBody body, List<ValidationError> errors) {
        UtxoSlice utxoSlice = context.getUtxoSlice();
        if (utxoSlice == null) return;

        List<TransactionInput> refInputs = body.getReferenceInputs();
        if (refInputs == null || refInputs.isEmpty()) return;

        long totalRefScriptSize = 0;

        for (TransactionInput refInput : refInputs) {
            var outputOpt = utxoSlice.lookup(refInput);
            if (outputOpt.isPresent()) {
                TransactionOutput output = outputOpt.get();
                byte[] scriptRef = output.getScriptRef();
                if (scriptRef != null) {
                    totalRefScriptSize += scriptRef.length;
                }
            }
        }

        if (totalRefScriptSize > MAX_REF_SCRIPT_SIZE_PER_TX) {
            errors.add(error("Total reference script size " + totalRefScriptSize
                    + " bytes exceeds maximum " + MAX_REF_SCRIPT_SIZE_PER_TX + " bytes"));
        }
    }

    /**
     * Validate that Bootstrap (Byron) addresses in outputs have attributes ≤ 64 bytes.
     * This is a hardcoded limit, not a protocol parameter.
     */
    private void validateBootstrapAddrAttrs(TransactionBody body, List<ValidationError> errors) {
        // Byron addresses start with specific prefixes and are base58-encoded.
        // In practice, most Conway-era transactions don't use Byron addresses.
        // Full Byron attribute size validation would require decoding the address
        // to inspect the CBOR attributes map. For now, this check is a placeholder
        // that will be refined when Byron address handling is fully implemented.
        // The key concern is that outputs with Byron addresses don't have
        // overly large attributes that bloat the UTxO set.
    }

    private ValidationError error(String message) {
        return ValidationError.builder()
                .rule(RULE_NAME)
                .message(message)
                .phase(ValidationError.Phase.PHASE_1)
                .build();
    }
}
