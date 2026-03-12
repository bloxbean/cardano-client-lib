package com.bloxbean.cardano.client.ledger;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationResult;
import com.bloxbean.cardano.client.api.util.PolicyUtil;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.InfoAction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for pure-Java LedgerStateValidator against Yaci DevKit.
 * <p>
 * Each test builds a real transaction via QuickTx, validates it with
 * {@link LedgerStateValidator} (plugged in via {@code withTxValidator()}),
 * submits it to Yaci DevKit, and confirms it lands on-chain.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LedgerValidationIT extends BaseIT {

    private QuickTxBuilder quickTxBuilder;

    // DevKit pool ID
    private static final String POOL_ID = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";

    @BeforeAll
    static void beforeAll() {
        resetDevNet();
    }

    @BeforeEach
    void setup() {
        initializeAccounts();
        topupAllTestAccounts();
        quickTxBuilder = new QuickTxBuilder(getBackendService());
    }

    /**
     * Create a LedgerStateValidator with current protocol params and slot from Yaci DevKit.
     */
    private LedgerStateValidator createValidator() throws Exception {
        ProtocolParams pp = getBackendService().getEpochService()
                .getProtocolParameters().getValue();
        long currentSlot = getBackendService().getBlockService()
                .getLatestBlock().getValue().getSlot();

        return LedgerStateValidator.builder()
                .protocolParams(pp)
                .currentSlot(currentSlot)
                .networkId(NetworkId.TESTNET)
                .build();
    }

    // =============================================
    // Category A: Basic Transactions
    // =============================================

    @Test
    @Order(1)
    void shouldPassSimpleAdaTransfer() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(2))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Simple ADA transfer should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address2);
    }

    @Test
    @Order(2)
    void shouldPassMultiOutputTransaction() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .payToAddress(address3, Amount.ada(1.5))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Multi-output tx should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
    }

    @Test
    @Order(3)
    void shouldPassNativeTokenMinting() throws Exception {
        LedgerStateValidator validator = createValidator();

        Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
        String assetName = "LedgerTestToken";
        BigInteger qty = BigInteger.valueOf(1000);

        Tx tx = new Tx()
                .payToAddress(address2, Amount.asset(policy.getPolicyId(), assetName, 1000))
                .mintAssets(policy.getPolicyScript(), new Asset(assetName, qty))
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.signerFrom(policy))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Native token minting should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
    }

    // =============================================
    // Category B: Stake Certificates
    // =============================================

    @Test
    @Order(10)
    void shouldPassStakeRegistration() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .registerStakeAddress(address1)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Stake registration should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    @Test
    @Order(11)
    void shouldPassStakeDelegation() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .delegateTo(address1, POOL_ID)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Stake delegation should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    @Test
    @Order(12)
    void shouldPassStakeDeregistration() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .deregisterStakeAddress(address1)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.stakeKeySignerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Stake deregistration should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    // =============================================
    // Category C: Governance
    // =============================================

    @Test
    @Order(20)
    void shouldPassDRepRegistration() throws Exception {
        LedgerStateValidator validator = createValidator();

        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .registerDRep(account1, anchor)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account1.drepHdKeyPair()))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "DRep registration should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    @Test
    @Order(21)
    void shouldPassDRepDeregistration() throws Exception {
        LedgerStateValidator validator = createValidator();

        Tx tx = new Tx()
                .unregisterDRep(account1.drepCredential())
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "DRep deregistration should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    @Test
    @Order(30)
    void shouldPassGovernanceProposal() throws Exception {
        // Register stake address for proposal (required for return address)
        try {
            stakeAddressRegistration(address1);
        } catch (Exception e) {
            // May already be registered
        }

        // Re-register DRep for voting test later
        try {
            registerDRep();
        } catch (Exception e) {
            // May already be registered
        }

        LedgerStateValidator validator = createValidator();

        var govAction = new InfoAction();
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(govAction, account1.stakeAddress(), anchor)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Governance proposal should succeed: " + result.getResponse());
        assertNotNull(result.getTxHash());
        waitForTransaction(result);
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    @Test
    @Order(31)
    void shouldPassGovernanceVote() throws Exception {
        // First create a proposal to vote on
        var govAction = new InfoAction();
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx proposalTx = new Tx()
                .createProposal(govAction, account1.stakeAddress(), anchor)
                .from(address1);

        TxResult proposalResult = quickTxBuilder.compose(proposalTx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        assertTrue(proposalResult.isSuccessful(), "Proposal for vote test should succeed: " + proposalResult.getResponse());
        checkIfUtxoAvailable(proposalResult.getTxHash(), address1);

        // Now vote on the proposal with ledger validation
        LedgerStateValidator validator = createValidator();

        var voter = new Voter(VoterType.DREP_KEY_HASH, account1.drepCredential());
        Tx voteTx = new Tx()
                .createVote(voter, new GovActionId(proposalResult.getTxHash(), 0),
                        Vote.YES, anchor)
                .from(address1);

        TxResult voteResult = quickTxBuilder.compose(voteTx)
                .withSigner(SignerProviders.drepKeySignerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account1))
                .withTxValidator(validator)
                .completeAndWait();

        assertTrue(voteResult.isSuccessful(), "Governance vote should succeed: " + voteResult.getResponse());
        assertNotNull(voteResult.getTxHash());
        waitForTransaction(voteResult);
        checkIfUtxoAvailable(voteResult.getTxHash(), address1);
    }

    // =============================================
    // Category D: Negative Tests
    // =============================================

    @Test
    @Order(40)
    void shouldReturnStructuredErrorOnBadTransaction() throws Exception {
        LedgerStateValidator validator = createValidator();

        // Build a manually-constructed bad transaction with empty inputs
        com.bloxbean.cardano.client.transaction.spec.Transaction badTx =
                new com.bloxbean.cardano.client.transaction.spec.Transaction();
        badTx.setBody(new com.bloxbean.cardano.client.transaction.spec.TransactionBody());
        badTx.getBody().setInputs(new java.util.ArrayList<>());
        badTx.getBody().setOutputs(java.util.List.of(
                com.bloxbean.cardano.client.transaction.spec.TransactionOutput.builder()
                        .address(address1)
                        .value(com.bloxbean.cardano.client.transaction.spec.Value.builder()
                                .coin(java.math.BigInteger.valueOf(2_000_000)).build())
                        .build()
        ));
        badTx.getBody().setFee(java.math.BigInteger.valueOf(200_000));
        badTx.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());

        ValidationResult validationResult = validator.validateTx(badTx, Set.of());

        assertFalse(validationResult.isValid(), "Empty inputs tx should fail validation");
        assertFalse(validationResult.getErrors().isEmpty(), "Should have error details");
        System.out.println("Validation errors: " + validationResult.getErrors());
    }

    // =============================================
    // Helper methods
    // =============================================

    private void stakeAddressRegistration(String addressToRegister) {
        Tx tx = new Tx()
                .registerStakeAddress(addressToRegister)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        assertTrue(result.isSuccessful(), "Stake address registration should succeed: " + result.getResponse());
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }

    private void registerDRep() {
        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .registerDRep(account1, anchor)
                .from(address1);

        TxResult result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account1.drepHdKeyPair()))
                .completeAndWait();

        assertTrue(result.isSuccessful(), "DRep registration should succeed: " + result.getResponse());
        checkIfUtxoAvailable(result.getTxHash(), address1);
    }
}
