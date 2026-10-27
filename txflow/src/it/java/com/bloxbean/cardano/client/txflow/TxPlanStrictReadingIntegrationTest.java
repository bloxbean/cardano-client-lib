package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks on Yaci DevKit that strict TxPlan reading does not get in the way of TxFlow: plans built in Java are
 * copied as before, including a variable whose value is a template and a payment address taken from the plan's own
 * variables.
 * <p>
 * Run with Yaci DevKit on :8080 (admin :10000):
 * {@code ./gradlew :txflow:integrationTest --tests TxPlanStrictReadingIntegrationTest}
 */
public class TxPlanStrictReadingIntegrationTest {

    private static final String YACI_BASE_URL = "http://localhost:8080/api/v1/";
    private static final String DEFAULT_MNEMONIC = "test test test test test test test test test test test test test test test test test test test test test test test sauce";

    private BFBackendService backendService;
    private DefaultSignerRegistry signerRegistry;
    private Account sender;   // index 310, apart from the other TxFlow integration tests
    private Account receiver; // index 311

    @BeforeEach
    void setUp() throws Exception {
        backendService = new BFBackendService(YACI_BASE_URL, "dummy-project-id");
        sender = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 310);
        receiver = new Account(Networks.testnet(), DEFAULT_MNEMONIC, 311);
        assertTrue(YaciDevKitUtil.topup(sender.baseAddress(), 1000), "Failed to topup sender");
        Thread.sleep(2000);
        signerRegistry = new DefaultSignerRegistry().addAccount("account://sender", sender);
    }

    @Test
    void javaBuiltPlanWithATemplateValuedVariableRuns() {
        TxPlan plan = TxPlan.from(new Tx()
                        .payToAddress(receiver.baseAddress(), Amount.ada(2))
                        .fromRef("account://sender"))
                .withSigner("account://sender")
                .addVariable("receiver", receiver.baseAddress())
                .addVariable("note", "${receiver}");
        // A document with this variable is rejected; TxFlow copies the in-memory plan without that authoring rule.
        RuntimeException rejected = assertThrows(RuntimeException.class, () -> TxPlan.from(plan.toYaml()));
        assertTrue(rejected.getMessage().contains("literal"), rejected.getMessage());

        TxFlow flow = TxFlow.builder("java-built-template-variable")
                .addStep(FlowStep.builder("payment").withTxPlan(plan).build())
                .build();
        FlowResult result = executor().executeSync(flow);

        assertTrue(result.isSuccessful(), () -> "flow failed: "
                + (result.getError() != null ? result.getError().getMessage() : "no error"));
        assertEquals(1, result.getTransactionHashes().size());
    }

    @Test
    void javaBuiltPlanResolvesItsOwnVariablesWhenCopied() throws Exception {
        // TxFlow copies the plan as TxPlan.from did before strict reading, so its own ${...} values still resolve.
        TxPlan plan = TxPlan.from(new Tx()
                        .payToAddress("${beneficiary}", Amount.ada(3))
                        .fromRef("account://sender"))
                .withSigner("account://sender")
                .addVariable("beneficiary", receiver.baseAddress());

        FlowResult result = executor().executeSync(TxFlow.builder("java-built-own-variable")
                .addStep(FlowStep.builder("payment").withTxPlan(plan).build())
                .build());

        assertTrue(result.isSuccessful(), () -> "flow failed: "
                + (result.getError() != null ? result.getError().getMessage() : "no error"));
        String txHash = result.getTransactionHashes().get(0);
        assertTrue(backendService.getTransactionService().getTransactionUtxos(txHash).getValue().getOutputs().stream()
                .anyMatch(output -> output.getAddress().equals(receiver.baseAddress())), "receiver was paid");
    }

    private FlowExecutor executor() {
        return FlowExecutor.create(backendService)
                .withSignerRegistry(signerRegistry)
                .withConfirmationConfig(ConfirmationConfig.builder().timeout(Duration.ofSeconds(120)).build());
    }
}
