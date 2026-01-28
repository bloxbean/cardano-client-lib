package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerScopes;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SignerIT extends BaseIT {

    private DefaultSignerRegistry registry;
    private QuickTxBuilder quickTxBuilder;

    @BeforeEach
    void setup() {
        initializeAccounts();
        topupAllTestAccounts();

        registry = new DefaultSignerRegistry()
                .addAccount("account://sender1", account1)
                .addAccount("account://sender2", account2)
                .addAccount("account://sender3", account3)
                .addAccount("account://sender4", account4);

        quickTxBuilder = new QuickTxBuilder(getBackendService());
    }

    @Test
    @Order(1)
    void compose_with_signer_registry_refs() {
        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .payToAddress(address4, Amount.ada(1.1))
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2");

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .completeAndWait();

        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
    }

    @Test
    @Order(2)
    void payment_with_stake_registrations_deregistration() {
        registerStakeAddress();
        deRegisterStakeAddress();
    }

    @Test
    @Order(3)
    void drep_registration_and_deregistration() {
        registerDRep();
        deregisterDRep();
    }

    @Test
    @Order(4)
    void stake_delegation_with_signer_ref() {
        delegateStake();
    }

    @Test
    @Order(5)
    void drep_update_with_signer_ref() {
        updateDRep();
    }

    @Test
    @Order(6)
    void voting_delegation_with_signer_ref() {
        delegateVotingPower();
    }

    @Test
    @Order(7)
    void combined_operations_multi_scope() {
        combinedOperations();
    }

    private void registerStakeAddress() {
        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .registerStakeAddress(address4)
                .payToAddress(address4, Amount.ada(1.1))
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2");

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
        checkIfUtxoAvailable(result.getTxHash(), address4);
    }

    private void deRegisterStakeAddress() {
        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .payToAddress(address4, Amount.ada(1.1))
                .fromRef("account://sender2");

        Tx tx3 = new Tx()
                .deregisterStakeAddress(address4)
                .fromRef("account://sender4");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2, tx3))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2")
                .withSigner("account://sender4")
                .withSigner("account://sender4", SignerScopes.STAKE);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
        checkIfUtxoAvailable(result.getTxHash(), address4);
    }

    private void registerDRep() {
        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .registerDRep(account2)
                .payToAddress(address2, Amount.ada(1.1))
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2")
                .withSigner("account://sender2", SignerScopes.DREP);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
        checkIfUtxoAvailable(result.getTxHash(), address2);
    }

    private void deregisterDRep() {
        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .unregisterDRep(account2.drepCredential(), address2)
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2")
                .withSigner("account://sender2", SignerScopes.DREP);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
    }

    private void delegateStake() {
        String poolId = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";

        Tx stakeKeyRegTx = new Tx()
                .registerStakeAddress(address3)
                .from(address3);

        var stakeKeyRegPlan = TxPlan.from(List.of(stakeKeyRegTx));

        var result = quickTxBuilder.compose(stakeKeyRegPlan)
                .withSigner(SignerProviders.signerFrom(account3))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertTrue(result.isSuccessful());

        Tx delegationTx = new Tx()
                .delegateTo(address3, poolId)
                .fromRef("account://sender3");

        TxPlan delegationPlan = TxPlan.from(List.of(delegationTx))
                .withSigner("account://sender3")
                .withSigner("account://sender3", SignerScopes.STAKE);

        String yaml = delegationPlan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan delgTxPlan = TxPlan.from(yaml);

        TxResult result1 = quickTxBuilder
                .compose(delgTxPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        waitForTransaction(result1);
        checkIfUtxoAvailable(result1.getTxHash(), address3);
    }

    private void updateDRep() {

        //Try to register first before update
        try {
            registerDRep();
        } catch (Error e) {}

        Anchor anchor = new Anchor("https://example.com/drep",
                HexUtil.decodeHexString("0000000000000000000000000000000000000000000000000000000000000000"));

        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Tx tx2 = new Tx()
                .updateDRep(account2.drepCredential(), anchor)
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender2")
                .withSigner("account://sender2", SignerScopes.DREP);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(getBackendService());
        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
    }

    private void delegateVotingPower() {

        try {
            registerStakeAddress();
        } catch (Error e) {}

        try {
            registerDRep();
        } catch (Error e) {}

        DRep drep = DRep.addrKeyHash(account3.drepCredential().getBytes());

        Tx tx1 = new Tx()
                .payToAddress(address3, Amount.ada(1.2))
                .fromRef("account://sender1");

        Anchor anchor = Anchor.builder()
                .anchorUrl("http://test.com")
                .anchorDataHash(Blake2bUtil.blake2bHash256("hello".getBytes()))
                .build();

        Tx tx2 = new Tx()
                .delegateVotingPowerTo(address4, drep)
                .fromRef("account://sender4");

        TxPlan plan = TxPlan.from(List.of(tx1, tx2))
                .feePayerRef("account://sender1")
                .withSigner("account://sender1")
                .withSigner("account://sender4")
                .withSigner("account://sender4", SignerScopes.STAKE)
                .withSigner("account://sender3", SignerScopes.DREP);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(getBackendService());
        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address3);
    }

    private void combinedOperations() {
        try {
            deRegisterStakeAddress();
        } catch (Error e) {}

        try {
            deregisterDRep();
        } catch (Error e) {}

        Tx tx = new Tx()
                .registerStakeAddress(address1)
                .registerDRep(account1)
                .payToAddress(address2, Amount.ada(3.0))
                .fromRef("account://sender2");

        TxPlan plan = TxPlan.from(tx)
                .feePayerRef("account://sender1")
                .withSigner("account://sender2")
                .withSigner("account://sender1")
                .withSigner("account://sender1", SignerScopes.STAKE)
                .withSigner("account://sender1", SignerScopes.DREP);

        String yaml = plan.toYaml();
        System.out.println(yaml);

        //Deserialize
        TxPlan txPlan = TxPlan.from(yaml);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(getBackendService());
        TxResult result = quickTxBuilder
                .compose(txPlan)
                .withSignerRegistry(registry)
                .withTxInspector(transaction -> {
                    System.out.println(JsonUtil.getPrettyJson(transaction));
                })
                .completeAndWait();

        System.out.println(result);
        assertTrue(result.isSuccessful());

        waitForTransaction(result);

        checkIfUtxoAvailable(result.getTxHash(), address2);
    }


}
