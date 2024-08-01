package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.impl.StaticTransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.junit.jupiter.api.Assertions.assertTrue;

//To run -- Start Yaci DevKit Node and run these tests
//This need the following script hash in the constitution
//6913eb6a46b67bfa1ca082f2b410b0a4e502237d03f7a0b7cbf1b025 (always true)
public class GovernanceScriptTxIT extends QuickTxBaseIT {
    BackendService backendService;
    Account sender1;
    String sender1Addr;

    QuickTxBuilder quickTxBuilder;

    static GovActionId lastGovActionId;

    @Override
    public BackendService getBackendService() {
        backendType = DEVKIT;
        return super.getBackendService();
    }

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        quickTxBuilder = new QuickTxBuilder(backendService);

        //addr_test1qryvgass5dsrf2kxl3vgfz76uhp83kv5lagzcp29tcana68ca5aqa6swlq6llfamln09tal7n5kvt4275ckwedpt4v7q48uhex
        String senderMnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();
    }

    @Test
    @Order(1)
    void registerDrep() throws CborSerializationException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var protocolParamSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        var protocolParams = protocolParamSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx drepRegTx = new ScriptTx()
                .registerDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .complete();

        System.out.println("DRepId : " + sender1.drepId());

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(2)
    void updateDRep() throws CborSerializationException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var protocolParamSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        var protocolParams = protocolParamSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://update.com",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx tx = new ScriptTx()
                .updateDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                //.withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .ignoreScriptCostEvaluationError(true)
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(3)
    void createProposal_parameterChangeAction() throws Exception {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var parameterChange = new ParameterChangeAction();
      //  parameterChange.setPrevGovActionId(new GovActionId("529736be1fac33431667f2b66231b7b66d4c7a3975319ddac7cfb17dcb5c4145", 0));
        parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                .minPoolCost(adaToLovelace(300))
                .build()
        );
        parameterChange.setPolicyHash(plutusScript.getScriptHash());
        System.out.println("Policy Hash : " + HexUtil.encodeHexString(plutusScript.getScriptHash()));
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx tx = new ScriptTx()
                .createProposal(parameterChange, sender1.stakeAddress(), anchor, BigIntPlutusData.of(1))
                .attachProposingValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .completeAndWait(s -> System.out.println(s));

        lastGovActionId = new GovActionId(result.getValue(), 0);

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(4)
    void createVote() throws CborSerializationException {
        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var drepCredential = Credential.fromScript(scriptHash);


        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://script.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        var voter = new Voter(VoterType.DREP_SCRIPT_HASH, drepCredential);
        ScriptTx tx = new ScriptTx()
                .createVote(voter, lastGovActionId,
                        Vote.YES, anchor, BigIntPlutusData.of(1))
                .attachVotingValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                ))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }


    @Test
    @Order(4)
    void voteDelegation() throws CborSerializationException {
        registerStakeKeys();
        PlutusV3Script drepPlutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var drepScriptHash = drepPlutusScript.getScriptHash();
        var drep = DRep.scriptHash(HexUtil.encodeHexString(drepScriptHash));

        String aikenCompiledCode1 = "581801000032223253330043370e00290010a4c2c6eb40095cd1"; //redeemer = 1
        PlutusScript delegatorScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(aikenCompiledCode1, PlutusVersion.v3);
        Address delegatorStakeAddress = AddressProvider.getRewardAddress(delegatorScript, Networks.testnet());

        //Delegator stake key Registration
        Tx stakeKeyRegTx = new Tx()
                .registerStakeAddress(delegatorStakeAddress)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(stakeKeyRegTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));
        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);

        //Delegation Tx

        ScriptTx delegationTx = new ScriptTx()
                .delegateVotingPowerTo(delegatorStakeAddress, drep, BigIntPlutusData.of(1))
                .attachCertificateValidator(delegatorScript);

        result = quickTxBuilder.compose(delegationTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(2934))
                        .steps(BigInteger.valueOf(976946))
                        .build())
                ))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(5)
    void deRegisterDrep() throws CborSerializationException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var protocolParamSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        var protocolParams = protocolParamSupplier.getProtocolParams();

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        ScriptTx tx = new ScriptTx()
                .unRegisterDRep(scriptCredential, sender1Addr, protocolParams.getDrepDeposit(), BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                //.withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new StaticTransactionEvaluator(List.of(ExUnits.builder()
                        .mem(BigInteger.valueOf(800))
                        .steps(BigInteger.valueOf(1000000))
                        .build())
                )).complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    private void registerStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        Tx tx = new Tx()
                .registerStakeAddress(sender1Addr)
                .attachMetadata(MessageMetadata.create().add("This is a script stake registration tx"))
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    private void deRegisterStakeKeys() {
        Result<String> result = _deRegisterStakeKeys();
        assertTrue(result.isSuccessful());
    }

    private Result<String> _deRegisterStakeKeys() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //stake Registration
        Tx tx = new Tx()
                .deregisterStakeAddress(sender1Addr)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.stakeKeySignerFrom(sender1))
                .withTxInspector((txn) -> System.out.println(JsonUtil.getPrettyJson(txn)))
                .completeAndWait(msg -> System.out.println(msg));

        System.out.println(result);
        if (result.isSuccessful())
            checkIfUtxoAvailable(result.getValue(), sender1Addr);
        return result;
    }
}
