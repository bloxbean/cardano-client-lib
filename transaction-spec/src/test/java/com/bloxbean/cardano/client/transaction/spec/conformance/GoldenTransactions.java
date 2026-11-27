package com.bloxbean.cardano.client.transaction.spec.conformance;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeDeregistration;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.ProposalProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.TreasuryWithdrawalsAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transactions built through the public API, whose bytes {@code golden-transactions.txt} pins to those of CCL
 * 0.8.0-pre7 (ADR 0002, gate G2). Key and script credentials are mixed on purpose: Haskell's order for those maps
 * differs from CCL's (D6.1), so changing it must show up here as an intended difference.
 */
final class GoldenTransactions {

    private static final String KEY_A = "aa".repeat(28);
    private static final String KEY_B = "0a".repeat(28);
    private static final String SCRIPT_A = "bb".repeat(28);
    private static final String SCRIPT_B = "0b".repeat(28);
    private static final String TX_ID = "cd".repeat(32);
    private static final byte[] HASH = new byte[32];
    private static final String ADDRESS = AddressProvider.getEntAddress(Credential.fromKey(KEY_A), Networks.testnet())
            .toBech32();

    private GoldenTransactions() {
    }

    /** @return the transactions by name, in file order */
    static Map<String, Transaction> all() {
        Map<String, Transaction> all = new LinkedHashMap<>();
        all.put("payment", tx(body()));
        all.put("multi_asset_and_mint", tx(multiAssetAndMint()));
        all.put("withdrawals_keys", tx(body().withdrawals(withdrawals(Credential.fromKey(KEY_A),
                Credential.fromKey(KEY_B)))));
        all.put("withdrawals_mixed", tx(body().withdrawals(mixedWithdrawals())));
        all.put("certificates", tx(body().certs(new ArrayList<>(List.of(
                new StakeRegistration(StakeCredential.fromKeyHash(hash(KEY_A))),
                new StakeDeregistration(StakeCredential.fromScriptHash(hash(SCRIPT_A))))))));
        all.put("voting_mixed", tx(body().votingProcedures(mixedVotes())));
        all.put("treasury_withdrawals_mixed", tx(proposal(TreasuryWithdrawalsAction.builder()
                .withdrawals(mixedWithdrawals()).build())));
        all.put("update_committee_mixed", tx(proposal(updateCommittee())));
        all.put("parameter_change", tx(proposal(ParameterChangeAction.builder().protocolParamUpdate(parameters())
                .build())));
        all.put("plutus", plutus());
        return all;
    }

    private static TransactionBody.TransactionBodyBuilder body() {
        return TransactionBody.builder()
                .inputs(new ArrayList<>(List.of(TransactionInput.builder().transactionId(TX_ID).index(1).build())))
                .outputs(new ArrayList<>(List.of(TransactionOutput.builder().address(ADDRESS)
                        .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).build()).build())))
                .fee(BigInteger.valueOf(170_000));
    }

    private static Transaction tx(TransactionBody.TransactionBodyBuilder body) {
        return Transaction.builder().body(body.build()).witnessSet(new TransactionWitnessSet()).build();
    }

    private static TransactionBody.TransactionBodyBuilder multiAssetAndMint() {
        List<MultiAsset> assets = List.of(
                MultiAsset.builder().policyId(SCRIPT_A).assets(List.of(
                        Asset.builder().name("0x62626262").value(BigInteger.TEN).build(),
                        Asset.builder().name("0x61").value(BigInteger.ONE).build())).build(),
                MultiAsset.builder().policyId(SCRIPT_B).assets(List.of(
                        Asset.builder().name("0xff").value(BigInteger.TWO).build(),
                        Asset.builder().name("0x01").value(BigInteger.TWO).build())).build());
        return body()
                .outputs(new ArrayList<>(List.of(TransactionOutput.builder().address(ADDRESS)
                        .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).multiAssets(assets).build())
                        .build())))
                .mint(new ArrayList<>(assets));
    }

    private static List<Withdrawal> mixedWithdrawals() {
        return withdrawals(Credential.fromKey(KEY_A), Credential.fromScript(SCRIPT_A), Credential.fromKey(KEY_B),
                Credential.fromScript(SCRIPT_B));
    }

    private static List<Withdrawal> withdrawals(Credential... credentials) {
        List<Withdrawal> withdrawals = new ArrayList<>();
        for (Credential credential : credentials) {
            withdrawals.add(new Withdrawal(
                    AddressProvider.getRewardAddress(credential, Networks.testnet()).toBech32(), BigInteger.TEN));
        }
        return withdrawals;
    }

    private static VotingProcedures mixedVotes() {
        GovActionId action = GovActionId.builder().transactionId(TX_ID).govActionIndex(0).build();
        VotingProcedures votes = new VotingProcedures();
        List.of(new Voter(VoterType.STAKING_POOL_KEY_HASH, Credential.fromKey(KEY_B)),
                new Voter(VoterType.DREP_KEY_HASH, Credential.fromKey(KEY_A)),
                new Voter(VoterType.DREP_SCRIPT_HASH, Credential.fromScript(SCRIPT_A)),
                new Voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH, Credential.fromKey(KEY_A)),
                new Voter(VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH, Credential.fromScript(SCRIPT_B)))
                .forEach(voter -> votes.add(voter, action, new VotingProcedure(Vote.YES, null)));
        return votes;
    }

    private static TransactionBody.TransactionBodyBuilder proposal(GovAction action) {
        return body().proposalProcedures(new ArrayList<>(List.of(ProposalProcedure.builder()
                .deposit(BigInteger.valueOf(100_000_000_000L))
                .rewardAccount(AddressProvider.getRewardAddress(Credential.fromKey(KEY_A), Networks.testnet())
                        .toBech32())
                .govAction(action)
                .anchor(new Anchor("https://example.com/a", HASH))
                .build())));
    }

    private static UpdateCommittee updateCommittee() {
        Set<Credential> removed = new LinkedHashSet<>(List.of(Credential.fromKey(KEY_B),
                Credential.fromScript(SCRIPT_B)));
        Map<Credential, Long> added = new LinkedHashMap<>();
        added.put(Credential.fromKey(KEY_A), 500L);
        added.put(Credential.fromScript(SCRIPT_A), 600L);
        return UpdateCommittee.builder().membersForRemoval(removed).newMembersAndTerms(added)
                .quorumThreshold(new UnitInterval(BigInteger.TWO, BigInteger.valueOf(3))).build();
    }

    private static ProtocolParamUpdate parameters() {
        return ProtocolParamUpdate.builder().minFeeA(BigInteger.valueOf(44)).minFeeB(BigInteger.valueOf(155_381))
                .maxBlockSize(90_112).maxTxSize(16_384).maxBlockHeaderSize(1_100)
                .keyDeposit(BigInteger.valueOf(2_000_000)).build();
    }

    private static Transaction plutus() {
        MapPlutusData datum = new MapPlutusData();
        datum.put(BigIntPlutusData.of(3), BigIntPlutusData.of(4));
        datum.put(BigIntPlutusData.of(1), ListPlutusData.of(BigIntPlutusData.of(2)));
        PlutusV2Script script = PlutusV2Script.builder().cborHex("4e4d01000033222220051200120011").build();
        PlutusData redeemerData = BigIntPlutusData.of(42);

        TransactionWitnessSet witnessSet = new TransactionWitnessSet();
        witnessSet.getPlutusV2Scripts().add(script);
        witnessSet.getPlutusDataList().add(datum);
        witnessSet.getRedeemers().add(Redeemer.builder().tag(RedeemerTag.Spend).index(BigInteger.ZERO)
                .data(redeemerData)
                .exUnits(ExUnits.builder().mem(BigInteger.valueOf(1_000)).steps(BigInteger.valueOf(2_000)).build())
                .build());
        TransactionBody body = body()
                .outputs(new ArrayList<>(List.of(TransactionOutput.builder().address(ADDRESS)
                        .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).build())
                        .inlineDatum(datum).scriptRef(hash("820243010203")).build())))
                .collateral(new ArrayList<>(List.of(TransactionInput.builder().transactionId(TX_ID).index(2).build())))
                .requiredSigners(new ArrayList<>(List.of(hash(KEY_A))))
                .scriptDataHash(HASH)
                .build();
        return Transaction.builder().body(body).witnessSet(witnessSet).build();
    }

    private static byte[] hash(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
