package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.util.AddressUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.custom.SortedMap;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.raw.RawAccountBalanceInterval;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;
import com.bloxbean.cardano.client.transaction.raw.RawInput;
import com.bloxbean.cardano.client.transaction.raw.RawMultiAsset;
import com.bloxbean.cardano.client.transaction.raw.RawOutput;
import com.bloxbean.cardano.client.transaction.raw.RawProposal;
import com.bloxbean.cardano.client.transaction.raw.RawTxBody;
import com.bloxbean.cardano.client.transaction.raw.RawUpdate;
import com.bloxbean.cardano.client.transaction.raw.RawWithdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.transaction.util.UniqueList;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.createArray;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.isDijkstra;
import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireDijkstra;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    //Dijkstra
    /**
     * The guards (key 14), written in Dijkstra as credentials in a set with tag 258, in list order. Key 14 holds the
     * guards or {@link #requiredSigners}, not both.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Credential> guards; //14

    /**
     * The sub-transactions (key 23) of a top-level body, written in Dijkstra as an array in list order.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SubTransaction> subTransactions; //23

    /**
     * The required top-level guards (key 24), written in Dijkstra as a map in Haskell's order of credentials.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<RequiredGuard> requiredTopLevelGuards; //24

    /**
     * The direct deposits (key 25), a reward address and a coin each, written in Dijkstra as a map in Haskell's order
     * of reward accounts.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Withdrawal> directDeposits; //25

    /**
     * The account balance intervals (key 26), written in Dijkstra as a map in Haskell's order of reward accounts.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AccountBalanceInterval> accountBalanceIntervals; //26

    /**
     * The starting account balance intervals (key 27) of a top-level body, written in Dijkstra as a map in Haskell's
     * order of reward accounts.
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AccountBalanceInterval> startingAccountBalanceIntervals; //27

    /**
     * The constructor of the fields before Dijkstra, kept for source and binary compatibility; use the builder for the
     * Dijkstra fields.
     */
    @SuppressWarnings("java:S107") // the old signature, kept for callers
    public TransactionBody(List<TransactionInput> inputs, List<TransactionOutput> outputs, BigInteger fee, long ttl,
                           List<Certificate> certs, List<Withdrawal> withdrawals, Update update,
                           byte[] auxiliaryDataHash, long validityStartInterval, List<MultiAsset> mint,
                           byte[] scriptDataHash, List<TransactionInput> collateral, List<byte[]> requiredSigners,
                           NetworkId networkId, TransactionOutput collateralReturn, BigInteger totalCollateral,
                           List<TransactionInput> referenceInputs, VotingProcedures votingProcedures,
                           List<ProposalProcedure> proposalProcedures, BigInteger currentTreasuryValue,
                           BigInteger donation) {
        this(inputs, outputs, fee, ttl, certs, withdrawals, update, auxiliaryDataHash, validityStartInterval, mint,
                scriptDataHash, collateral, requiredSigners, networkId, collateralReturn, totalCollateral,
                referenceInputs, votingProcedures, proposalProcedures, currentTreasuryValue, donation, null, null,
                null, null, null, null);
    }

    public Map serialize() throws CborSerializationException, AddressExcepion {
        return serialize(EraSerializationConfig.INSTANCE.getEra());
    }

    public Map serialize(Era era) throws CborSerializationException, AddressExcepion {
        return serialize(era, false);
    }

    /**
     * Writes the body of a top-level transaction or, from Dijkstra, of a sub-transaction, which has no fee (its default
     * of zero is not written), collateral, collateral return, total collateral, sub-transactions or starting intervals.
     * A field the era has no place for is an error, not dropped (ADR 0004 D1).
     */
    Map serialize(Era era, boolean subTransaction) throws CborSerializationException, AddressExcepion {
        if (subTransaction)
            checkSubTransactionBody();
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

        if (!subTransaction) { //a sub-transaction has no fee
            if (fee != null) {
                bodyMap.put(new UnsignedInteger(2), new UnsignedInteger(fee)); //fee
            } else {
                throw new CborSerializationException("Fee cannot be null");
            }
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

        //guards: from Dijkstra, credentials in key 14
        if (guards != null && !guards.isEmpty()) {
            requireDijkstra(era, "TransactionBody.guards");
            if (requiredSigners != null && !requiredSigners.isEmpty())
                throw new CborSerializationException(
                        "TransactionBody.requiredSigners and guards both fill key 14: set only one of them");
            Array guardArray = createArray(era);
            for (Credential guard : guards)
                guardArray.add(CredentialSerializer.serialize(guard));
            bodyMap.put(new UnsignedInteger(14), guardArray);
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
                //before Dijkstra as before ADR 0004, in the configured era
                proposalProceduresArray.add(isDijkstra(era) ? proposalProcedure.serialize(era)
                        : proposalProcedure.serialize());
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

        serializeDijkstraFields(bodyMap, era);
        return bodyMap;
    }

    // Keys 23 to 27.
    private void serializeDijkstraFields(Map bodyMap, Era era) throws CborSerializationException, AddressExcepion {
        if (subTransactions != null && !subTransactions.isEmpty()) {
            requireDijkstra(era, "TransactionBody.subTransactions");
            Array subTransactionArray = new Array();
            for (SubTransaction subTransaction : subTransactions)
                subTransactionArray.add(subTransaction.serialize(era));
            bodyMap.put(new UnsignedInteger(23), subTransactionArray);
        }

        if (requiredTopLevelGuards != null && !requiredTopLevelGuards.isEmpty()) {
            requireDijkstra(era, "TransactionBody.requiredTopLevelGuards");
            List<RequiredGuard> sorted = new ArrayList<>(requiredTopLevelGuards);
            sorted.sort(Comparator.comparing(RequiredGuard::getCredential, Comparators.CREDENTIAL));
            Map guardMap = new SortedMap();
            for (RequiredGuard guard : sorted)
                guardMap.put(CredentialSerializer.serialize(guard.getCredential()),
                        guard.getData() != null ? guard.getData().serialize() : SimpleValue.NULL);
            bodyMap.put(new UnsignedInteger(24), guardMap);
        }

        if (directDeposits != null && !directDeposits.isEmpty()) {
            requireDijkstra(era, "TransactionBody.directDeposits");
            bodyMap.put(new UnsignedInteger(25), Withdrawal.serializeInLedgerOrder(directDeposits));
        }

        putIntervals(bodyMap, 26, "TransactionBody.accountBalanceIntervals", accountBalanceIntervals, era);
        putIntervals(bodyMap, 27, "TransactionBody.startingAccountBalanceIntervals", startingAccountBalanceIntervals,
                era);
    }

    private static void putIntervals(Map bodyMap, int key, String field, List<AccountBalanceInterval> intervals,
                                     Era era) throws CborSerializationException, AddressExcepion {
        if (intervals == null || intervals.isEmpty())
            return;
        requireDijkstra(era, field);
        List<AccountBalanceInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(AccountBalanceInterval::getRewardAddress, Comparators.REWARD_ADDRESS));
        Map intervalMap = new SortedMap();
        for (AccountBalanceInterval interval : sorted)
            intervalMap.put(new ByteString(AddressUtil.addressToBytes(interval.getRewardAddress())),
                    interval.serializeInterval());
        bodyMap.put(new UnsignedInteger(key), intervalMap);
    }

    // A sub-transaction body has none of the top-level-only fields: a fee other than zero, collateral, a collateral
    // return, total collateral, sub-transactions or starting intervals.
    private void checkSubTransactionBody() throws CborSerializationException {
        String field = fee != null && fee.signum() != 0 ? "fee"
                : collateral != null && !collateral.isEmpty() ? "collateral"
                : collateralReturn != null ? "collateralReturn"
                : totalCollateral != null ? "totalCollateral"
                : subTransactions != null && !subTransactions.isEmpty() ? "subTransactions"
                : startingAccountBalanceIntervals != null && !startingAccountBalanceIntervals.isEmpty()
                ? "startingAccountBalanceIntervals" : null;
        if (field != null)
            throw new CborSerializationException("A sub-transaction body has no " + field);
    }

    public static TransactionBody deserialize(Map bodyMap) throws CborDeserializationException {
        try {
            return from(RawTxBody.lenient(CborSerializationUtil.span(bodyMap), RawModels.READ_IN));
        } catch (CborRuntimeException e) {
            throw new CborDeserializationException("TransactionBody deserialization failed", e);
        }
    }

    // The body in the view's era: the Dijkstra fields are read only in Dijkstra.
    static TransactionBody from(RawTxBody raw) throws CborDeserializationException {
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

        Optional<List<RawCredential>> guards = raw.guards();
        if (guards.isPresent())
            body.setGuards(RawModels.mapAll(guards.get(), RawModels::credential));
        body.setSubTransactions(orNull(raw.subTransactions(), sub -> SubTransaction.from(sub, raw.era())));
        body.setRequiredTopLevelGuards(orNull(raw.requiredTopLevelGuards(), guard -> new RequiredGuard(
                RawModels.credential(guard.credential()),
                guard.data() != null ? RawModels.toPlutusData(guard.data()) : null)));
        body.setDirectDeposits(orNull(raw.directDeposits(), Withdrawal::from));
        body.setAccountBalanceIntervals(orNull(raw.accountBalanceIntervals(), TransactionBody::interval));
        body.setStartingAccountBalanceIntervals(orNull(raw.startingAccountBalanceIntervals(),
                TransactionBody::interval));
        return body;
    }

    // The models of a Dijkstra field's entries, or null if it has none.
    private static <R, M> List<M> orNull(List<R> raw, RawModels.Mapper<R, M> mapper)
            throws CborDeserializationException {
        return raw.isEmpty() ? null : RawModels.mapAll(raw, mapper);
    }

    private static AccountBalanceInterval interval(RawAccountBalanceInterval raw) {
        return new AccountBalanceInterval(RawModels.rewardAddress(raw.rewardAccount()), raw.exact(), raw.lower(),
                raw.upper());
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
