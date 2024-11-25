package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.hdwallet.supplier.DefaultWalletUtxoSupplier;
import com.bloxbean.cardano.hdwallet.supplier.WalletUtxoSupplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StakeTxIT extends QuickTxBaseIT {
    static BackendService backendService;
    static UtxoSupplier utxoSupplier;
    static WalletUtxoSupplier walletUtxoSupplier;
    static Wallet wallet1;
    static Wallet wallet2;

    static String poolId;
    static ProtocolParamsSupplier protocolParamsSupplier;

    static String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
    static PlutusScript plutusScript1 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v2);

    static String aikenCompileCode2 = "581801000032223253330043370e00290020a4c2c6eb40095cd1"; //redeemer = 2
    static PlutusScript plutusScript2 = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompileCode2, PlutusVersion.v2);

    static String scriptStakeAddress1 = AddressProvider.getRewardAddress(plutusScript1, Networks.testnet()).toBech32();
    static String scriptStakeAddress2 = AddressProvider.getRewardAddress(plutusScript2, Networks.testnet()).toBech32();

    static QuickTxBuilder quickTxBuilder;
    static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void beforeAll() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();

        protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        quickTxBuilder = new QuickTxBuilder(backendService);

        String wallet1Mnemonic = "clog book honey force cricket stamp until seed minimum margin denial kind volume undo simple federal then jealous solid legal crucial crazy acoustic thank";
        wallet1 = new Wallet(Networks.testnet(), wallet1Mnemonic);
        String wallet2Mnemonic = "theme orphan remind output arrive lobster decorate ten gap piece casual distance attend total blast dilemma damp punch pride file limit soldier plug canoe";
        wallet2 = new Wallet(Networks.testnet(), wallet2Mnemonic);

        if (backendType.equals(DEVKIT)) {
            poolId = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";
        } else {
            poolId = "pool1vqq4hdwrh442u97e2jh6k4xuscs3x5mqjjrn8daj36y7gt2rj85";
        }

        topUpFund(wallet1.getBaseAddressString(0), 10000L);
    }

    @BeforeEach
    void setup() {
    }

    @Test
    @Order(1)
    void stakeAddressRegistration() {
        //De-register all stake addresses if required
        _deRegisterStakeKeys();

        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Tx tx = new Tx()
                .payToAddress(wallet1.getBaseAddressString(1), Amount.ada(1.5))
                .payToAddress(wallet2.getBaseAddressString(1), Amount.ada(2.5))
                .payToAddress(wallet1.getBaseAddressString(0), Amount.ada(4.3))
                .registerStakeAddress(wallet2)
                .registerStakeAddress(wallet1)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(1));
    }

    @Test
    @Order(2)
    void stakeAddressDeRegistration() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1); // TODO WalletUTXOSupplier only works with one wallet - Is it a problem?
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Tx tx = new Tx()
                .payToAddress(wallet1.getBaseAddressString(1), Amount.ada(1.5))
                .payToAddress(wallet1.getBaseAddressString(0), Amount.ada(4.0))
                .deregisterStakeAddress(wallet1)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withSigner(SignerProviders.stakeKeySignerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(1));
    }

    @Test
    @Order(3)
    void stakeAddressRegistration_onlyRegistration() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Tx tx = new Tx()
                .registerStakeAddress(wallet1)
                .attachMetadata(MessageMetadata.create().add("This is a stake registration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    @Test
    @Order(4)
    void stakeAddressDeRegistration_onlyRegistration() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Tx tx = new Tx()
                .deregisterStakeAddress(wallet1)
                .attachMetadata(MessageMetadata.create().add("This is a stake deregistration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withSigner(SignerProviders.stakeKeySignerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    @Test
    @Order(5)
    void scriptStakeAddress_registration() {
//        deregisterScriptsStakeKeys();
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        Tx tx = new Tx()
                .registerStakeAddress(scriptStakeAddress1)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    @Test
    @Order(6)
    void scriptStakeAddress_deRegistration() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);
        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(scriptStakeAddress1, BigIntPlutusData.of(1))
                .attachMetadata(MessageMetadata.create().add("This is a script stake address deregistration tx"))
                .attachCertificateValidator(plutusScript1);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(wallet1.getBaseAddressString(0))
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) ?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier) : null)
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    @Test
    @Order(9)
    void stakeDelegation_scriptStakeKeys() {
        registerScriptsStakeKeys();

        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        //Delegation
        ScriptTx delegTx = new ScriptTx()
                .delegateTo(new Address(scriptStakeAddress1), poolId, BigIntPlutusData.of(1))
                .attachMetadata(MessageMetadata.create().add("This is a delegation transaction"))
                .attachCertificateValidator(plutusScript1);

        Result<String> delgResult = quickTxBuilder.compose(delegTx)
                .feePayer(wallet1.getBaseAddressString(0))
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) ?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier) : null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(delgResult);
        assertTrue(delgResult.isSuccessful());

        checkIfUtxoAvailable(delgResult.getValue(), wallet1.getBaseAddressString(0));

        deregisterScriptsStakeKeys();
    }

    private void registerScriptsStakeKeys() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        //stake Registration
        Tx tx = new Tx()
                .registerStakeAddress(scriptStakeAddress1)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    private void deregisterScriptsStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(scriptStakeAddress1, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript1);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(wallet1.getBaseAddressString(0))
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxEvaluator(!backendType.equals(BLOCKFROST) ?
                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier) : null)
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    private Result<String> _deRegisterStakeKeys() {
        UtxoSupplier walletUtxoSupplier = new DefaultWalletUtxoSupplier(backendService.getUtxoService(), wallet1);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, walletUtxoSupplier);

        //stake Registration
        Tx tx = new Tx()
                .deregisterStakeAddress(wallet1)
                .deregisterStakeAddress(wallet2)
                .from(wallet1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.stakeKeySignerFrom(wallet1))
                .withSigner(SignerProviders.stakeKeySignerFrom(wallet2))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
        return result;
    }
}
