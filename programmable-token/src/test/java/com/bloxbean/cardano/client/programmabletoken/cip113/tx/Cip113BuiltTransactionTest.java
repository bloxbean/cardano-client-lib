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
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.programmabletoken.BurnAuthorization;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenExtension;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenTx;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployment;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Protocol;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.cip113.LedgerOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyIdDerivation;
import com.bloxbean.cardano.client.programmabletoken.cip113.PolicyOrdering;
import com.bloxbean.cardano.client.programmabletoken.cip113.SmartWalletAddress;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Redeemers;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The v0.0.1 redeemers as they land in a finished transaction: built offline through QuickTx with
 * the extension wired in, then read back against the ledger orderings the validators index.
 *
 * <p>The scripts are stand-ins registered by hash and nothing is evaluated, so what this pins is
 * the wiring the validators depend on: which withdrawal every base-script input's
 * {@code wdrl_idx} names, that the dispatcher's arm matches the delegate present, that delegate
 * redeemers carry no {@code params_idx}, and — the custody rule — that a burn's issuance_logic
 * proof names the very registry node the transfer delegate's proof names, so the delegate covers
 * the burn instead of issuance_logic's stricter {@code no_escape} fallback.</p>
 */
class Cip113BuiltTransactionTest {

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

    private static final Cip113Deployment DEPLOYMENT = Cip113Deployment.builder()
            .network(Networks.testnet())
            .paramsPolicy("99".repeat(28))
            .programmableLogicBaseHash(hash(BASE))
            .programmableLogicGlobalHash(hash(DISPATCHER))
            .issuanceLogicHash(hash(ISSUANCE_LOGIC))
            .transferScriptHash(hash(TRANSFER))
            .thirdPartyScriptHash(hash(THIRD_PARTY))
            // Ordinary builds must not need an unfracking hash, even if the chain enables it.
            .unfrackingEnabled(true)
            .registryNodeCs("59fd9f91c09ab82bbc40b58e62c455eab8d3105ca3d2f46948af7b0c")
            .issuanceCborHexCs("44".repeat(28))
            .maxInlineDatumBytes(1024)
            .build();

    private static final Address OWNER = AddressProvider.getEntAddress(
            Credential.fromKey("22".repeat(28)), Networks.testnet());
    private static final Address HOLDER = AddressProvider.getEntAddress(
            Credential.fromKey("33".repeat(28)), Networks.testnet());
    private static final Address ADMIN = AddressProvider.getEntAddress(
            Credential.fromKey("44".repeat(28)), Networks.testnet());

    private static final Utxo PARAMS = Utxo.builder().txHash("cc".repeat(32)).outputIndex(0)
            .address(DEPLOYMENT.protocolParamsAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(5_000_000L)))).build();
    private static final Utxo NODE_A = Utxo.builder().txHash("0a".repeat(32)).outputIndex(3)
            .address(DEPLOYMENT.registryAddress().toBech32())
            .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)))).build();

    // ------------------------------------------------------------------ burn

    @Test
    void aBurnsIssuanceProofNamesTheNodeTheTransferProofNames() {
        String ownerWallet = SmartWalletAddress.ofPaymentCredential(DEPLOYMENT, OWNER).toBech32();
        Utxo supply = Utxo.builder().txHash("0b".repeat(32)).outputIndex(1).address(ownerWallet)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)),
                        Amount.asset(UNIT_A, 20))).build();

        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(OWNER.toBech32())
                .burn(POLICY_A, List.of(new Asset("0x" + TOK, BigInteger.valueOf(5))),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0)))
                .transfer(HOLDER.toBech32(), Amount.asset(UNIT_A, 1), BigIntPlutusData.of(0));

        Transaction txn = build(tx, OWNER, supply);

        int paramsIdx = LedgerOrdering.indexOfReferenceInput(txn, PARAMS.getTxHash(), PARAMS.getOutputIndex());
        int nodeIdx = LedgerOrdering.indexOfReferenceInput(txn, NODE_A.getTxHash(), NODE_A.getOutputIndex());

        // Every base-script input names the dispatcher's withdrawal; the dispatcher names transfer.
        assertThat(spendRedeemer(txn, supply)).isEqualTo(Cip113Redeemers.baseSpend(paramsIdx,
                LedgerOrdering.indexOfWithdrawal(txn, DEPLOYMENT.programmableLogicGlobalCredential())));
        assertThat(rewardRedeemer(txn, DEPLOYMENT.programmableLogicGlobalCredential()))
                .isEqualTo(Cip113Redeemers.transferAct());

        // The custody rule: issuance_logic's RefInput and the transfer proof's TokenExists name
        // one and the same reference input, and it is policy A's registry node.
        assertThat(rewardRedeemer(txn, DEPLOYMENT.transferCredential()))
                .isEqualTo(Cip113Redeemers.transfer(List.of(Cip113Redeemers.tokenExists(nodeIdx))));
        assertThat(rewardRedeemer(txn, DEPLOYMENT.issuanceLogicCredential()))
                .isEqualTo(Cip113Redeemers.issuanceLogic(Map.of(POLICY_A, Cip113Redeemers.mintRefInput(nodeIdx))));
        TransactionInput named = LedgerOrdering.sortedReferenceInputs(txn).get(nodeIdx);
        assertThat(named.getTransactionId() + "#" + named.getIndex())
                .isEqualTo(NODE_A.getTxHash() + "#" + NODE_A.getOutputIndex());

        // The permanent issuance policy reads only the params UTxO's position.
        assertThat(mintRedeemer(txn, POLICY_A)).isEqualTo(Cip113Redeemers.issuanceMint(paramsIdx));
        TransactionInput params = LedgerOrdering.sortedReferenceInputs(txn).get(paramsIdx);
        assertThat(params.getTransactionId()).isEqualTo(PARAMS.getTxHash());
    }

    // ------------------------------------------------------------ third party

    @Test
    void aSeizureNamesTheSubjectNodeAndDispatchesThroughThirdParty() {
        String holderWallet = SmartWalletAddress.ofPaymentCredential(DEPLOYMENT, HOLDER).toBech32();
        Utxo held = Utxo.builder().txHash("0c".repeat(32)).outputIndex(2).address(holderWallet)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)),
                        Amount.asset(UNIT_A, 9))).build();

        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(ADMIN.toBech32())
                .thirdPartyTransfer(HOLDER.toBech32(), ADMIN.toBech32(), Amount.asset(UNIT_A, 4),
                        BigIntPlutusData.of(0));

        Transaction txn = build(tx, ADMIN, held);

        int paramsIdx = LedgerOrdering.indexOfReferenceInput(txn, PARAMS.getTxHash(), PARAMS.getOutputIndex());
        int nodeIdx = LedgerOrdering.indexOfReferenceInput(txn, NODE_A.getTxHash(), NODE_A.getOutputIndex());

        assertThat(spendRedeemer(txn, held)).isEqualTo(Cip113Redeemers.baseSpend(paramsIdx,
                LedgerOrdering.indexOfWithdrawal(txn, DEPLOYMENT.programmableLogicGlobalCredential())));
        assertThat(rewardRedeemer(txn, DEPLOYMENT.programmableLogicGlobalCredential()))
                .isEqualTo(Cip113Redeemers.thirdPartyAct());

        PlutusData thirdParty = rewardRedeemer(txn, DEPLOYMENT.thirdPartyCredential());
        assertThat(thirdParty).isInstanceOf(ConstrPlutusData.class);
        List<PlutusData> fields = ((ConstrPlutusData) thirdParty).getData().getPlutusDataList();
        assertThat(fields).as("registry_node_idx, outputs_start_idx — no params_idx").hasSize(2);
        assertThat(fields.get(0)).isEqualTo(BigIntPlutusData.of(nodeIdx));
        assertThat(txn.getBody().getOutputs().get(((BigIntPlutusData) fields.get(1)).getValue().intValue())
                .getAddress()).as("outputs_start_idx points at the holder's continuing output")
                .isEqualTo(holderWallet);
    }

    // ------------------------------------------------------------- helpers

    private static Transaction build(ProgrammableTokenTx tx, Address payer, Utxo... smartWallet) {
        ProtocolParams protocolParams = protocolParams();
        ProtocolParamsSupplier protocolParamsSupplier = () -> protocolParams;

        Map<String, List<Utxo>> chain = new LinkedHashMap<>();
        chain.put(payer.toBech32(), List.of(Utxo.builder().txHash("ee".repeat(32)).outputIndex(0)
                .address(payer.toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(500_000_000L)))).build()));
        for (Utxo utxo : smartWallet) {
            chain.computeIfAbsent(utxo.getAddress(), a -> new ArrayList<>());
            List<Utxo> at = new ArrayList<>(chain.get(utxo.getAddress()));
            at.add(utxo);
            chain.put(utxo.getAddress(), at);
        }
        chain.put(PARAMS.getAddress(), List.of(PARAMS));
        chain.put(NODE_A.getAddress(), List.of(NODE_A));
        UtxoSupplier utxoSupplier = new InMemoryUtxoSupplier(chain);

        Cip113ProtocolService service = service(protocolParamsSupplier);
        ProgrammableTokenExtension extension = ProgrammableTokenExtension.builder()
                .protocol(new Cip113Protocol(service)).deployment(Map.of()).build();

        // Nothing is evaluated: every redeemer gets the same nominal budget, however many there are.
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

    private static Cip113ProtocolService service(ProtocolParamsSupplier protocolParamsSupplier) {
        RegistryNode nodeA = RegistryNode.builder().key(POLICY_A).next("ff".repeat(28))
                .mintingLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .transferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .thirdPartyTransferLogicScript(Credential.fromScript(hash(LOGIC_A)))
                .unfrackingLogicScript(Credential.fromKey(""))
                .globalStateCs("").build();
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
        DeploymentScripts scripts = new DeploymentScripts((ScriptSupplier) null, DEPLOYMENT)
                .registerAll(List.of(BASE, TRANSFER, LOGIC_A, THIRD_PARTY, DISPATCHER, ISSUANCE_LOGIC));
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

    private static PlutusData spendRedeemer(Transaction txn, Utxo input) {
        int idx = LedgerOrdering.indexOfInput(txn, input.getTxHash(), input.getOutputIndex());
        return redeemer(txn, RedeemerTag.Spend, idx);
    }

    private static PlutusData rewardRedeemer(Transaction txn, Credential credential) {
        return redeemer(txn, RedeemerTag.Reward, LedgerOrdering.indexOfWithdrawal(txn, credential));
    }

    private static PlutusData mintRedeemer(Transaction txn, String policyId) {
        List<String> policies = new ArrayList<>();
        txn.getBody().getMint().forEach(ma -> policies.add(ma.getPolicyId().toLowerCase()));
        policies.sort(PolicyOrdering.COMPARATOR);
        return redeemer(txn, RedeemerTag.Mint, policies.indexOf(policyId.toLowerCase()));
    }

    private static PlutusData redeemer(Transaction txn, RedeemerTag tag, int index) {
        List<Redeemer> matching = txn.getWitnessSet().getRedeemers().stream()
                .filter(r -> r.getTag() == tag && r.getIndex().intValue() == index)
                .toList();
        assertThat(matching).as("one %s redeemer at index %d", tag, index).hasSize(1);
        return matching.get(0).getData();
    }

    private static ProtocolParams protocolParams() {
        try (InputStream in = Cip113BuiltTransactionTest.class.getResourceAsStream("/cip113/protocol-params.json")) {
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

    /** UTxOs by address, all on the first page; enough for selection, collateral and lookups. */
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
