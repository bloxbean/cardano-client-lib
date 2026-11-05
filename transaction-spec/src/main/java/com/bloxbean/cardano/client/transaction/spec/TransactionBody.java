package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawInput;
import com.bloxbean.cardano.client.transaction.raw.RawMultiAsset;
import com.bloxbean.cardano.client.transaction.raw.RawOutput;
import com.bloxbean.cardano.client.transaction.raw.RawProposal;
import com.bloxbean.cardano.client.transaction.raw.RawTx;
import com.bloxbean.cardano.client.transaction.raw.RawTxBody;
import com.bloxbean.cardano.client.transaction.raw.RawUpdate;
import com.bloxbean.cardano.client.transaction.raw.RawWithdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionBody {
    @Builder.Default
    private List<TransactionInput> inputs = new UniqueList<>();

    @Builder.Default
    private List<TransactionOutput> outputs = new ArrayList<>();

    @Builder.Default
    private BigInteger fee = BigInteger.ZERO;
    private long ttl; //Optional

    @Builder.Default
    private List<Certificate> certs = new UniqueList<>();

    @Builder.Default
    private List<Withdrawal> withdrawals = new ArrayList<>();

    private Update update;

    private byte[] auxiliaryDataHash; //auxiliary_data_hash
    private long validityStartInterval;

    @Builder.Default
    private List<MultiAsset> mint = new ArrayList<>();

    private byte[] scriptDataHash;

    @Builder.Default
    private List<TransactionInput> collateral = new UniqueList<>();

    @Builder.Default
    private List<byte[]> requiredSigners = new UniqueList<>();

    private NetworkId networkId; // 1 or 0

    //babbage fields
    private TransactionOutput collateralReturn; //?16

    private BigInteger totalCollateral; //? 17

    @Builder.Default
    private List<TransactionInput> referenceInputs = new UniqueList<>(); // ? 18

    //Conway
    private VotingProcedures votingProcedures; //19
    private List<ProposalProcedure> proposalProcedures; //20
    private BigInteger currentTreasuryValue; //21
    private BigInteger donation; //22

    public Map serialize() throws CborSerializationException, AddressExcepion {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    public Map serialize(Era era) throws CborSerializationException, AddressExcepion {
        Map bodyMap = new Map();

        Array inputsArray = createArray(era);
        for (TransactionInput ti : inputs) {
            Array input = ti.serialize();
            inputsArray.add(input);
        }
        bodyMap.put(new UnsignedInteger(0), inputsArray);

        Array outputsArray = new Array();
        for (TransactionOutput to : outputs) {
            DataItem output = to.serialize();
            outputsArray.add(output);
        }
        bodyMap.put(new UnsignedInteger(1), outputsArray);

        if (fee != null) {
            bodyMap.put(new UnsignedInteger(2), new UnsignedInteger(fee)); //fee
        } else {
            throw new CborSerializationException("Fee cannot be null");
        }

        if (ttl != 0) {
            bodyMap.put(new UnsignedInteger(3), unsignedLongItem(ttl)); //ttl
        }

        if (certs != null && certs.size() > 0) { //certs
            Array certArray = createArray(era);
            for (Certificate cert : certs) {
                certArray.add(cert.serialize(era));
            }

            bodyMap.put(new UnsignedInteger(4), certArray);
        }

        if (withdrawals != null && withdrawals.size() > 0) { //Withdrawals
            bodyMap.put(new UnsignedInteger(5), Withdrawal.serialize(withdrawals));
        }

        if (update != null) {
            bodyMap.put(new UnsignedInteger(6), update.serialize());
        }

        if (auxiliaryDataHash != null) {
            bodyMap.put(new UnsignedInteger(7), new ByteString(auxiliaryDataHash));
        }

        if (validityStartInterval != 0) {
            bodyMap.put(new UnsignedInteger(8), unsignedLongItem(validityStartInterval)); //validityStartInterval
        }

        if (mint != null && mint.size() > 0) {
            bodyMap.put(new UnsignedInteger(9), MultiAsset.serialize(mint));
        }

        if (scriptDataHash != null) {
            bodyMap.put(new UnsignedInteger(11), new ByteString(scriptDataHash));
        }

        //collateral
        if (collateral != null && collateral.size() > 0) {
            Array collateralArray = createArray(era);
            for (TransactionInput ti : collateral) {
                Array input = ti.serialize();
                collateralArray.add(input);
            }
            bodyMap.put(new UnsignedInteger(13), collateralArray);
        }

        //required_signers
        if (requiredSigners != null && requiredSigners.size() > 0) {
            Array requiredSignerArray = createArray(era);
            for (byte[] requiredSigner : requiredSigners) {
                requiredSignerArray.add(new ByteString(requiredSigner));
            }
            bodyMap.put(new UnsignedInteger(14), requiredSignerArray);
        }

        //NetworkId
        if (networkId != null) {
            switch (networkId) {
                case TESTNET:
                    bodyMap.put(new UnsignedInteger(15), new UnsignedInteger(0));
                    break;
                case MAINNET:
                    bodyMap.put(new UnsignedInteger(15), new UnsignedInteger(1));
                    break;
            }
        }

        //collateral return
        if (collateralReturn != null) {
            bodyMap.put(new UnsignedInteger(16), collateralReturn.serialize());
        }

        //total collateral
        if (totalCollateral != null) {
            bodyMap.put(new UnsignedInteger(17), new UnsignedInteger(totalCollateral));
        }

        //reference inputs
        if (referenceInputs != null && referenceInputs.size() > 0) {
            Array referenceInputsArray = createArray(era);
            for (TransactionInput ti : referenceInputs) {
                Array input = ti.serialize();
                referenceInputsArray.add(input);
            }
            bodyMap.put(new UnsignedInteger(18), referenceInputsArray);
        }

        //voting procedures
        if (votingProcedures != null) {
            bodyMap.put(new UnsignedInteger(19), votingProcedures.serialize());
        }

        //proposal procedures
        if (proposalProcedures != null && proposalProcedures.size() > 0) {
            Array proposalProceduresArray = createArray(era);
            for (var proposalProcedure : proposalProcedures) {
                proposalProceduresArray.add(proposalProcedure.serialize());
            }
            bodyMap.put(new UnsignedInteger(20), proposalProceduresArray);
        }

        //current treasury value
        if (currentTreasuryValue != null) {
            Number currentTreasuryValueDI;
            if (currentTreasuryValue.compareTo(BigInteger.ZERO) >= 0)
                currentTreasuryValueDI = new UnsignedInteger(currentTreasuryValue);
            else
                currentTreasuryValueDI = new NegativeInteger(currentTreasuryValue);

            bodyMap.put(new UnsignedInteger(21), currentTreasuryValueDI);
        }

        //donation
        if (donation != null) {
            bodyMap.put(new UnsignedInteger(22), new UnsignedInteger(donation));
        }

        return bodyMap;
    }

    public static TransactionBody deserialize(Map bodyMap) throws CborDeserializationException {
        try {
            return from(RawTxBody.lenient(RawModels.span(bodyMap), RawModels.READ_IN));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("TransactionBody deserialization failed", e);
        }
    }

    static TransactionBody from(RawTx tx) throws CborDeserializationException {
        return from(tx.body(RawModels.READ_IN));
    }

    private static TransactionBody from(RawTxBody raw) throws CborDeserializationException {
        TransactionBody body = new TransactionBody();

        body.setInputs(inputs(raw.inputs()));

        List<TransactionOutput> outputs = new ArrayList<>();
        for (RawOutput output : raw.outputs())
            outputs.add(TransactionOutput.from(output));
        body.setOutputs(outputs);

        raw.fee().ifPresent(body::setFee);
        raw.ttl().ifPresent(body::setTtl);

        for (RawCertificate certificate : raw.certificates())
            body.getCerts().add(Certificate.from(certificate));

        for (RawWithdrawal withdrawal : raw.withdrawals())
            body.getWithdrawals().add(Withdrawal.from(withdrawal));

        Optional<RawUpdate> update = raw.update();
        if (update.isPresent())
            body.setUpdate(Update.from(update.get()));

        raw.auxDataHash().ifPresent(body::setAuxiliaryDataHash);
        raw.validityStart().ifPresent(body::setValidityStartInterval);

        for (RawMultiAsset multiAsset : raw.mint())
            body.getMint().add(MultiAsset.from(multiAsset));

        raw.scriptDataHash().ifPresent(body::setScriptDataHash);

        Optional<List<RawInput>> collateral = raw.collateral();
        if (collateral.isPresent())
            body.setCollateral(inputs(collateral.get()));

        raw.requiredSigners().ifPresent(body::setRequiredSigners);

        OptionalInt networkId = raw.networkId();
        if (networkId.isPresent())
            body.setNetworkId(networkId(networkId.getAsInt()));

        Optional<RawOutput> collateralReturn = raw.collateralReturn();
        if (collateralReturn.isPresent())
            body.setCollateralReturn(TransactionOutput.from(collateralReturn.get()));

        raw.totalCollateral().ifPresent(body::setTotalCollateral);

        Optional<List<RawInput>> referenceInputs = raw.referenceInputs();
        if (referenceInputs.isPresent())
            body.setReferenceInputs(inputs(referenceInputs.get()));

        raw.votingProcedures().map(VotingProcedures::from).ifPresent(body::setVotingProcedures);

        Optional<List<RawProposal>> proposals = raw.proposalProcedures();
        if (proposals.isPresent() && !proposals.get().isEmpty()) {
            List<ProposalProcedure> proposalProcedures = new UniqueList<>();
            for (RawProposal proposal : proposals.get())
                proposalProcedures.add(ProposalProcedure.from(proposal));
            body.setProposalProcedures(proposalProcedures);
        }

        raw.currentTreasury().ifPresent(body::setCurrentTreasuryValue);
        raw.donation().ifPresent(body::setDonation);

        return body;
    }

    private static List<TransactionInput> inputs(List<RawInput> raw) {
        List<TransactionInput> inputs = new ArrayList<>(raw.size());
        for (RawInput input : raw)
            inputs.add(TransactionInput.from(input));
        return inputs;
    }

    private static NetworkId networkId(int value) throws CborDeserializationException {
        switch (value) {
            case 0:
                return NetworkId.TESTNET;
            case 1:
                return NetworkId.MAINNET;
            default:
                throw new CborDeserializationException("Invalid networkId value : " + value);
        }
    }

}
