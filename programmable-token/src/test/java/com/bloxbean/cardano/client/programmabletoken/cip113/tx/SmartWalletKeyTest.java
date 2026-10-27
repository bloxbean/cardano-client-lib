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
import com.bloxbean.cardano.client.programmabletoken.ThirdPartyBurnAuthorization;
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
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A deployment keyed by the owner's stake credential spends, pays and requires the signature of
 * that wallet — and the default stays the payment-key wallet.
 *
 * <p>An application that keeps tokens at {@code (base script, stake credential)} could previously
 * mint to such a wallet (receivers pass through) but never spend from it: the sender's own wallet
 * was always derived from the payment key. Built offline through QuickTx, nothing evaluated.</p>
 */
class SmartWalletKeyTest {

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

    @Test
    void theDefaultIsThePaymentKeyAndTheBuiltInsKeepIt() {
        assertThat(PAYMENT_KEYED.getSmartWalletKey()).isEqualTo(SmartWalletKey.PAYMENT);
        assertThat(Cip113Deployments.PREVIEW.getSmartWalletKey()).isEqualTo(SmartWalletKey.PAYMENT);
        assertThat(SmartWalletAddress.ofOwner(PAYMENT_KEYED, OWNER).toBech32())
                .isEqualTo(SmartWalletAddress.ofPaymentCredential(PAYMENT_KEYED, OWNER).toBech32());
        assertThat(SmartWalletAddress.ownerSigner(PAYMENT_KEYED, OWNER)).isEqualTo(HexUtil.decodeHexString(OWNER_PAYMENT));
    }

    @Test
    void aStakeKeyedTransferSpendsTheStakeWalletAndRequiresTheStakeKey() {
        String stakeWallet = SmartWalletAddress.ofStakeCredential(STAKE_KEYED, OWNER).toBech32();
        String paymentWallet = SmartWalletAddress.ofPaymentCredential(STAKE_KEYED, OWNER).toBech32();
        assertThat(stakeWallet).isNotEqualTo(paymentWallet);
        Utxo held = Utxo.builder().txHash("0b".repeat(32)).outputIndex(1).address(stakeWallet)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)), Amount.asset(UNIT_A, 20))).build();

        Transaction txn = build(STAKE_KEYED, new ProgrammableTokenTx().from(OWNER.toBech32())
                .transfer(RECEIVER.toBech32(), Amount.asset(UNIT_A, 5), BigIntPlutusData.of(0)), held);

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId).contains(held.getTxHash());
        assertThat(txn.getBody().getRequiredSigners()).extracting(HexUtil::encodeHexString)
                .contains(OWNER_STAKE).doesNotContain(OWNER_PAYMENT);
        assertThat(outputsHolding(txn, UNIT_A)).extracting(TransactionOutput::getAddress)
                .as("the remainder stays at the stake wallet, the receiver gets its stake wallet")
                .containsExactlyInAnyOrder(stakeWallet, SmartWalletAddress.ofStakeCredential(STAKE_KEYED, RECEIVER).toBech32());
    }

    @Test
    void aStakeKeyedBurnSpendsTheStakeWallet() {
        String stakeWallet = SmartWalletAddress.ofStakeCredential(STAKE_KEYED, OWNER).toBech32();
        Utxo held = Utxo.builder().txHash("0c".repeat(32)).outputIndex(0).address(stakeWallet)
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)), Amount.asset(UNIT_A, 20))).build();

        Transaction txn = build(STAKE_KEYED, new ProgrammableTokenTx().from(OWNER.toBech32())
                .burn(POLICY_A, List.of(new Asset("0x" + TOK, BigInteger.valueOf(5))),
                        BurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0))), held);

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId).contains(held.getTxHash());
        assertThat(txn.getBody().getRequiredSigners()).extracting(HexUtil::encodeHexString).contains(OWNER_STAKE);
    }

    @ParameterizedTest
    @EnumSource(SmartWalletKey.class)
    void aThirdPartyBurnFromTheSendersWalletDeclaresNoHolderSigner(SmartWalletKey key) {
        Cip113Deployment deployment = PAYMENT_KEYED.toBuilder().smartWalletKey(key).build();
        Utxo held = heldByOwner(deployment);

        Transaction txn = build(deployment, thirdPartyBurnFromOwner(held), held);

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                .contains(held.getTxHash());
        assertThat(txn.getBody().getRequiredSigners())
                .as("third-party authorization does not require the holder's %s key", key).isNullOrEmpty();
    }

    @ParameterizedTest
    @EnumSource(SmartWalletKey.class)
    void aThirdPartyTransferFromTheSendersWalletDeclaresNoHolderSigner(SmartWalletKey key) {
        Cip113Deployment deployment = PAYMENT_KEYED.toBuilder().smartWalletKey(key).build();
        Utxo held = heldByOwner(deployment);

        Transaction txn = build(deployment, new ProgrammableTokenTx().from(OWNER.toBech32())
                .thirdPartyTransfer(OWNER.toBech32(), RECEIVER.toBech32(),
                        Amount.asset(UNIT_A, 5), BigIntPlutusData.of(0)), held);

        assertThat(txn.getBody().getInputs()).extracting(TransactionInput::getTransactionId)
                .contains(held.getTxHash());
        assertThat(txn.getBody().getRequiredSigners())
                .as("third-party authorization does not require the holder's %s key", key).isNullOrEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"55", "77"})
    void aThirdPartyBurnPreservesAnExplicitAdminOrHolderSigner(String signerByte) {
        String requiredSigner = signerByte.repeat(28);
        Utxo held = heldByOwner(STAKE_KEYED);

        Transaction txn = build(STAKE_KEYED, thirdPartyBurnFromOwner(held), OWNER,
                context -> context.withRequiredSigners(HexUtil.decodeHexString(requiredSigner)), held);

        assertThat(txn.getBody().getRequiredSigners()).extracting(HexUtil::encodeHexString)
                .as("the caller can explicitly require an admin key or the holder's stake key")
                .containsExactly(requiredSigner);
    }

    @Test
    void aStakeKeyedMintFromAnEnterpriseIssuerStillBuilds() {
        Transaction txn = build(STAKE_KEYED, new ProgrammableTokenTx().from(ENTERPRISE_ISSUER.toBech32())
                .mint(POLICY_A, RECEIVER.toBech32(), List.of(new Asset("0x" + TOK, BigInteger.TEN)),
                        BigIntPlutusData.of(0), null), ENTERPRISE_ISSUER);

        assertThat(outputsHolding(txn, UNIT_A)).extracting(TransactionOutput::getAddress)
                .containsExactly(SmartWalletAddress.ofStakeCredential(STAKE_KEYED, RECEIVER).toBech32());
        assertThat(txn.getBody().getRequiredSigners() == null ? List.<byte[]>of() : txn.getBody().getRequiredSigners())
                .as("an issuer without a stake credential owns no wallet to authorise").isEmpty();
    }

    /** Nothing reads the owner's key unless the owner's own wallet is spent. */
    @Test
    void aMintDeclaresNoOwnerSignerUnderEitherKey() {
        for (Cip113Deployment deployment : List.of(PAYMENT_KEYED, STAKE_KEYED)) {
            Transaction txn = build(deployment, new ProgrammableTokenTx().from(OWNER.toBech32())
                    .mint(POLICY_A, RECEIVER.toBech32(), List.of(new Asset("0x" + TOK, BigInteger.TEN)),
                            BigIntPlutusData.of(0), null), OWNER);
            assertThat(txn.getBody().getRequiredSigners() == null ? List.<byte[]>of() : txn.getBody().getRequiredSigners())
                    .as("%s-keyed mint", deployment.getSmartWalletKey()).isEmpty();
        }
    }

    /** A plan written for a stake-keyed deployment says so, and a payment-keyed service refuses it. */
    @Test
    void savedPlansPinTheWalletKey() {
        var stakeService = mock(Cip113ProtocolService.class);
        when(stakeService.deployment()).thenReturn(STAKE_KEYED);
        var paymentService = mock(Cip113ProtocolService.class);
        when(paymentService.deployment()).thenReturn(PAYMENT_KEYED);
        var written = new com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService(stakeService)
                .extension().metadata();
        assertThat(written.getDeployment()).containsEntry("smart_wallet_key", "STAKE");
        var writtenForPayment = new com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService(paymentService)
                .extension().metadata();
        assertThat(writtenForPayment.getDeployment()).containsEntry("smart_wallet_key", "PAYMENT");

        assertThatThrownBy(() -> new Cip113Protocol(paymentService).validateMetadata(written))
                .hasMessageContaining("smart_wallet_key");
        assertThatThrownBy(() -> new Cip113Protocol(stakeService).validateMetadata(writtenForPayment))
                .as("the other direction too").hasMessageContaining("smart_wallet_key");
        new Cip113Protocol(stakeService).validateMetadata(written);
        new Cip113Protocol(paymentService).validateMetadata(writtenForPayment);
        assertThatThrownBy(() -> SmartWalletAddress.ofOwner(PAYMENT_KEYED.toBuilder().smartWalletKey(null).build(), OWNER))
                .hasMessageContaining("not null");
    }

    @Test
    void aStakeKeyedOwnerNeedsAKeyStakeCredential() {
        Address scriptStaked = AddressProvider.getBaseAddress(Credential.fromKey(OWNER_PAYMENT),
                Credential.fromScript("55".repeat(28)), Networks.testnet());
        assertThatThrownBy(() -> SmartWalletAddress.ownerSigner(STAKE_KEYED, scriptStaked))
                .hasMessageContaining("script credential");
        assertThatThrownBy(() -> SmartWalletAddress.ownerSigner(STAKE_KEYED, ENTERPRISE_ISSUER))
                .hasMessageContaining("No stake credential");
    }

    // ------------------------------------------------------------- helpers

    private static Utxo heldByOwner(Cip113Deployment deployment) {
        return Utxo.builder().txHash("0c".repeat(32)).outputIndex(0)
                .address(SmartWalletAddress.ofOwner(deployment, OWNER).toBech32())
                .amount(List.of(Amount.lovelace(BigInteger.valueOf(2_000_000L)), Amount.asset(UNIT_A, 20)))
                .build();
    }

    private static ProgrammableTokenTx thirdPartyBurnFromOwner(Utxo held) {
        return new ProgrammableTokenTx().from(OWNER.toBech32())
                .thirdPartyBurn(held.getTxHash() + "#" + held.getOutputIndex(), POLICY_A,
                        List.of(new Asset("0x" + TOK, BigInteger.valueOf(5))),
                        ThirdPartyBurnAuthorization.of(BigIntPlutusData.of(0), BigIntPlutusData.of(0)));
    }

    private static List<TransactionOutput> outputsHolding(Transaction txn, String unit) {
        return txn.getBody().getOutputs().stream()
                .filter(o -> o.getValue().getMultiAssets() != null && o.getValue().getMultiAssets().stream()
                        .anyMatch(ma -> unit.startsWith(ma.getPolicyId())))
                .toList();
    }

    private static Transaction build(Cip113Deployment deployment, ProgrammableTokenTx tx, Utxo held) {
        return build(deployment, tx, OWNER, held);
    }

    private static Transaction build(Cip113Deployment deployment, ProgrammableTokenTx tx, Address payer, Utxo... smartWallet) {
        return build(deployment, tx, payer, context -> { }, smartWallet);
    }

    private static Transaction build(Cip113Deployment deployment, ProgrammableTokenTx tx, Address payer,
                                     Consumer<QuickTxBuilder.TxContext> configure, Utxo... smartWallet) {
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
        QuickTxBuilder.TxContext context = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, null)
                .withExtension(extension)
                .compose(tx)
                .feePayer(payer.toBech32())
                .collateralPayer(payer.toBech32())
                .withScriptSupplier(service.scripts())
                .withTxEvaluator(evaluator);
        configure.accept(context);
        return context.build();
    }

    private static Cip113ProtocolService service(Cip113Deployment deployment, ProtocolParamsSupplier protocolParamsSupplier) {
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
        try (InputStream in = SmartWalletKeyTest.class.getResourceAsStream("/cip113/protocol-params.json")) {
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
