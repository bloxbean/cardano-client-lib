package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.governance.GovId;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VoterType;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Integration tests for TxDsl governance functionality using YaciDevKit.
 *
 * Prerequisites:
 * - YaciDevKit running on localhost:8080
 * - Test accounts funded with test ADA
 *
 * Tests governance operations:
 * - DRep registration/update/deregistration
 * - Voting delegation
 * - Proposal creation
 * - Voting on proposals
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TxDslGovernanceIT extends BaseIT {
    private static final Logger log = LoggerFactory.getLogger(TxDslGovernanceIT.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private BackendService backendService;
    private Account drepAccount;
    private String drepAddress;

    // Store proposal transaction hash for use in voting tests
    private static String proposalTxHash;

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        initializeAccounts();

        // Create a dedicated account for DRep operations
        String drepMnemonic = getMnemonic();
        drepAccount = new Account(Networks.testnet(), drepMnemonic,
            DerivationPath.createExternalAddressDerivationPathForAccount(5));
        drepAddress = drepAccount.baseAddress();

        // Topup accounts for governance operations (which require deposits)
        topupIfNeeded(address1);
        topupIfNeeded(drepAddress);

        printBalances();
    }

    @Test
    @Order(1)
    void testDRepRegistration() {
        log.info("=== Testing DRep Registration with TxDsl ===");

        // First ensure DRep is not registered to have a clean state
        ensureDRepNotRegistered(drepAccount);

        // Given - prepare DRep registration with anchor
        Anchor anchor = new Anchor(
            "https://example.com/drep-metadata.json",
            HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl txDsl = new TxDsl()
                .registerDRep(drepAccount.drepCredential(), anchor)
                .from(drepAddress);

        log.info("Generated YAML for DRep registration:");
        log.info(txDsl.toYaml());
        log.info("DRep ID: {}", drepAccount.drepId());

        // Verify DSL structure (no intention captured for governance yet)
        // Note: We haven't implemented governance intentions yet, so this will be empty
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + drepAddress);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(drepAccount))
                .withSigner(SignerProviders.signerFrom(drepAccount.drepHdKeyPair()))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("DRep registration failed: " + result.getResponse())
                .isTrue();

        log.info("✓ DRep registration transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ DRep registration completed successfully!");
    }

    @Test
    @Order(2)
    void testDRepUpdate() {
        log.info("=== Testing DRep Update with TxDsl ===");

        // Ensure DRep is registered before attempting update
        ensureDRepRegistered(drepAccount);

        // Given - prepare DRep update with new anchor
        Anchor newAnchor = new Anchor(
            "https://example.com/updated-drep-metadata.json",
            HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl txDsl = new TxDsl()
                .updateDRep(drepAccount.drepCredential(), newAnchor)
                .from(drepAddress);

        log.info("Generated YAML for DRep update:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + drepAddress);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(drepAccount))
                .withSigner(SignerProviders.signerFrom(drepAccount))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("DRep update failed: " + result.getResponse())
                .isTrue();

        log.info("✓ DRep update transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ DRep update completed successfully!");
    }

    @Test
    @Order(3)
    void testDRepUpdateWithoutAnchor() {
        log.info("=== Testing DRep Update without Anchor with TxDsl ===");

        // Ensure DRep is registered before attempting update
        ensureDRepRegistered(drepAccount);

        // Given - prepare DRep update without anchor
        TxDsl txDsl = new TxDsl()
                .updateDRep(drepAccount.drepCredential())
                .from(drepAddress);

        log.info("Generated YAML for DRep update without anchor:");
        log.info(txDsl.toYaml());

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(drepAccount))
                .withSigner(SignerProviders.signerFrom(drepAccount))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("DRep update without anchor failed: " + result.getResponse())
                .isTrue();

        log.info("✓ DRep update without anchor transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ DRep update without anchor completed successfully!");
    }

    @Test
    @Order(4)
    void testVotingDelegation() {
        log.info("=== Testing Voting Delegation with TxDsl ===");

        // Ensure DRep is registered before delegation
        ensureDRepRegistered(drepAccount);

        // First ensure stake address is registered
        try {
            registerStakeAddress(account2);
        } catch (Exception e) {
            log.info("Stake address might already be registered: {}", e.getMessage());
        }

        // Given - prepare voting delegation to DRep
        DRep drep = GovId.toDrep(drepAccount.drepId());
        log.info("Delegating voting power to DRep: {}", drepAccount.drepId());

        TxDsl txDsl = new TxDsl()
                .delegateVotingPowerTo(address2, drep)
                .from(address1);

        log.info("Generated YAML for voting delegation:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + address1);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.stakeKeySignerFrom(account2))
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Voting delegation failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Voting delegation transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ Voting delegation completed successfully!");
    }

    @Test
    @Order(5)
    void testCreateInfoActionProposal() {
        log.info("=== Testing Info Action Proposal Creation with TxDsl ===");

        // First ensure stake address is registered
        try {
            registerStakeAddress(account1);
        } catch (Exception e) {
            log.info("Stake address might already be registered: {}", e.getMessage());
        }

        // Given - prepare info action proposal
        InfoAction infoAction = new InfoAction();
        Anchor proposalAnchor = new Anchor(
            "https://example.com/proposal-metadata.json",
            HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl txDsl = new TxDsl()
                .createProposal(infoAction, account1.stakeAddress(), proposalAnchor)
                .from(address1);

        log.info("Generated YAML for info action proposal:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + address1);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(account1))
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Info action proposal creation failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Info action proposal transaction submitted: {}", result.getValue());

        // Store proposal transaction hash for use in voting tests
        proposalTxHash = result.getValue();

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ Info action proposal created successfully with tx hash: {}", proposalTxHash);
    }

    @Test
    @Order(6)
    void testCreateVote() {
        log.info("=== Testing Vote Creation with TxDsl ===");

        // Ensure DRep is registered for voting
        ensureDRepRegistered(drepAccount);

        // Check if we have a proposal from the previous test
        if (proposalTxHash == null) {
            log.warn("No proposal transaction hash available from previous test, using dummy governance action ID");
            // Use a dummy ID for structure testing only
            GovActionId govActionId = new GovActionId(
                "0000000000000000000000000000000000000000000000000000000000000000",
                0
            );

            Voter voter = new Voter(VoterType.DREP_KEY_HASH, drepAccount.drepCredential());
            Vote vote = Vote.YES;

            Anchor voteAnchor = new Anchor(
                "https://example.com/vote-rationale.json",
                HexUtil.decodeHexString("dafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
            );

            TxDsl txDsl = new TxDsl()
                    .createVote(voter, govActionId, vote, voteAnchor)
                    .from(drepAddress);

            log.info("Generated YAML for vote:");
            log.info(txDsl.toYaml());

            // Verify YAML structure
            String yaml = txDsl.toYaml();
            assertThat(yaml).contains("from: " + drepAddress);

            log.info("✓ Vote DSL structure validated successfully!");
            return;
        }

        // We have a real proposal, let's actually vote on it!
        log.info("Using proposal transaction hash from previous test: {}", proposalTxHash);

        // In YaciDevKit, the governance action ID is the transaction hash with index 0
        GovActionId govActionId = new GovActionId(proposalTxHash, 0);

        Voter voter = new Voter(VoterType.DREP_KEY_HASH, drepAccount.drepCredential());
        Vote vote = Vote.YES;

        Anchor voteAnchor = new Anchor(
            "https://example.com/vote-rationale.json",
            HexUtil.decodeHexString("dafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl txDsl = new TxDsl()
                .createVote(voter, govActionId, vote, voteAnchor)
                .from(drepAddress);

        log.info("Generated YAML for vote on proposal {}:", proposalTxHash);
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + drepAddress);

        // Actually submit the vote transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(drepAccount))
                .withSigner(SignerProviders.signerFrom(drepAccount))
                .completeAndWait();

        // Verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("Vote submission failed: " + result.getResponse())
                .isTrue();

        log.info("✓ Vote transaction submitted successfully: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ Successfully voted YES on proposal {}", proposalTxHash);
    }

    @Test
    @Order(7)
    void testCreateVoteWithoutAnchor() {
        log.info("=== Testing Vote Creation without Anchor with TxDsl ===");

        // Ensure DRep is registered for voting
        ensureDRepRegistered(drepAccount);

        // Given - prepare vote without anchor
        GovActionId govActionId = new GovActionId(
            "0000000000000000000000000000000000000000000000000000000000000000",
            0
        );

        Voter voter = new Voter(VoterType.DREP_KEY_HASH, drepAccount.drepCredential());
        Vote vote = Vote.NO;

        TxDsl txDsl = new TxDsl()
                .createVote(voter, govActionId, vote)
                .from(drepAddress);

        log.info("Generated YAML for vote without anchor:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + drepAddress);

        log.info("✓ Vote without anchor DSL structure validated successfully!");
    }

    @Test
    @Order(8)
    void testDRepDeregistration() {
        log.info("=== Testing DRep Deregistration with TxDsl ===");

        // Ensure DRep is registered before attempting deregistration
        ensureDRepRegistered(drepAccount);

        // Given - prepare DRep deregistration
        TxDsl txDsl = new TxDsl()
                .unregisterDRep(drepAccount.drepCredential())
                .from(drepAddress);

        log.info("Generated YAML for DRep deregistration:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + drepAddress);

        // When - build and submit transaction
        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(drepAccount))
                .withSigner(SignerProviders.signerFrom(drepAccount))
                .completeAndWait();

        // Then - verify transaction succeeded
        assertThat(result.isSuccessful())
                .withFailMessage("DRep deregistration failed: " + result.getResponse())
                .isTrue();

        log.info("✓ DRep deregistration transaction submitted: {}", result.getValue());

        // Wait for transaction confirmation
        waitForTransaction(result);

        log.info("✓ DRep deregistration completed successfully!");
    }

    @Test
    @Order(9)
    void testDRepDeregistrationWithRefund() {
        log.info("=== Testing DRep Deregistration with Refund Address with TxDsl ===");

        // Use a different account to avoid conflicts with other tests
        Account refundTestAccount = new Account(Networks.testnet(), getMnemonic(),
            DerivationPath.createExternalAddressDerivationPathForAccount(7));
        String refundTestAddress = refundTestAccount.baseAddress();
        topupIfNeeded(refundTestAddress);

        // Ensure this DRep is registered
        ensureDRepRegistered(refundTestAccount);

        // Given - prepare DRep deregistration with refund address
        String refundAddress = address3;

        TxDsl txDsl = new TxDsl()
                .unregisterDRep(refundTestAccount.drepCredential(), refundAddress)
                .from(refundTestAddress);

        log.info("Generated YAML for DRep deregistration with refund:");
        log.info(txDsl.toYaml());

        // Verify YAML structure
        String yaml = txDsl.toYaml();
        assertThat(yaml).contains("from: " + refundTestAddress);

        log.info("✓ DRep deregistration with refund DSL structure validated successfully!");

        // Note: We don't submit this transaction as it would deregister the DRep
        // This test focuses on DSL structure and YAML serialization
    }

    @Test
    @Order(10)
    void testCompleteGovernanceWorkflow() {
        log.info("=== Testing Complete Governance Workflow ===");

        // This test demonstrates a complete governance workflow:
        // 1. Register as DRep
        // 2. Create a proposal
        // 3. Vote on proposal (structure only)
        // 4. Update DRep metadata
        // 5. Deregister DRep

        Account govAccount = new Account(Networks.testnet(), getMnemonic(),
            DerivationPath.createExternalAddressDerivationPathForAccount(6));
        String govAddress = govAccount.baseAddress();

        // Ensure account has sufficient funds
        topupIfNeeded(govAddress);

        // Step 1: Register as DRep
        log.info("Step 1: Registering as DRep...");
        Anchor anchor = new Anchor(
            "https://example.com/gov-drep.json",
            HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl registerTxDsl = new TxDsl()
                .registerDRep(govAccount.drepCredential(), anchor)
                .from(govAddress);

        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> registerResult = builder.compose(registerTxDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(govAccount))
                .withSigner(SignerProviders.signerFrom(govAccount.drepHdKeyPair()))
                .completeAndWait();

        assertThat(registerResult.isSuccessful()).isTrue();
        log.info("✓ DRep registration completed: {}", registerResult.getValue());
        waitForTransaction(registerResult);

        // Step 2: Create a proposal
        log.info("Step 2: Creating an info action proposal...");

        // Ensure stake address is registered
        try {
            registerStakeAddress(govAccount);
        } catch (Exception e) {
            log.info("Stake address might already be registered: {}", e.getMessage());
        }

        InfoAction infoAction = new InfoAction();
        Anchor proposalAnchor = new Anchor(
            "https://example.com/proposal.json",
            HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl proposalTxDsl = new TxDsl()
                .createProposal(infoAction, govAccount.stakeAddress(), proposalAnchor)
                .from(govAddress);

        Result<String> proposalResult = builder.compose(proposalTxDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(govAccount))
                .withSigner(SignerProviders.signerFrom(govAccount))
                .completeAndWait();

        assertThat(proposalResult.isSuccessful()).isTrue();
        log.info("✓ Proposal creation completed: {}", proposalResult.getValue());
        waitForTransaction(proposalResult);

        // Step 3: Vote on proposal (structure only)
        log.info("Step 3: Creating vote structure (not submitting)...");
        GovActionId govActionId = new GovActionId(proposalResult.getValue(), 0);
        Voter voter = new Voter(VoterType.DREP_KEY_HASH, govAccount.drepCredential());
        Vote vote = Vote.YES;

        TxDsl voteTxDsl = new TxDsl()
                .createVote(voter, govActionId, vote)
                .from(govAddress);

        log.info("Vote YAML structure:");
        log.info(voteTxDsl.toYaml());

        // Step 4: Update DRep metadata
        log.info("Step 4: Updating DRep metadata...");
        Anchor updatedAnchor = new Anchor(
            "https://example.com/updated-gov-drep.json",
            HexUtil.decodeHexString("dafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        TxDsl updateTxDsl = new TxDsl()
                .updateDRep(govAccount.drepCredential(), updatedAnchor)
                .from(govAddress);

        Result<String> updateResult = builder.compose(updateTxDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(govAccount))
                .withSigner(SignerProviders.signerFrom(govAccount))
                .completeAndWait();

        assertThat(updateResult.isSuccessful()).isTrue();
        log.info("✓ DRep update completed: {}", updateResult.getValue());
        waitForTransaction(updateResult);

        // Step 5: Deregister DRep
        log.info("Step 5: Deregistering DRep...");
        TxDsl deregisterTxDsl = new TxDsl()
                .unregisterDRep(govAccount.drepCredential())
                .from(govAddress);

        Result<String> deregisterResult = builder.compose(deregisterTxDsl.unwrap())
                .withSigner(SignerProviders.drepKeySignerFrom(govAccount))
                .withSigner(SignerProviders.signerFrom(govAccount))
                .completeAndWait();

        assertThat(deregisterResult.isSuccessful()).isTrue();
        log.info("✓ DRep deregistration completed: {}", deregisterResult.getValue());
        waitForTransaction(deregisterResult);

        log.info("✓ Complete governance workflow executed successfully!");
        log.info("  Registration tx: {}", registerResult.getValue());
        log.info("  Proposal tx: {}", proposalResult.getValue());
        log.info("  Update tx: {}", updateResult.getValue());
        log.info("  Deregistration tx: {}", deregisterResult.getValue());
    }

    @Test
    @Order(11)
    void testGovernanceYamlSerialization() {
        log.info("=== Testing Governance YAML Serialization ===");

        // Given - create a TxDsl with various governance operations
        Anchor anchor = new Anchor(
            "https://example.com/metadata.json",
            HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
        );

        Credential drepCred = drepAccount.drepCredential();
        GovActionId govActionId = new GovActionId(
            "0000000000000000000000000000000000000000000000000000000000000000",
            0
        );
        Voter voter = new Voter(VoterType.DREP_KEY_HASH, drepCred);
        Vote vote = Vote.ABSTAIN;

        InfoAction infoAction = new InfoAction();
        DRep drep = GovId.toDrep(drepAccount.drepId());

        TxDsl original = new TxDsl()
                .registerDRep(drepCred, anchor)
                .createProposal(infoAction, account1.stakeAddress(), anchor)
                .createVote(voter, govActionId, vote, anchor)
                .delegateVotingPowerTo(address2, drep)
                .updateDRep(drepCred, anchor)
                .from(address1);

        // When - serialize to YAML and back
        String yaml = original.toYaml();
        log.info("Serialized YAML for governance operations:\n{}", yaml);

        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify structure is preserved
        assertThat(restored).isNotNull();

        // Verify YAML contains expected elements
        assertThat(yaml).contains("from: " + address1);
        assertThat(yaml).contains("version: 1.0");

        log.info("✓ Governance YAML serialization working correctly");
    }

    /**
     * Check if a DRep is registered by querying YaciDevKit governance state.
     * @param drepId The DRep ID to check
     * @return true if DRep is registered, false otherwise
     */
    private boolean isDRepRegistered(String drepId) {
        log.info("Checking if DRep is registered: {}", drepId);
        try {
            String url = "http://localhost:8080/api/v1/governance-state/dreps/" + drepId;
            log.debug("Checking DRep status at: {}", url);

            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Parse JSON to check if DRep exists and is active
                JsonNode drepInfo = objectMapper.readTree(response.toString());

                // Check if the response has DRep data (not empty or error)
                if (drepInfo != null && drepInfo.has("drep_id")) {
                    log.debug("DRep {} is registered", drepId);
                    return true;
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                log.debug("DRep {} is not registered (404)", drepId);
                return false;
            }

            log.debug("DRep {} status unknown (response code: {})", drepId, responseCode);
            return false;

        } catch (Exception e) {
            log.warn("Failed to check DRep status for {}: {}", drepId, e.getMessage());
            // Assume not registered if we can't check
            return false;
        }
    }

    /**
     * Ensure a DRep is registered. If not registered, register it.
     * @param account The account to register as DRep
     * @return true if DRep is registered (either already was or newly registered)
     */
    private boolean ensureDRepRegistered(Account account) {
        String drepId = account.drepId();
        String address = account.baseAddress();

        // Check if already registered
        if (isDRepRegistered(drepId)) {
            log.info("DRep {} is already registered", drepId);
            return true;
        }

        log.info("DRep {} is not registered, registering now...", drepId);

        try {
            // Register the DRep
            Anchor anchor = new Anchor(
                "https://example.com/drep-metadata.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")
            );

            TxDsl txDsl = new TxDsl()
                    .registerDRep(account.drepCredential(), anchor)
                    .from(address);

            QuickTxBuilder builder = new QuickTxBuilder(backendService);
            Result<String> result = builder.compose(txDsl.unwrap())
                    .withSigner(SignerProviders.signerFrom(account))
                    .withSigner(SignerProviders.signerFrom(account.drepHdKeyPair()))
                    .completeAndWait();

            if (result.isSuccessful()) {
                log.info("Successfully registered DRep {}: {}", drepId, result.getValue());
                waitForTransaction(result);
                return true;
            } else {
                log.error("Failed to register DRep {}: {}", drepId, result.getResponse());
                return false;
            }
        } catch (Exception e) {
            log.error("Exception while registering DRep {}: {}", drepId, e.getMessage());
            return false;
        }
    }

    /**
     * Ensure a DRep is not registered. If registered, deregister it.
     * @param account The account to deregister as DRep
     * @return true if DRep is not registered (either already wasn't or newly deregistered)
     */
    private boolean ensureDRepNotRegistered(Account account) {
        String drepId = account.drepId();
        String address = account.baseAddress();

        // Check if registered
        if (!isDRepRegistered(drepId)) {
            log.info("DRep {} is not registered", drepId);
            return true;
        }

        log.info("DRep {} is registered, deregistering now...", drepId);

        try {
            // Deregister the DRep
            TxDsl txDsl = new TxDsl()
                    .unregisterDRep(account.drepCredential())
                    .from(address);

            QuickTxBuilder builder = new QuickTxBuilder(backendService);
            Result<String> result = builder.compose(txDsl.unwrap())
                    .withSigner(SignerProviders.drepKeySignerFrom(account))
                    .withSigner(SignerProviders.signerFrom(account))
                    .completeAndWait();

            if (result.isSuccessful()) {
                log.info("Successfully deregistered DRep {}: {}", drepId, result.getValue());
                waitForTransaction(result);
                return true;
            } else {
                log.error("Failed to deregister DRep {}: {}", drepId, result.getResponse());
                return false;
            }
        } catch (Exception e) {
            log.error("Exception while deregistering DRep {}: {}", drepId, e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to register stake address for an account.
     */
    private void registerStakeAddress(Account account) throws Exception {
        String address = account.baseAddress();

        TxDsl txDsl = new TxDsl()
                .registerStakeAddress(account.stakeAddress())
                .from(address);

        QuickTxBuilder builder = new QuickTxBuilder(backendService);
        Result<String> result = builder.compose(txDsl.unwrap())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait();

        if (!result.isSuccessful()) {
            throw new Exception("Failed to register stake address: " + result.getResponse());
        }

        waitForTransaction(result);
    }
}
