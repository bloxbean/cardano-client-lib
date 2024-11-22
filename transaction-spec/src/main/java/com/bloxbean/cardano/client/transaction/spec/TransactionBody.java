package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
            bodyMap.put(new UnsignedInteger(3), new UnsignedInteger(ttl)); //ttl
        }

        if (certs != null && certs.size() > 0) { //certs
            Array certArray = createArray(era);
            for (Certificate cert : certs) {
                certArray.add(cert.serialize(era));
            }

            bodyMap.put(new UnsignedInteger(4), certArray);
        }

        if (withdrawals != null && withdrawals.size() > 0) { //Withdrawals
            Map withdrawalMap = new Map();
            for (Withdrawal withdrawal : withdrawals) {
                withdrawal.serialize(withdrawalMap);
            }
            bodyMap.put(new UnsignedInteger(5), withdrawalMap);
        }

        if (update != null) {
            bodyMap.put(new UnsignedInteger(6), update.serialize());
        }

        if (auxiliaryDataHash != null) {
            bodyMap.put(new UnsignedInteger(7), new ByteString(auxiliaryDataHash));
        }

        if (validityStartInterval != 0) {
            bodyMap.put(new UnsignedInteger(8), new UnsignedInteger(validityStartInterval)); //validityStartInterval
        }

        if (mint != null && mint.size() > 0) {
            Map mintMap = new Map();
            for (MultiAsset multiAsset : mint) {
                multiAsset.serialize(mintMap);
            }
            bodyMap.put(new UnsignedInteger(9), mintMap);
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
        TransactionBody transactionBody = new TransactionBody();

        Array inputArray = (Array) bodyMap.get(new UnsignedInteger(0));
        List<TransactionInput> inputs = new ArrayList<>();
        for (DataItem inputItem : inputArray.getDataItems()) {
            if (inputItem == SimpleValue.BREAK) continue;
            TransactionInput ti = TransactionInput.deserialize((Array) inputItem);
            inputs.add(ti);
        }
        transactionBody.setInputs(inputs);

        Array outputArray = (Array) bodyMap.get(new UnsignedInteger(1));
        List<TransactionOutput> outputs = new ArrayList<>();
        for (DataItem ouptutItem : outputArray.getDataItems()) {
            if (ouptutItem == SimpleValue.BREAK) continue;
            TransactionOutput to = TransactionOutput.deserialize(ouptutItem);
            outputs.add(to);
        }
        transactionBody.setOutputs(outputs);

        UnsignedInteger feeUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(2));
        if (feeUI != null) {
            transactionBody.setFee(feeUI.getValue());
        }

        UnsignedInteger ttlUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(3));
        if (ttlUI != null) {
            transactionBody.setTtl(ttlUI.getValue().longValue());
        }

        //certs
        Array certArray = (Array) bodyMap.get(new UnsignedInteger(4));
        if (certArray != null && certArray.getDataItems() != null && certArray.getDataItems().size() > 0) {
            for (DataItem dataItem : certArray.getDataItems()) {
                if (dataItem == SimpleValue.BREAK) continue;
                Certificate cert = Certificate.deserialize((Array) dataItem);
                transactionBody.getCerts().add(cert);
            }
        }

        //withdrawals
        Map withdrawalMap = (Map) bodyMap.get(new UnsignedInteger(5));
        if (withdrawalMap != null && withdrawalMap.getKeys() != null && withdrawalMap.getKeys().size() > 0) {
            Collection<DataItem> addrKeys = withdrawalMap.getKeys();
            for (DataItem addrKey : addrKeys) {
                Withdrawal withdrawal = Withdrawal.deserialize(withdrawalMap, addrKey);
                transactionBody.getWithdrawals().add(withdrawal);
            }
        }

        //update
        DataItem updateDI = bodyMap.get(new UnsignedInteger(6));
        if (updateDI != null) {
            Update update = Update.deserialize(updateDI);
            transactionBody.setUpdate(update);
        }

        ByteString metadataHashBS = (ByteString) bodyMap.get(new UnsignedInteger(7));
        if (metadataHashBS != null) {
            transactionBody.setAuxiliaryDataHash(metadataHashBS.getBytes());
        }

        UnsignedInteger validityStartIntervalUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(8));
        if (validityStartIntervalUI != null) {
            transactionBody.setValidityStartInterval(validityStartIntervalUI.getValue().longValue());
        }

        //Mint
        Map mintMap = (Map) bodyMap.get(new UnsignedInteger(9));
        if (mintMap != null) {
            Collection<DataItem> mintDataItems = mintMap.getKeys();
            for (DataItem multiAssetKey : mintDataItems) {
                MultiAsset ma = MultiAsset.deserialize(mintMap, multiAssetKey);
                transactionBody.getMint().add(ma);
            }
        }

        //script_data_hash
        ByteString scriptDataHashBS = (ByteString) bodyMap.get(new UnsignedInteger(11));
        if (scriptDataHashBS != null) {
            transactionBody.setScriptDataHash(scriptDataHashBS.getBytes());
        }

        //collateral
        Array collateralArray = (Array) bodyMap.get(new UnsignedInteger(13));
        if (collateralArray != null) {
            List<TransactionInput> collateral = new ArrayList<>();
            for (DataItem inputItem : collateralArray.getDataItems()) {
                if (inputItem == SimpleValue.BREAK) continue;
                TransactionInput ti = TransactionInput.deserialize((Array) inputItem);
                collateral.add(ti);
            }
            transactionBody.setCollateral(collateral);
        }

        //required_signers
        Array requiredSignerArray = (Array) bodyMap.get(new UnsignedInteger(14));
        if (requiredSignerArray != null) {
            List<byte[]> requiredSigners = new ArrayList<>();
            for (DataItem requiredSigDI : requiredSignerArray.getDataItems()) {
                if (requiredSigDI == SimpleValue.BREAK) continue;
                ByteString requiredSigBS = (ByteString) requiredSigDI;
                requiredSigners.add(requiredSigBS.getBytes());
            }
            transactionBody.setRequiredSigners(requiredSigners);
        }

        //network Id
        UnsignedInteger networkIdUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(15));
        if (networkIdUI != null) {
            int networkIdInt = networkIdUI.getValue().intValue();
            if (networkIdInt == 0) {
                transactionBody.setNetworkId(NetworkId.TESTNET);
            } else if (networkIdInt == 1) {
                transactionBody.setNetworkId(NetworkId.MAINNET);
            } else {
                throw new CborDeserializationException("Invalid networkId value : " + networkIdInt);
            }
        }

        //collateral return
        DataItem collateralReturnDI = bodyMap.get(new UnsignedInteger(16));
        if (collateralReturnDI != null) {
            TransactionOutput collateralReturn = TransactionOutput.deserialize(collateralReturnDI);
            transactionBody.setCollateralReturn(collateralReturn);
        }

        //total collateral
        UnsignedInteger totalCollateralUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(17));
        if (totalCollateralUI != null) {
            transactionBody.setTotalCollateral(totalCollateralUI.getValue());
        }

        //reference inputs
        Array referenceInputsArray = (Array) bodyMap.get(new UnsignedInteger(18));
        if (referenceInputsArray != null) {
            List<TransactionInput> referenceInputs = new ArrayList<>();
            for (DataItem inputItem : referenceInputsArray.getDataItems()) {
                if (inputItem == SimpleValue.BREAK) continue;
                TransactionInput ti = TransactionInput.deserialize((Array) inputItem);
                referenceInputs.add(ti);
            }
            transactionBody.setReferenceInputs(referenceInputs);
        }

        //voting procedures
        var votingProceduresDI = bodyMap.get(new UnsignedInteger(19));
        if (votingProceduresDI != null) {
            VotingProcedures votingProcedures = VotingProcedures.deserialize(votingProceduresDI);
            transactionBody.setVotingProcedures(votingProcedures);
        }

        //proposal procedures
        Array proposalProceduresArray = (Array) bodyMap.get(new UnsignedInteger(20));
        if (proposalProceduresArray != null && proposalProceduresArray.getDataItems().size() > 0) {
            var proposalProceduresDIList = proposalProceduresArray.getDataItems();

            List<ProposalProcedure> proposalProcedureList = new UniqueList<>();
            for (var proposalProcedureDI : proposalProceduresDIList) {
                if (proposalProcedureDI == SimpleValue.BREAK) continue;
                var proposalProcedure = ProposalProcedure.deserialize(proposalProcedureDI);
                proposalProcedureList.add(proposalProcedure);
            }

            if (proposalProcedureList.size() > 0)
                transactionBody.setProposalProcedures(proposalProcedureList);
        }

        //current treasury value
        var currentTreasuryValueDI = (Number) bodyMap.get(new UnsignedInteger(21));
        if (currentTreasuryValueDI != null)
            transactionBody.setCurrentTreasuryValue(currentTreasuryValueDI.getValue());

        //donation
        var donationDI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(22));
        if (donationDI != null)
            transactionBody.setDonation(donationDI.getValue());

        return transactionBody;
    }
}
