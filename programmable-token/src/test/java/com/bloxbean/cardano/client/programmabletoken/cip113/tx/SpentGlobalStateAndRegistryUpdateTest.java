package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113RegistryUpdate;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two shapes the materializer used to get wrong, built offline through QuickTx with stand-in
 * scripts. Nothing is evaluated; the evaluator records what it is handed.
 *
 * <ul>
 *   <li>A registry-node update was evaluated before it held any fee input: it is the one verb
 *       that never funded itself, so a validator saw a transaction spending nothing but the node.</li>
 *   <li>A mint whose transaction SPENDS the token's global-state UTxO (a substandard enforcing a
 *       supply cap does) also got that UTxO as a reference input, which the ledger rejects
 *       ({@code BabbageNonDisjointRefInputs}).</li>
 * </ul>
 */
class SpentGlobalStateAndRegistryUpdateTest {

    private static final PlutusScript BASE = script("01");
    private static final PlutusScript TRANSFER = script("02");
    private static final PlutusScript LOGIC_A = script("03");
    private static final PlutusScript THIRD_PARTY = script("04");
    private static final PlutusScript DISPATCHER = script("05");
    private static final PlutusScript ISSUANCE_LOGIC = script("06");
    private static final PlutusScript REGISTRY = script("07");
    private static final PlutusScript GLOBAL_STATE_SPEND = script("08");

    private static final IssuanceCborHex TEMPLATE = new IssuanceCborHex(
            HexUtil.decodeHexString("58aa"), HexUtil.decodeHexString("bbcc"));
    private static final String POLICY_A = hash(PolicyIdDerivation.issuanceScript(
            TEMPLATE, Credential.fromScript(hash(LOGIC_A))));
    private static final String TOK = HexUtil.encodeHexString("Tok".getBytes(StandardCharsets.UTF_8));

    private static final Cip113Deployment DEPLOYMENT = Cip113Deployment.builder()
            .network(Networks.testnet())
            .paramsPolicy("99".repeat(28))
            .programmableLogicBaseHash(hash(BASE))
            .programmableLogicGlobalHash(hash(DISPATCHER))
            .issuanceLogicHash(hash(ISSUANCE_LOGIC))
            .transferScriptHash(hash(TRANSFER))
            .thirdPartyScriptHash(hash(THIRD_PARTY))
            .unfrackingScriptHash("66".repeat(28))
            .registryNodeCs(hash(REGISTRY))
            .issuanceCborHexCs("44".repeat(28))
            .maxInlineDatumBytes(1024)
            .build();

    private static final Address ADMIN = AddressProvider.getEntAddress(
            Credential.fromKey("44".repeat(28)), Networks.testnet());
    private static final Address RECEIVER = AddressProvider.getBaseAddress(
            Credential.fromKey("33".repeat(28)), Credential.fromKey("88".repeat(28)), Networks.testnet());

    private static final Utxo FEE_UTXO = Utxo.builder().txHash("ee".repeat(32)).outputIndex(0)
            .address(ADMIN.toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(500_000_000L)))).build();
    private static final Utxo PARAMS = Utxo.builder().txHash("cc".repeat(32)).outputIndex(0)
            .address(DEPLOYMENT.protocolParamsAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)))).build();
    private static final Utxo NODE_A = Utxo.builder().txHash("0a".repeat(32)).outputIndex(3)
            .address(DEPLOYMENT.registryAddress().toBech32())
            // Above the continuation's min-ADA, as a node created on chain is: the update then copies
            // the node's value exactly and the change before balancing is 0, not negative.
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)),
                    Amount.asset(hash(REGISTRY) + POLICY_A, 1))).build();

    private static final String GS_POLICY = "ab".repeat(28);
    private static final Utxo GLOBAL_STATE = Utxo.builder().txHash("9f".repeat(32)).outputIndex(4)
            .address(AddressProvider.getEntAddress(GLOBAL_STATE_SPEND, Networks.testnet()).toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(3_000_000L)), Amount.asset(GS_POLICY + "00", 1)))
            .inlineDatum(ConstrPlutusData.of(0).serializeToHex())
            .build();

    // ------------------------------------------------------------ registry update

    /** Every verb funds itself before evaluation, so the transaction a validator sees can pay its fee. */
    @Test
    void aRegistryUpdateIsEvaluatedWithItsFeeInput() {
        RegistryNode updated = nodeA().toBuilder()
                .thirdPartyTransferLogicScript(Credential.fromScript(hash(TRANSFER)))
                .build();
        var tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .updateRegistry(POLICY_A, Cip113RegistryUpdate.from(updated), BigIntPlutusData.of(0));

        List<Transaction> evaluated = new ArrayList<>();
        build(tx, evaluated);

        assertThat(evaluated).as("the update has a script input, so it is evaluated").isNotEmpty();
        assertThat(evaluated.get(0).getBody().getInputs())
                .as("the first evaluation already spends the fee payer's ADA, as for every other verb")
                .extracting(TransactionInput::getTransactionId)
                .contains(FEE_UTXO.getTxHash());
    }

    // ------------------------------------------------------- spent global state

    /** The caller spends global state (and continues it); CCL takes that as the global state and does not reference it too. */
    @Test
    void aMintThatSpendsItsGlobalStateDoesNotAlsoReferenceIt() {
        var tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(new Asset("0x" + TOK, BigInteger.TEN)),
                        BigIntPlutusData.of(0), null);
        tx.collectFrom(GLOBAL_STATE, BigIntPlutusData.of(1));
        tx.payToContract(GLOBAL_STATE.getAddress(), GLOBAL_STATE.getAmount(), ConstrPlutusData.of(0));
        tx.attachSpendingValidator(GLOBAL_STATE_SPEND);

        Transaction txn = build(tx, new ArrayList<>());

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                .as("the caller's global-state spend is kept").contains(GLOBAL_STATE.getTxHash());
        assertThat(Optional.ofNullable(txn.getBody().getReferenceInputs()).orElse(List.of()))
                .extracting(TransactionInput::getTransactionId)
                .as("a UTxO the transaction spends must not also be a reference input")
                .doesNotContain(GLOBAL_STATE.getTxHash());
    }

    /**
     * The same transaction saved as a TxPlan and reloaded. YAML keeps a spent UTxO only as an outref
     * ({@code utxo_refs}), so the spend must be recognised from the reference alone.
     */
    @Test
    void aMintThatSpendsItsGlobalStateStillDoesNotReferenceItAfterAYamlRoundTrip() {
        Transaction txn = buildReloaded(mintSpendingGlobalState(), true);

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                .as("the reloaded global-state spend is kept").contains(GLOBAL_STATE.getTxHash());
        assertThat(Optional.ofNullable(txn.getBody().getReferenceInputs()).orElse(List.of()))
                .extracting(TransactionInput::getTransactionId)
                .as("a UTxO the reloaded transaction spends must not also be a reference input")
                .doesNotContain(GLOBAL_STATE.getTxHash());
    }

    /** A reloaded spend the backend cannot resolve is refused by name, not silently ignored. */
    @Test
    void aReloadedSpendOfAnUnknownUtxoIsRefusedByItsOutref() {
        String outref = GLOBAL_STATE.getTxHash() + "#" + GLOBAL_STATE.getOutputIndex();

        assertThatThrownBy(() -> buildReloaded(mintSpendingGlobalState(), false))
                .satisfies(e -> assertThat(causeChain(e)).contains(outref));
    }

    // ------------------------------------------------------------------ helpers

    private static ProgrammableTokenTx mintSpendingGlobalState() {
        var tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(new Asset("0x" + TOK, BigInteger.TEN)),
                        BigIntPlutusData.of(0), null);
        tx.collectFrom(GLOBAL_STATE, BigIntPlutusData.of(1));
        tx.payToContract(GLOBAL_STATE.getAddress(), GLOBAL_STATE.getAmount(), ConstrPlutusData.of(0));
        tx.attachSpendingValidator(GLOBAL_STATE_SPEND);
        return tx;
    }

    /** {@code tx} saved as TxPlan YAML, reloaded, and built; the reload carries the spend as an outref only. */
    private static Transaction buildReloaded(ProgrammableTokenTx tx, boolean globalStateOnChain) {
        return build(new ArrayList<>(), globalStateOnChain, (builder, extension) -> {
            TxPlanCodec codec = TxPlanCodec.builder()
                    .withExtension(ProgrammableTokenExtension.DEFAULT_NAMESPACE, extension).build();
            String yaml = codec.toYaml(extension.configure(TxPlan.from(tx)));
            TxPlan restored = codec.fromYaml(yaml);
            assertThat(restored.getTxs().get(0).getIntentions())
                    .filteredOn(ScriptCollectFromIntent.class::isInstance)
                    .map(ScriptCollectFromIntent.class::cast)
                    .singleElement()
                    .satisfies(collect -> {
                        assertThat(collect.getUtxos()).as("precondition: YAML drops the UTxO bodies").isNullOrEmpty();
                        assertThat(collect.getUtxoRefs()).hasSize(1);
                    });
            return builder.compose(restored);
        });
    }

    private static String causeChain(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) messages.append(t.getMessage()).append(" | ");
        return messages.toString();
    }

    private static RegistryNode nodeA() {
        return RegistryNode.builder().key(POLICY_A).next("ff".repeat(28))
                .mintingLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .transferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .thirdPartyTransferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .unfrackingLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .globalStateCs(GS_POLICY).build();
    }

    private static Transaction build(ProgrammableTokenTx tx, List<Transaction> evaluated) {
        return build(evaluated, true, (builder, extension) -> builder.compose(tx));
    }

    private static Transaction build(List<Transaction> evaluated, boolean globalStateOnChain,
                                     BiFunction<QuickTxBuilder, ProgrammableTokenExtension, QuickTxBuilder.TxContext> compose) {
        ProtocolParams protocolParams = protocolParams();
        ProtocolParamsSupplier protocolParamsSupplier = () -> protocolParams;
        Map<String, List<Utxo>> chain = new LinkedHashMap<>();
        chain.put(ADMIN.toBech32(), List.of(FEE_UTXO));
        chain.put(PARAMS.getAddress(), List.of(PARAMS));
        chain.put(NODE_A.getAddress(), List.of(NODE_A));
        if (globalStateOnChain) chain.put(GLOBAL_STATE.getAddress(), List.of(GLOBAL_STATE));
        UtxoSupplier utxoSupplier = new InMemoryUtxoSupplier(chain);

        Cip113ProtocolService service = service(protocolParamsSupplier);
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(new Cip113Protocol(service)).deployment(Map.of()).build();
        TransactionEvaluator evaluator = (cbor, inputUtxos) -> {
            Transaction candidate;
            try {
                candidate = Transaction.deserialize(cbor);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            evaluated.add(candidate);
            return new StaticTransactionEvaluator(Collections.nCopies(candidate.getWitnessSet().getRedeemers().size(),
                    ExUnits.builder().mem(BigInteger.valueOf(100_000)).steps(BigInteger.valueOf(10_000_000)).build()))
                    .evaluateTx(cbor, inputUtxos);
        };
        QuickTxBuilder builder = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension);
        return compose.apply(builder, extension)
                .feePayer(ADMIN.toBech32())
                .collateralPayer(ADMIN.toBech32())
                .withScriptSupplier(service.scripts())
                .withTxEvaluator(evaluator)
                .build();
    }

    private static Cip113ProtocolService service(ProtocolParamsSupplier protocolParamsSupplier) {
        RegistryLookup.RegistryNodeUtxo entry = new RegistryLookup.RegistryNodeUtxo(NODE_A, nodeA());
        RegistryLookup registry = new RegistryLookup() {
            @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
                return POLICY_A.equalsIgnoreCase(policyId) ? Optional.of(entry) : Optional.empty();
            }

            @Override public RegistryNodeUtxo coveringNode(String policyId) {
                throw new AssertionError("no unregistered policy here: " + policyId);
            }

            @Override public List<RegistryNodeUtxo> all() {
                return List.of(entry);
            }
        };
        DeploymentScripts scripts = new DeploymentScripts((ScriptSupplier) null, DEPLOYMENT)
                .registerAll(List.of(BASE, TRANSFER, LOGIC_A, THIRD_PARTY, DISPATCHER, ISSUANCE_LOGIC, REGISTRY,
                        GLOBAL_STATE_SPEND));
        Utxo template = Utxo.builder().txHash("dd".repeat(32)).outputIndex(0)
                .address("addr_test1template")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .inlineDatum(ConstrPlutusData.of(0,
                        BytesPlutusData.of(TEMPLATE.getPrefixCborHex()),
                        BytesPlutusData.of(TEMPLATE.getPostfixCborHex())).serializeToHex())
                .build();

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(DEPLOYMENT);
        when(service.registryLookup(any())).thenReturn(registry);
        when(service.scripts()).thenReturn(scripts);
        when(service.coordinationUtxo(any())).thenReturn(PARAMS);
        when(service.issuanceTemplateUtxo()).thenReturn(template);
        when(service.protocolParamsSupplier()).thenReturn(protocolParamsSupplier);
        when(service.globalStateUtxo(anyString(), any())).thenReturn(null);
        return service;
    }

    private static ProtocolParams protocolParams() {
        try (InputStream in = SpentGlobalStateAndRegistryUpdateTest.class.getResourceAsStream("/cip113/protocol-params.json")) {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, ProtocolParams.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static PlutusScript script(String lastByte) {
        return PlutusBlueprintUtil.getPlutusScriptFromCompiledCode("4501010024" + lastByte, PlutusVersion.v3);
    }

    private static String hash(PlutusScript script) {
        try {
            return script.getPolicyId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class InMemoryUtxoSupplier implements UtxoSupplier {
        private final Map<String, List<Utxo>> byAddress;

        InMemoryUtxoSupplier(Map<String, List<Utxo>> byAddress) {
            this.byAddress = byAddress;
        }

        @Override
        public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
            return page != null && page == 0 ? byAddress.getOrDefault(address, List.of()) : List.of();
        }

        @Override
        public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
            return byAddress.values().stream().flatMap(List::stream)
                    .filter(u -> u.getTxHash().equals(txHash) && u.getOutputIndex() == outputIndex)
                    .findFirst();
        }
    }
}
