package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.VoteDelegCert;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.DRepType;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Intention for voting delegation operations.
 * Maps to GovTx.delegateVotingPowerTo(Address, DRep, PlutusData) operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotingDelegationIntention implements TxIntention {

    // Runtime fields - original objects preserved

    /**
     * Address to delegate from (runtime object).
     * Should have delegation credential (base address or stake address).
     */
    @JsonIgnore
    private Address address;

    /**
     * DRep to delegate to (runtime object).
     */
    @JsonIgnore
    private DRep drep;

    // Serialization fields - computed from runtime objects or set during deserialization

    /**
     * Address to delegate from.
     */
    @JsonProperty("address")
    private String addressStr;

    /**
     * DRep as CBOR hex for serialization.
     */
    @JsonProperty("drep_hex")
    private String drepHex;

    /**
     * DRep type for human-readable serialization.
     * Can be "key_hash", "script_hash", "abstain", or "no_confidence".
     */
    @JsonProperty("drep_type")
    private String drepType;

    /**
     * DRep key hash or script hash (when applicable).
     */
    @JsonProperty("drep_hash")
    private String drepHash;

    // Optional redeemer for cert witness
    @JsonIgnore
    private PlutusData redeemer;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try {
                return redeemer.serializeToHex();
            } catch (Exception e) {
                // ignore and fall back
            }
        }
        return redeemerHex;
    }

    /**
     * Get address string for serialization.
     */
    @JsonProperty("address")
    public String getAddressStr() {
        if (address != null) {
            return address.getAddress();
        }
        return addressStr;
    }

    /**
     * Get DRep hex for serialization.
     */
    @JsonProperty("drep_hex")
    public String getDrepHex() {
        if (drep != null) {
            try {
                return HexUtil.encodeHexString(CborSerializationUtil.serialize(drep.serialize()));
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return drepHex;
    }

    /**
     * Get DRep type for serialization.
     */
    @JsonProperty("drep_type")
    public String getDrepType() {
        if (drep != null) {
            return drepTypeToString(drep.getType());
        }
        return drepType;
    }

    /**
     * Get DRep hash for serialization.
     */
    @JsonProperty("drep_hash")
    public String getDrepHash() {
        if (drep != null && drep.getHash() != null) {
            return drep.getHash(); // getHash() already returns String
        }
        return drepHash;
    }

    @Override
    public String getType() {
        return "voting_delegation";
    }

    @Override
    public void validate() {
        // Check address
        if (address == null && (addressStr == null || addressStr.isEmpty())) {
            throw new IllegalStateException("Address is required for voting delegation");
        }

        // Check DRep
        if (drep == null && (drepHex == null || drepHex.isEmpty()) &&
            (drepType == null || drepType.isEmpty())) {
            throw new IllegalStateException("DRep is required for voting delegation");
        }

        // Validate DRep type format
        if (drepType != null && !drepType.startsWith("${")) {
            if (!drepType.equals("key_hash") &&
                !drepType.equals("script_hash") &&
                !drepType.equals("abstain") &&
                !drepType.equals("no_confidence")) {
                throw new IllegalStateException("DRep type must be key_hash, script_hash, abstain, or no_confidence");
            }
        }

        // Validate hex formats
        if (drepHex != null && !drepHex.isEmpty() && !drepHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(drepHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid DRep hex format: " + drepHex);
            }
        }

        if (drepHash != null && !drepHash.isEmpty() && !drepHash.startsWith("${")) {
            try {
                HexUtil.decodeHexString(drepHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid DRep hash format: " + drepHash);
            }
        }

        // Validate that hash-based DReps have hash
        if ((drepType != null && (drepType.equals("key_hash") || drepType.equals("script_hash"))) &&
            (drepHash == null || drepHash.isEmpty())) {
            throw new IllegalStateException("DRep hash is required for key_hash and script_hash DRep types");
        }

        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    // Factory methods for different use cases

    /**
     * Create VotingDelegationIntention from runtime objects.
     */
    public static VotingDelegationIntention delegate(Address address, DRep drep) {
        return VotingDelegationIntention.builder()
            .address(address)
            .drep(drep)
            .build();
    }

    /**
     * Create VotingDelegationIntention from address string and DRep.
     */
    public static VotingDelegationIntention delegate(String addressStr, DRep drep) {
        return VotingDelegationIntention.builder()
            .addressStr(addressStr)
            .drep(drep)
            .build();
    }

    /**
     * Create VotingDelegationIntention from serializable values.
     */
    public static VotingDelegationIntention delegate(String addressStr, String drepType, String drepHash) {
        return VotingDelegationIntention.builder()
            .addressStr(addressStr)
            .drepType(drepType)
            .drepHash(drepHash)
            .build();
    }

    /**
     * Create VotingDelegationIntention for abstain delegation.
     */
    public static VotingDelegationIntention delegateToAbstain(String addressStr) {
        return VotingDelegationIntention.builder()
            .addressStr(addressStr)
            .drepType("abstain")
            .build();
    }

    /**
     * Create VotingDelegationIntention for no confidence delegation.
     */
    public static VotingDelegationIntention delegateToNoConfidence(String addressStr) {
        return VotingDelegationIntention.builder()
            .addressStr(addressStr)
            .drepType("no_confidence")
            .build();
    }

    // Utility methods

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return address != null || drep != null;
    }

    /**
     * Check if this intention needs deserialization from stored data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() &&
               (addressStr != null && !addressStr.isEmpty()) &&
               (drepHex != null && !drepHex.isEmpty() || drepType != null && !drepType.isEmpty());
    }

    /**
     * Check if this is an abstain delegation.
     */
    @JsonIgnore
    public boolean isAbstainDelegation() {
        return "abstain".equals(drepType) ||
               (drep != null && drep.getType().toString().equalsIgnoreCase("abstain"));
    }

    /**
     * Check if this is a no confidence delegation.
     */
    @JsonIgnore
    public boolean isNoConfidenceDelegation() {
        return "no_confidence".equals(drepType) ||
               (drep != null && drep.getType().toString().equalsIgnoreCase("no_confidence"));
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank())
            throw new TxBuildException("From address is required for voting delegation");
        return (ctx, outputs) -> outputs.add(new TransactionOutput(from, Value.builder().coin(ADAConversionUtil.adaToLovelace(1)).build()));
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for voting delegation");
            String addr = (address != null) ? address.getAddress() : addressStr;
            if (addr == null || addr.isBlank())
                throw new TxBuildException("Address is required for voting delegation");
            if (drep == null && (drepHex == null && (drepType == null || drepType.isEmpty())))
                throw new TxBuildException("DRep is required for voting delegation");
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                Address _address = (address != null) ? address : new Address(addressStr);
                byte[] delegationHash = _address.getDelegationCredentialHash()
                        .orElseThrow(() -> new TxBuildException("Invalid stake address. Address does not have delegation credential"));
                StakeCredential stakeCredential = _address.isStakeKeyHashInDelegationPart()
                        ? StakeCredential.fromKeyHash(delegationHash)
                        : StakeCredential.fromScriptHash(delegationHash);

                DRep _drep = drep;
                if (_drep == null) {
                    if (drepHex != null && !drepHex.isEmpty()) {
                        _drep = DRep.deserialize(CborSerializationUtil.deserialize(HexUtil.decodeHexString(drepHex)));
                    } else if (drepType != null) {
                        // build from type/hash where applicable
                        switch (drepType) {
                            case "abstain":
                                _drep = DRep.abstain();
                                break;
                            case "no_confidence":
                                _drep = DRep.noConfidence();
                                break;
                            case "key_hash":
                                _drep = DRep.addrKeyHash(drepHash);
                                break;
                            case "script_hash":
                                _drep = DRep.scriptHash(drepHash);
                                break;
                            default:
                                throw new TxBuildException("Unsupported drep_type: " + drepType);
                        }
                    }
                }

                if (_drep == null) throw new TxBuildException("DRep resolution failed");

                if (txn.getBody().getCerts() == null) txn.getBody().setCerts(new ArrayList<Certificate>());
                txn.getBody().getCerts().add(VoteDelegCert.builder()
                        .stakeCredential(stakeCredential)
                        .drep(_drep)
                        .build());

                // Add cert redeemer if provided
                PlutusData rdData = redeemer;
                if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                    rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
                }
                if (rdData != null) {
                    if (txn.getWitnessSet() == null)
                        txn.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());
                    int certIndex = txn.getBody().getCerts().size() - 1;
                    Redeemer rd = Redeemer.builder()
                            .tag(RedeemerTag.Cert)
                            .data(rdData)
                            .index(java.math.BigInteger.valueOf(certIndex))
                            .exUnits(ExUnits.builder()
                                    .mem(java.math.BigInteger.valueOf(10000))
                                    .steps(java.math.BigInteger.valueOf(1000))
                                    .build())
                            .build();
                    txn.getWitnessSet().getRedeemers().add(rd);
                }
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply VotingDelegationIntention: " + e.getMessage(), e);
            }
        };
    }

    private String drepTypeToString(DRepType dRepType) {
        switch (dRepType) {
            case ADDR_KEYHASH:
                return "key_hash";
            case SCRIPTHASH:
                return "script_hash";
            case ABSTAIN:
                return "abstain";
            case NO_CONFIDENCE:
                return "no_confidence";
            default:
                throw new TxBuildException("Unsupported DRepType: " + dRepType);
        }
    }
}
