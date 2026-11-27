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
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletKey;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.IssuanceCborHex;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A token whose registry node declares global state can be built when the caller reads that
 * global-state UTxO, without the backend's policy → holders asset index.
 *
 * <p>An application that keeps tokens at {@code (base script, stake credential)} could previously
 * mint to such a wallet (receivers pass through) but never spend from it: the sender's own wallet
 * was always derived from the payment key. Built offline through QuickTx, nothing evaluated.</p>
 */
class CallerGlobalStateTest {

    private static final PlutusScript BASE = script("01");
    private static final PlutusScript TRANSFER = script("02");
    private static final PlutusScript LOGIC_A = script("03");
    private static final PlutusScript THIRD_PARTY = script("04");
    private static final PlutusScript DISPATCHER = script("05");
    private static final PlutusScript ISSUANCE_LOGIC = script("06");
    private static final IssuanceCborHex TEMPLATE = new IssuanceCborHex(
            HexUtil.decodeHexString("58aa"), HexUtil.decodeHexString("bbcc"));
    private static final String POLICY_A = hash(PolicyIdDerivation.issuanceScript(
            TEMPLATE, Credential.fromScript(hash(LOGIC_A))));
    private static final String TOK = HexUtil.encodeHexString("Tok".getBytes(StandardCharsets.UTF_8));
    private static final String UNIT_A = POLICY_A + TOK;

    private static final Cip113Deployment PAYMENT_KEYED = Cip113Deployment.builder()
            .network(Networks.testnet())
            .paramsPolicy("99".repeat(28))
            .programmableLogicBaseHash(hash(BASE))
            .programmableLogicGlobalHash(hash(DISPATCHER))
            .issuanceLogicHash(hash(ISSUANCE_LOGIC))
            .transferScriptHash(hash(TRANSFER))
            .thirdPartyScriptHash(hash(THIRD_PARTY))
            .unfrackingScriptHash("66".repeat(28))
            .registryNodeCs("59fd9f91c09ab82bbc40b58e62c455eab8d3105ca3d2f46948af7b0c")
            .issuanceCborHexCs("44".repeat(28))
            .maxInlineDatumBytes(1024)
            .build();
    private static final Cip113Deployment STAKE_KEYED = PAYMENT_KEYED.toBuilder()
            .smartWalletKey(SmartWalletKey.STAKE).build();

    private static final String OWNER_PAYMENT = "22".repeat(28);
    private static final String OWNER_STAKE = "77".repeat(28);
    private static final Address OWNER = AddressProvider.getBaseAddress(
            Credential.fromKey(OWNER_PAYMENT), Credential.fromKey(OWNER_STAKE), Networks.testnet());
    private static final Address RECEIVER = AddressProvider.getBaseAddress(
            Credential.fromKey("33".repeat(28)), Credential.fromKey("88".repeat(28)), Networks.testnet());
    private static final Address ENTERPRISE_ISSUER = AddressProvider.getEntAddress(
            Credential.fromKey("44".repeat(28)), Networks.testnet());

    private static final Utxo PARAMS = Utxo.builder().txHash("cc".repeat(32)).outputIndex(0)
            .address(PAYMENT_KEYED.protocolParamsAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)))).build();
    private static final Utxo NODE_A = Utxo.builder().txHash("0a".repeat(32)).outputIndex(3)
            .address(PAYMENT_KEYED.registryAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)))).build();

    private static final String GS_POLICY = "ab".repeat(28);
    private static final Utxo GLOBAL_STATE = Utxo.builder().txHash("9f".repeat(32)).outputIndex(4)
            .address("addr_test1globalstate")
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(3_000_000L)), Amount.asset(GS_POLICY + "00", 1)))
            .build();

    /**
     * The backend's asset-index lookup finds nothing (the service mock answers null, as Yaci Store,
     * Koios and Ogmios effectively do), so without the caller's reference input the build fails.
     */
    @Test
    void aTokenWithGlobalStateNeedsItsUtxo() {
        assertThatThrownBy(() -> build(PAYMENT_KEYED, mint(), OWNER))
                .hasStackTraceContaining("declares global state under policy " + GS_POLICY);
    }

    /** A global-state UTxO the caller reads is used: exactly that UTxO becomes the reference input. */
    @Test
    void aGlobalStateUtxoTheCallerReadsIsUsed() {
        var tx = mint();
        tx.readFrom(GLOBAL_STATE);
        Transaction txn = build(PAYMENT_KEYED, tx, OWNER, GLOBAL_STATE);
        assertThat(txn.getBody().getReferenceInputs()).extracting(TransactionInput::getTransactionId)
                .contains(GLOBAL_STATE.getTxHash());
    }

    /** A caller reference input without the token does not satisfy the lookup. */
    @Test
    void anUnrelatedCallerReferenceIsNotTakenForGlobalState() {
        Utxo unrelated = Utxo.builder().txHash("8e".repeat(32)).outputIndex(4).address("addr_test1globalstate")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(3_000_000L)))).build();
        var tx = mint();
        tx.readFrom(unrelated);
        assertThatThrownBy(() -> build(PAYMENT_KEYED, tx, OWNER, unrelated))
                .hasStackTraceContaining("declares global state under policy " + GS_POLICY);
    }

    private static ProgrammableTokenTx mint() {
        return new ProgrammableTokenTx().from(OWNER.toBech32())
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(new Asset("0x" + TOK, BigInteger.TEN)),
                        BigIntPlutusData.of(0), null);
    }

    // ------------------------------------------------------------- helpers

    private static Transaction build(Cip113Deployment deployment, ProgrammableTokenTx tx, Utxo held) {
        return build(deployment, tx, OWNER, held);
    }

    private static Transaction build(Cip113Deployment deployment, ProgrammableTokenTx tx, Address payer, Utxo... smartWallet) {
        ProtocolParams protocolParams = protocolParams();
        ProtocolParamsSupplier protocolParamsSupplier = () -> protocolParams;
        Map<String, List<Utxo>> chain = new LinkedHashMap<>();
        chain.put(payer.toBech32(), List.of(Utxo.builder().txHash("ee".repeat(32)).outputIndex(0)
                .address(payer.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(500_000_000L)))).build()));
        for (Utxo utxo : smartWallet) {
            List<Utxo> at = new ArrayList<>(chain.getOrDefault(utxo.getAddress(), List.of()));
            at.add(utxo);
            chain.put(utxo.getAddress(), at);
        }
        chain.put(PARAMS.getAddress(), List.of(PARAMS));
        chain.put(NODE_A.getAddress(), List.of(NODE_A));
        UtxoSupplier utxoSupplier = new InMemoryUtxoSupplier(chain);

        Cip113ProtocolService service = service(deployment, protocolParamsSupplier);
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(new Cip113Protocol(service)).deployment(Map.of()).build();
        TransactionEvaluator evaluator = (cbor, inputUtxos) -> {
            int redeemers;
            try {
                redeemers = Transaction.deserialize(cbor).getWitnessSet().getRedeemers().size();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return new StaticTransactionEvaluator(Collections.nCopies(redeemers, ExUnits.builder()
                    .mem(BigInteger.valueOf(100_000)).steps(BigInteger.valueOf(10_000_000)).build()))
                    .evaluateTx(cbor, inputUtxos);
        };
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension)
                .compose(tx)
                .feePayer(payer.toBech32())
                .collateralPayer(payer.toBech32())
                .withScriptSupplier(service.scripts())
                .withTxEvaluator(evaluator)
                .build();
    }

    private static Cip113ProtocolService service(Cip113Deployment deployment, ProtocolParamsSupplier protocolParamsSupplier) {
        RegistryNode nodeA = RegistryNode.builder().key(POLICY_A).next("ff".repeat(28))
                .mintingLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .transferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .thirdPartyTransferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .unfrackingLogicScript(Credential.fromKey(""))
                .globalStateCs(GS_POLICY).build();
        RegistryLookup.RegistryNodeUtxo entry = new RegistryLookup.RegistryNodeUtxo(NODE_A, nodeA);
        RegistryLookup registry = new RegistryLookup() {
            @Override public Optional<RegistryNodeUtxo> byPolicy(String policyId) {
                return POLICY_A.equalsIgnoreCase(policyId) ? Optional.of(entry) : Optional.empty();
            }

            @Override public RegistryNodeUtxo coveringNode(String policyId) {
                throw new AssertionError("no unregistered policy in these wallets: " + policyId);
            }

            @Override public List<RegistryNodeUtxo> all() {
                return List.of(entry);
            }
        };
        DeploymentScripts scripts = new DeploymentScripts((ScriptSupplier) null, deployment)
                .registerAll(List.of(BASE, TRANSFER, LOGIC_A, THIRD_PARTY, DISPATCHER, ISSUANCE_LOGIC));
        Utxo template = Utxo.builder().txHash("dd".repeat(32)).outputIndex(0)
                .address("addr_test1template")
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L))))
                .inlineDatum(ConstrPlutusData.of(0,
                        BytesPlutusData.of(TEMPLATE.getPrefixCborHex()),
                        BytesPlutusData.of(TEMPLATE.getPostfixCborHex())).serializeToHex())
                .build();

        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        when(service.deployment()).thenReturn(deployment);
        when(service.registryLookup(any())).thenReturn(registry);
        when(service.scripts()).thenReturn(scripts);
        when(service.coordinationUtxo(any())).thenReturn(PARAMS);
        when(service.issuanceTemplateUtxo()).thenReturn(template);
        when(service.protocolParamsSupplier()).thenReturn(protocolParamsSupplier);
        when(service.globalStateUtxo(anyString(), any())).thenReturn(null);
        return service;
    }

    private static ProtocolParams protocolParams() {
        try (InputStream in = CallerGlobalStateTest.class.getResourceAsStream("/cip113/protocol-params.json")) {
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
