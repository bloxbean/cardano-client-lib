package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeCredential;
import com.bloxbean.cardano.client.transaction.spec.cert.StakeRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.TreasuryWithdrawalsAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CIP-21: every map in a transaction body that a hardware wallet can sign has its keys sorted, shorter first, then by
 * unsigned bytes. The check walks the encoded bytes with its own rule, the one Ledger's and Trezor's apps apply, so it
 * does not rely on {@link Comparators} or on the encoder.
 */
class Cip21BodyOrderTest {

    private static final String LOW = "0a".repeat(28);
    private static final String HIGH = "aa".repeat(28);

    private static Withdrawal withdrawal(Credential credential, Network network) {
        return new Withdrawal(AddressProvider.getRewardAddress(credential, network).toBech32(), BigInteger.ONE);
    }

    /** The upper-case id sorts first as hex text and last as bytes. */
    private static List<MultiAsset> assets() {
        return List.of(
                MultiAsset.builder().policyId(HIGH).assets(assetNames()).build(),
                MultiAsset.builder().policyId(LOW).assets(assetNames()).build(),
                MultiAsset.builder().policyId("BB".repeat(28)).assets(assetNames()).build());
    }

    private static List<Asset> assetNames() {
        return List.of(asset("0x0000"), asset("0xff"), asset("0x80"), asset("0x01"));
    }

    private static Asset asset(String name) {
        return Asset.builder().name(name).value(BigInteger.ONE).build();
    }

    private static TransactionBody body() {
        String address = AddressProvider.getEntAddress(Credential.fromKey(HIGH), Networks.testnet()).toBech32();
        TransactionOutput output = TransactionOutput.builder().address(address)
                .value(Value.builder().coin(BigInteger.valueOf(2_000_000)).multiAssets(assets()).build()).build();
        TransactionInput input = TransactionInput.builder().transactionId("cd".repeat(32)).build();
        VotingProcedures votes = new VotingProcedures();
        votes.add(new Voter(VoterType.DREP_KEY_HASH, Credential.fromKey(LOW)), new GovActionId("ff".repeat(32), 24),
                new VotingProcedure(Vote.YES, null));

        return TransactionBody.builder()
                .inputs(new ArrayList<>(List.of(input)))
                .outputs(new ArrayList<>(List.of(output)))
                .fee(BigInteger.valueOf(170_000))
                .withdrawals(new ArrayList<>(List.of(
                        withdrawal(Credential.fromScript(LOW), Networks.testnet()),
                        withdrawal(Credential.fromKey(HIGH), Networks.testnet()),
                        withdrawal(Credential.fromScript(HIGH), Networks.testnet()),
                        withdrawal(Credential.fromKey(LOW), Networks.testnet()))))
                .certs(new ArrayList<>(List.of(new StakeRegistration(StakeCredential.fromKeyHash(hash(LOW))))))
                .mint(new ArrayList<>(assets()))
                .collateral(new ArrayList<>(List.of(input)))
                .requiredSigners(new ArrayList<>(List.of(hash(LOW))))
                .collateralReturn(output)
                .referenceInputs(new ArrayList<>(List.of(input)))
                .votingProcedures(votes)
                .build();
    }

    @Test
    void builtTransactionBodyIsInOrder() throws Exception {
        Transaction transaction = Transaction.builder().body(body()).witnessSet(new TransactionWitnessSet()).build();

        Array decoded = (Array) CborSerializationUtil.deserialize(transaction.serialize());

        assertMeetsCip21(decoded.getDataItems().get(0));
    }

    /** Writing the decoded items back keeps the bytes only if each integer and length is minimal. */
    @Test
    void builtTransactionIsMinimal() throws Exception {
        byte[] bytes = Transaction.builder().body(body()).witnessSet(new TransactionWitnessSet()).build().serialize();

        assertThat(CborSerializationUtil.serialize(CborSerializationUtil.deserialize(bytes), false)).isEqualTo(bytes);
    }

    /** Certificates, then inputs of each kind and required signers: CIP-21 wants tag 258 on all sets or on none. */
    @Test
    void everySetInTheBodyHasTheTag() throws Exception {
        Map map = (Map) CborSerializationUtil.deserialize(CborSerializationUtil.serialize(body().serialize()));

        for (int key : List.of(0, 4, 13, 14, 18)) {
            assertThat(map.get(new UnsignedInteger(key)).getTag().getValue()).as("body key %d", key).isEqualTo(258);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bodyIsInOrderInBothEncoderModes(boolean canonical) throws Exception {
        assertMeetsCip21(CborSerializationUtil.deserialize(
                CborSerializationUtil.serialize(body().serialize(), canonical)));
    }

    /** Shows the check can fail, on the one map kept in Haskell's order that a mixed input would put out of CIP-21's. */
    @Test
    void checkRejectsScriptBeforeKey() throws Exception {
        Map treasuryMap = (Map) ((Array) TreasuryWithdrawalsAction.builder().withdrawals(List.of(
                withdrawal(Credential.fromKey(LOW), Networks.testnet()),
                withdrawal(Credential.fromScript(LOW), Networks.testnet()))).build().serialize())
                .getDataItems().get(1);

        assertThatThrownBy(() -> assertMeetsCip21(
                CborSerializationUtil.deserialize(CborSerializationUtil.serialize(treasuryMap))))
                .isInstanceOf(AssertionError.class);
    }

    private static void assertMeetsCip21(DataItem item) throws Exception {
        if (item instanceof Array array) {
            assertThat(array.isChunked()).as("indefinite array").isFalse();
            for (DataItem element : array.getDataItems()) {
                assertMeetsCip21(element);
            }
        } else if (item instanceof Map map) {
            assertThat(map.isChunked()).as("indefinite map").isFalse();
            byte[] previous = null;
            for (DataItem key : map.getKeys()) {
                byte[] encoded = CborSerializationUtil.serialize(key, false);
                if (previous != null) {
                    assertThat(lengthFirst(previous, encoded)).as("key after %s", Arrays.toString(previous))
                            .isNegative();
                }
                previous = encoded;
                assertMeetsCip21(key);
                assertMeetsCip21(map.get(key));
            }
        }
    }

    private static byte[] hash(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private static int lengthFirst(byte[] a, byte[] b) {
        return a.length != b.length ? Integer.compare(a.length, b.length) : Arrays.compareUnsigned(a, b);
    }
}
