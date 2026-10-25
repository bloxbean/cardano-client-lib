package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.cert.SingleHostAddr;
import com.bloxbean.cardano.client.transaction.spec.cert.SingleHostName;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.InfoAction;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One deterministic TxPlan document per core intent type, written by the canonical writer ({@code TxPlan.toYaml()}).
 * {@code TxPlanStrictReadingTest} checks that every {@code @JsonSubTypes} name is covered, so a new intent type needs a
 * sample here.
 */
final class IntentSamples {

    static final String SENDER = "addr_test1qpcf5ursqpwx2tp8maeah00rxxdfpvf8h65k4hk3chac0fvu28duly863yqhgjtl8an2pkksd6mlzv0qv4nejh5u2zjsshr90k";
    static final String RECEIVER = "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp";
    static final String SCRIPT_ADDRESS = "addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqq9pqm7e";
    static final String STAKE_ADDRESS = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";
    static final String POOL_ID = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy";
    static final String TX_HASH = "12745f09b138d4d0a11a560b4591ebb830cf12336347606d2edbbf1893d395c6";
    static final String KEY_HASH = "ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884";

    private IntentSamples() {
    }

    /**
     * @return intent type name → TxPlan YAML containing that intent, in a stable order
     */
    static Map<String, String> documents() {
        Map<String, Supplier<Tx>> samples = new LinkedHashMap<>();
        samples.put("payment", () -> tx().payToAddress(RECEIVER, List.of(Amount.ada(2), Amount.asset(policyId(), "token", 5))));
        samples.put("donation", () -> tx().donateToTreasury(BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(2_000_000)));
        samples.put("minting", () -> tx().mintAssets(nativeScript(), new Asset("token", BigInteger.TEN), RECEIVER));
        samples.put("metadata", () -> tx().attachMetadata(metadata()));
        samples.put("stake_registration", () -> tx().registerStakeAddress(SENDER));
        samples.put("stake_deregistration", () -> tx().deregisterStakeAddress(SENDER, RECEIVER));
        samples.put("stake_delegation", () -> tx().delegateTo(SENDER, POOL_ID));
        samples.put("stake_withdrawal", () -> tx().withdraw(STAKE_ADDRESS, BigInteger.valueOf(5_000_000), RECEIVER));
        samples.put("pool_registration", () -> tx().registerPool(poolRegistration()));
        samples.put("pool_update", () -> tx().updatePool(poolRegistration()));
        samples.put("pool_retirement", () -> tx().retirePool(POOL_ID, 300));
        samples.put("drep_registration", () -> tx().registerDRep(drepCredential(), anchor()));
        samples.put("drep_deregistration", () -> tx().unregisterDRep(drepCredential(), RECEIVER, BigInteger.valueOf(500_000_000)));
        samples.put("drep_update", () -> tx().updateDRep(drepCredential(), anchor()));
        samples.put("governance_proposal", () -> tx().createProposal(new InfoAction(), STAKE_ADDRESS, anchor()));
        samples.put("voting", () -> tx().createVote(voter(), new GovActionId(TX_HASH, 0), Vote.YES, anchor()));
        samples.put("voting_delegation", () -> tx().delegateVotingPowerTo(SENDER, DRep.abstain()));
        samples.put("script_collect_from", () -> tx().collectFrom(List.of(utxo()), BigIntPlutusData.of(1), BigIntPlutusData.of(2)));
        samples.put("script_minting", () -> tx().mintAsset(plutusScript(), new Asset("token", BigInteger.ONE), BigIntPlutusData.of(1), RECEIVER));
        samples.put("validator", () -> tx().attachSpendingValidator(plutusScript()));
        samples.put("native_script", () -> tx().attachNativeScript(nativeScript()));
        samples.put("reference_input", () -> tx().readFrom(TX_HASH, 1));
        samples.put("collect_from", () -> tx().collectFrom(List.of(utxo())));

        Map<String, String> documents = new LinkedHashMap<>();
        samples.forEach((type, tx) -> documents.put(type, TxPlan.from(tx.get()).feePayer(SENDER).toYaml()));
        return documents;
    }

    private static Tx tx() {
        return new Tx().from(SENDER);
    }

    private static String policyId() {
        try {
            return nativeScript().getPolicyId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ScriptPubkey nativeScript() {
        return new ScriptPubkey(KEY_HASH);
    }

    private static PlutusV2Script plutusScript() {
        return PlutusV2Script.builder().type("PlutusScriptV2").cborHex("49480100002221200101").build();
    }

    private static Metadata metadata() {
        return MetadataBuilder.createMetadata().put(BigInteger.valueOf(674), "TxPlan schema sample");
    }

    private static Utxo utxo() {
        return Utxo.builder().txHash(TX_HASH).outputIndex(0).address(SCRIPT_ADDRESS)
                .amount(List.of(Amount.ada(5))).build();
    }

    private static Credential drepCredential() {
        return Credential.fromKey(KEY_HASH);
    }

    private static Anchor anchor() {
        return new Anchor("https://example.com/anchor.json",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));
    }

    private static Voter voter() {
        return Voter.builder().credential(drepCredential()).type(VoterType.DREP_KEY_HASH).build();
    }

    private static PoolRegistration poolRegistration() {
        try {
            return PoolRegistration.builder()
                    .operator(HexUtil.decodeHexString("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4"))
                    .vrfKeyHash(HexUtil.decodeHexString("b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b"))
                    .pledge(BigInteger.valueOf(6_600_000_000_000L))
                    .cost(BigInteger.valueOf(340_000_000))
                    .margin(new UnitInterval(BigInteger.valueOf(13), BigInteger.valueOf(1000)))
                    .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                    .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                    .relays(List.of(
                            SingleHostAddr.builder().port(3001).ipv4((Inet4Address) Inet4Address.getByName("54.177.41.35")).build(),
                            SingleHostName.builder().port(3001).dnsName("relay.example.com").build()))
                    .poolMetadataUrl("https://git.io/JttTl")
                    .poolMetadataHash("51700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41")
                    .build();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
