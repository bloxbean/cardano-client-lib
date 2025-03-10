package com.bloxbean.cardano.hdwallet;

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
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StakeTxIT extends QuickTxBaseIT {
    static BackendService backendService;
    static UtxoSupplier utxoSupplier;
    static Wallet wallet1;
    static Wallet wallet2;

    static String poolId;
    static ProtocolParamsSupplier protocolParamsSupplier;

    static PlutusV3Script alwaysTrueScript = PlutusV3Script.builder()
            .type("PlutusScriptV3")
            .cborHex("46450101002499")
            .build();

    static String alwaysTrueScriptAddress = AddressProvider.getRewardAddress(alwaysTrueScript, Networks.testnet()).toBech32();

    static QuickTxBuilder quickTxBuilder;

    @BeforeAll
    static void beforeAll() {
        backendService = getBackendService();
        utxoSupplier = getUTXOSupplier();

        protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        quickTxBuilder = new QuickTxBuilder(backendService);

        String wallet1Mnemonic = "clog book honey force cricket stamp until seed minimum margin denial kind volume undo simple federal then jealous solid legal crucial crazy acoustic thank";
        wallet1 = Wallet.createFromMnemonic(Networks.testnet(), wallet1Mnemonic);
        String wallet2Mnemonic = "theme orphan remind output arrive lobster decorate ten gap piece casual distance attend total blast dilemma damp punch pride file limit soldier plug canoe";
        wallet2 = Wallet.createFromMnemonic(Networks.testnet(), wallet2Mnemonic);

        if (backendType.equals(DEVKIT)) {
            poolId = "pool1wvqhvyrgwch4jq9aa84hc8q4kzvyq2z3xr6mpafkqmx9wce39zy";
        } else {
            poolId = "pool1vqq4hdwrh442u97e2jh6k4xuscs3x5mqjjrn8daj36y7gt2rj85";
        }

        resetNetwork();
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

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
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
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
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
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
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
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
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
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
        Tx tx = new Tx()
                .registerStakeAddress(alwaysTrueScriptAddress)
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
    void stakeDelegation_scriptStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);

        //Delegation
        ScriptTx delegTx = new ScriptTx()
                .delegateTo(new Address(alwaysTrueScriptAddress), poolId, BigIntPlutusData.of(1))
                .attachMetadata(MessageMetadata.create().add("This is a delegation transaction"))
                .attachCertificateValidator(alwaysTrueScript);

        Result<String> delgResult = quickTxBuilder.compose(delegTx)
                .feePayer(wallet1)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(delgResult);
        assertTrue(delgResult.isSuccessful());

        checkIfUtxoAvailable(delgResult.getValue(), wallet1.getBaseAddressString(0));
    }

    @Test
    @Order(7)
    void scriptStakeAddress_deRegistration() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);
        ScriptTx tx = new ScriptTx()
                .deregisterStakeAddress(alwaysTrueScriptAddress, BigIntPlutusData.of(1))
                .attachMetadata(MessageMetadata.create().add("This is a script stake address deregistration tx"))
                .attachCertificateValidator(alwaysTrueScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(wallet1)
                .withSigner(SignerProviders.signerFrom(wallet1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), wallet1.getBaseAddressString(0));
    }

    private Result<String> _deRegisterStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService, utxoSupplier);

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
