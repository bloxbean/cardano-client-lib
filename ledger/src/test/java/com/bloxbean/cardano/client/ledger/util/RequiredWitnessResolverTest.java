package com.bloxbean.cardano.client.ledger.util;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.ledger.slice.SimpleUtxoSlice;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredWitnessResolverTest {

    private static final String TX_HASH = "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb";
    private static final String POLICY_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef01";
    // A testnet VKey base address
    private static final String VKEY_ADDR = "addr_test1qzx9hu8j4ah3auytk0mwcupd69hpc52t0cw39a65ndrah86djs784u92a3m5w475w3w35tyd6v3qumkze80j8a6h5tuqq5xe8y";
    private static final String STAKE_ADDR = "stake_test1uqfu74w3wh4gfzu8m6e7j987h4lq9r3t7ef5gaw497uu85qsqfy27";

    @Test
    void resolve_inputsRequireVKeyWitnesses() {
        TransactionInput input = new TransactionInput(TX_HASH, 0);
        TransactionOutput output = TransactionOutput.builder()
                .address(VKEY_ADDR)
                .value(Value.builder().coin(BigInteger.valueOf(5000000)).build())
                .build();

        Map<TransactionInput, TransactionOutput> utxoMap = new HashMap<>();
        utxoMap.put(input, output);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(input))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(utxoMap));
        assertThat(reqs.getRequiredVKeyHashes()).isNotEmpty();
    }

    @Test
    void resolve_requiredSignersAddedDirectly() {
        byte[] keyHash = new byte[28];
        Arrays.fill(keyHash, (byte) 0xAA);

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .requiredSigners(List.of(keyHash))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredVKeyHashes()).contains(HexUtil.encodeHexString(keyHash));
    }

    @Test
    void resolve_mintPolicyIdsAreScriptHashes() {
        MultiAsset mint = MultiAsset.builder()
                .policyId(POLICY_ID)
                .assets(List.of(Asset.builder().name("token").value(BigInteger.valueOf(100)).build()))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .mint(List.of(mint))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredScriptHashes()).contains(POLICY_ID);
    }

    @Test
    void resolve_stakeDeregistrationRequiresWitness() {
        byte[] keyHash = new byte[28];
        Arrays.fill(keyHash, (byte) 0xBB);
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromKeyHash(keyHash));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .certs(List.of(cert))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredVKeyHashes()).contains(HexUtil.encodeHexString(keyHash));
    }

    @Test
    void resolve_scriptCertRequiresScriptWitness() {
        byte[] scriptHash = new byte[28];
        Arrays.fill(scriptHash, (byte) 0xCC);
        StakeDeregistration cert = new StakeDeregistration(StakeCredential.fromScriptHash(scriptHash));

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .certs(List.of(cert))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredScriptHashes()).contains(HexUtil.encodeHexString(scriptHash));
    }

    @Test
    void resolve_poolRegistrationRequiresOperatorAndOwners() {
        byte[] operator = new byte[28];
        Arrays.fill(operator, (byte) 0xDD);
        String ownerHash = HexUtil.encodeHexString(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xAA,
                (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE});

        PoolRegistration cert = PoolRegistration.builder()
                .operator(operator)
                .poolOwners(Set.of(ownerHash))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .certs(List.of(cert))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredVKeyHashes())
                .contains(HexUtil.encodeHexString(operator))
                .contains(ownerHash);
    }

    @Test
    void resolve_drepCertRequiresCredentialWitness() {
        byte[] keyBytes = new byte[28];
        Arrays.fill(keyBytes, (byte) 0xEE);
        RegDRepCert cert = RegDRepCert.builder()
                .drepCredential(Credential.fromKey(keyBytes))
                .coin(BigInteger.valueOf(500000000))
                .build();

        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput(TX_HASH, 0)))
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .certs(List.of(cert))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredVKeyHashes()).contains(HexUtil.encodeHexString(keyBytes));
    }

    @Test
    void resolve_noInputs_emptyResult() {
        Transaction tx = Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of())
                        .outputs(List.of())
                        .fee(BigInteger.valueOf(200000))
                        .build())
                .build();

        var reqs = RequiredWitnessResolver.resolve(tx, new SimpleUtxoSlice(Collections.emptyMap()));
        assertThat(reqs.getRequiredVKeyHashes()).isEmpty();
        assertThat(reqs.getRequiredScriptHashes()).isEmpty();
    }
}
