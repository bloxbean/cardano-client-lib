package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.governance.GovId;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.ProtocolVersion;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.*;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GovernanceTxIT extends QuickTxBaseIT {
    static BackendService backendService;
    static Account sender1;
    static Account sender2;

    static String sender1Addr;
    static String sender2Addr;

    static QuickTxBuilder quickTxBuilder;

    //For devkit default guardrails script
    static PlutusV3Script alwaysTrueScript = PlutusV3Script.builder()
            .type("PlutusScriptV3")
            .cborHex("46450101002499")
            .build();

    @BeforeAll
    static void setup() {
        backendService = new BFBackendService("http://localhost:8080/api/v1/", "Dummy");
        quickTxBuilder = new QuickTxBuilder(backendService);

        String senderMnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        sender2 = new Account(Networks.testnet(), senderMnemonic, DerivationPath.createExternalAddressDerivationPathForAccount(1));
        sender2Addr = sender2.baseAddress();

        resetDevNet();
        topUpFund(sender1Addr, 500000L);
    }

    @Test
    @Order(1)
    void registerDrep() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(sender1, anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1.drepHdKeyPair()))
                .complete();

        System.out.println("DRepId : " + sender1.drepId());


        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(2)
    void deRegisterDrep() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .unregisterDRep(sender1.drepCredential())
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(3)
    void registerDrep_again() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .registerDRep(sender1, anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1.drepHdKeyPair()))
                .complete();

        System.out.println("DRepId : " + sender1.drepId());


        System.out.println(result);
        assertTrue(result.isSuccessful());
        waitForTransaction(result);

        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(4)
    void voteDelegation() {
        try {
            stakeAddressRegistration(sender2Addr);
        } catch (Exception e) {
            e.printStackTrace();
            //Incase it's already registered
        }

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        //CIP 105
        DRep drep = GovId.toDrep(sender1.drepId());
        System.out.println("Drep : " + sender1.drepId());

        //For Legacy DRep id (CIP 105)
//      DRep drep = GovId.toDrep(sender1.legacyDRepId());

        Tx tx = new Tx()
                .delegateVotingPowerTo(sender2Addr, drep)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.stakeKeySignerFrom(sender2))
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector(transaction -> {
                    System.out.println(transaction);
                })
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }


    @Test
    @Order(5)
    void updateDrep() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx drepRegTx = new Tx()
                .updateDRep(sender1.drepCredential(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println("DRepId : " + sender1.drepId());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(6)
    void updateDrep_nullAnchor() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        Tx drepRegTx = new Tx()
                .updateDRep(sender1.drepCredential())
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println("DRepId : " + sender1.drepId());
        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(15)
    void createProposal_infoAction() {
        try {
            stakeAddressRegistration(sender1Addr);
        } catch (Exception e) {
            e.printStackTrace();
            //Incase already registered
        }

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var govAction = new InfoAction();
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(govAction, sender1.stakeAddress(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(16)
    void createProposal_newConstitutionAction() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://bit.ly/2kBHHHL",
                HexUtil.decodeHexString("cdfef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));
        var govAction = new NewConstitution();
        //govAction.setPrevGovActionId(new GovActionId("bd5d786d745ec7c1994f8cff341afee513c7cdad73e8883d540ff71c41763fd1", 0));
        govAction.setConstitution(Constitution.builder()
                .anchor(anchor)
                .build());

        Tx tx = new Tx()
                .createProposal(govAction, sender1.stakeAddress(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    String prevGovId;
    @Test
    @Order(17)
    void createProposal_noConfidence_withPrevGovAction() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var noConfidence = new NoConfidence();
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(noConfidence, sender1.stakeAddress(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        prevGovId = result.getValue();
        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);

        //Another noConfidence proposal with prevGovAction id

        var noConfidence2 = new NoConfidence();
        System.out.println("Prev Gov id: " + prevGovId);
        noConfidence2.setPrevGovActionId(new GovActionId(prevGovId, 0));
        var anchor2 = new Anchor("https://abc.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx2 = new Tx()
                .createProposal(noConfidence2, sender1.stakeAddress(), anchor2)
                .from(sender1Addr);

        Result<String> result2 = quickTxBuilder.compose(tx2)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result2);
        assertTrue(result2.isSuccessful());
        checkIfUtxoAvailable(result2.getValue(), sender1Addr);
    }

    @Test
    @Order(18)
    void createProposal_parameterChangeAction() throws CborSerializationException {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var parameterChange = new ParameterChangeAction();
//        parameterChange.setPrevGovActionId(new GovActionId("529736be1fac33431667f2b66231b7b66d4c7a3975319ddac7cfb17dcb5c4145", 0));
        parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                .minPoolCost(adaToLovelace(100))
                .build()
        );

        //For devkit default guardrails script
        PlutusV3Script alwaysTrueScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        parameterChange.setPolicyHash(alwaysTrueScript.getScriptHash());

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx tx = new ScriptTx()
                .createProposal(parameterChange, sender1.stakeAddress(), anchor, PlutusData.unit())
                .attachProposingValidator(alwaysTrueScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(19)
    void createProposal_treasuryWithdrawalAction() throws CborSerializationException {
        String rewardAddress = "stake_test1ur6l9f5l9jw44kl2nf6nm5kca3nwqqkccwynnjm0h2cv60ccngdwa";
        try {
            stakeAddressRegistration(rewardAddress);
        } catch (Exception e) {
            e.printStackTrace();
            //If already registered
        }

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var treasuryWithdrawalsAction = new TreasuryWithdrawalsAction();
        treasuryWithdrawalsAction.addWithdrawal(new Withdrawal(rewardAddress, adaToLovelace(20)));

        //For devkit default guardrails script
        treasuryWithdrawalsAction.setPolicyHash(alwaysTrueScript.getScriptHash());

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx tx = new ScriptTx()
                .createProposal(treasuryWithdrawalsAction, sender1.stakeAddress(), anchor, PlutusData.unit())
                .attachProposingValidator(alwaysTrueScript);

        Result<String> result = quickTxBuilder.compose(tx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxInspector(transaction -> {
                    System.out.println(transaction);
                })
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(20)
    void createProposal_updateCommittee() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var updateCommittee = new UpdateCommittee();
//        updateCommittee.setPrevGovActionId(new GovActionId("b3ce0371310a07a797657d19453d953bb352b6841c2f5c5e0bd2557189ef5c3a", 0));
        updateCommittee.setQuorumThreshold(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(3)));

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(updateCommittee, sender1.stakeAddress(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(21)
    void createProposal_hardforkInitiation() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var hardforkInitiation = new HardForkInitiationAction();
//        hardforkInitiation.setPrevGovActionId(new GovActionId("416f7f01c548a85546aa5bbd155b34bb2802df68e08db4e843ef6da764cd8f7e", 0));
        hardforkInitiation.setProtocolVersion(new ProtocolVersion(11, 0));

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(hardforkInitiation, sender1.stakeAddress(), anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(30)
    void createVote() {
        //Create an info proposal and then vote
        var govAction = new InfoAction();

        Tx infoPropTx = new Tx()
                .createProposal(govAction, sender1.stakeAddress(), new Anchor("https://xyz.com",
                        HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")))
                .from(sender1Addr);

        Result<String> infoPropResult = quickTxBuilder.compose(infoPropTx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(infoPropResult);
        assertTrue(infoPropResult.isSuccessful());
        checkIfUtxoAvailable(infoPropResult.getValue(), sender1Addr);

        //Create vote and submit
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        var voter = new Voter(VoterType.DREP_KEY_HASH, sender1.drepCredential());
        Tx tx = new Tx()
                .createVote(voter, new GovActionId(infoPropResult.getValue(), 0),
                        Vote.NO, anchor)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Order(31)
    void createVote_noAnchor() {
        //Create an info proposal and then vote
        var govAction = new InfoAction();

        Tx infoPropTx = new Tx()
                .createProposal(govAction, sender1.stakeAddress(), new Anchor("https://xyz.com",
                        HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937")))
                .registerDRep(sender2)
                .from(sender1Addr);

        Result<String> infoPropResult = quickTxBuilder.compose(infoPropTx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.drepKeySignerFrom(sender2))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(infoPropResult);
        assertTrue(infoPropResult.isSuccessful());
        checkIfUtxoAvailable(infoPropResult.getValue(), sender1Addr);

        //Vote
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var voter = new Voter(VoterType.DREP_KEY_HASH, sender2.drepCredential());
        Tx tx = new Tx()
                .createVote(voter, new GovActionId(infoPropResult.getValue(), 0),
                        Vote.YES)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.drepKeySignerFrom(sender2))
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(s -> System.out.println(s));

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    //stake address registration
    void stakeAddressRegistration(String addressToRegister) {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .registerStakeAddress(addressToRegister)
                .from(sender1Addr);

        System.out.println("Registering stake address for address: " + addressToRegister);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(msg -> System.out.println(msg));

        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }
}
