package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.governance.LegacyDRepId;
import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.ProtocolVersion;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static org.junit.jupiter.api.Assertions.assertTrue;

//TODO -- Update tests to use Yaci DevKit Sanchonet
public class GovernanceTxIT extends TestDataBaseIT {
    BackendService backendService;
    Account sender1;
    Account sender2;

    String sender1Addr;
    String sender2Addr;

    QuickTxBuilder quickTxBuilder;

    @Override
    public BackendService getBackendService() {
        if (BLOCKFROST.equals(backendType)) {
            String bfProjectId = System.getProperty("BF_PROJECT_ID");
            if (bfProjectId == null || bfProjectId.isEmpty()) {
                bfProjectId = System.getenv("BF_PROJECT_ID");
            }

            return new BFBackendService(Constants.BLOCKFROST_SANCHONET_URL, bfProjectId);
        } else
            return super.getBackendService();
    }

    @BeforeEach
    void setup() {
        backendService = getBackendService();
        quickTxBuilder = new QuickTxBuilder(backendService);

        String senderMnemonic = "test test test test test test test test test test test test test test test test test test test test test test test sauce";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        sender2 = new Account(Networks.testnet(), senderMnemonic, DerivationPath.createExternalAddressDerivationPathForAccount(1));
        sender2Addr = sender2.baseAddress();
    }

    @Test
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
    void createProposal_infoAction() {
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
    void createProposal_newConstitutionAction() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://bit.ly/2kBHHHL",
                HexUtil.decodeHexString("cdfef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));
        var govAction = new NewConstitution();
        govAction.setPrevGovActionId(new GovActionId("bd5d786d745ec7c1994f8cff341afee513c7cdad73e8883d540ff71c41763fd1", 0));
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

    @Test
    void createProposal_noConfidence() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var noConfidence = new NoConfidence();
        noConfidence.setPrevGovActionId(new GovActionId("e86050ac376fc4df7c76635f648c963f44702e13beb81a5c9971a418013c74dc", 0));
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(noConfidence, sender1.stakeAddress(), anchor)
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
    void createProposal_parameterChangeAction() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var parameterChange = new ParameterChangeAction();
        parameterChange.setPrevGovActionId(new GovActionId("529736be1fac33431667f2b66231b7b66d4c7a3975319ddac7cfb17dcb5c4145", 0));
        parameterChange.setProtocolParamUpdate(ProtocolParamUpdate.builder()
                .minPoolCost(adaToLovelace(100))
                .build()
        );
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("cafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(parameterChange, sender1.stakeAddress(), anchor)
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
    void createProposal_treasuryWithdrawalAction() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var treasuryWithdrawalsAction = new TreasuryWithdrawalsAction();
        treasuryWithdrawalsAction.addWithdrawal(new Withdrawal("stake_test1ur6l9f5l9jw44kl2nf6nm5kca3nwqqkccwynnjm0h2cv60ccngdwa", adaToLovelace(20)));
        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        Tx tx = new Tx()
                .createProposal(treasuryWithdrawalsAction, sender1.stakeAddress(), anchor)
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
    void createProposal_updateCommittee() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var updateCommittee = new UpdateCommittee();
        updateCommittee.setPrevGovActionId(new GovActionId("b3ce0371310a07a797657d19453d953bb352b6841c2f5c5e0bd2557189ef5c3a", 0));
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
    void createProposal_hardforkInitiation() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var hardforkInitiation = new HardForkInitiationAction();
        hardforkInitiation.setPrevGovActionId(new GovActionId("416f7f01c548a85546aa5bbd155b34bb2802df68e08db4e843ef6da764cd8f7e", 0));
        hardforkInitiation.setProtocolVersion(new ProtocolVersion(9, 3));

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
    void createVote() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var anchor = new Anchor("https://xyz.com",
                HexUtil.decodeHexString("daeef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        var voter = new Voter(VoterType.DREP_KEY_HASH, sender1.drepCredential());
        Tx tx = new Tx()
                .createVote(voter, new GovActionId("5655fbb4ceafd34296fe58f6e3d28b8ff663a89e84aa0edd77bd02fe379cef4c", 0),
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
    void createVote_noAnchor() {
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        var voter = new Voter(VoterType.DREP_KEY_HASH, sender2.drepCredential());
        Tx tx = new Tx()
                .createVote(voter, new GovActionId("5655fbb4ceafd34296fe58f6e3d28b8ff663a89e84aa0edd77bd02fe379cef4c", 0),
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

    @Test
    void voteDelegation() {
//        stakeAddressRegistration(sender2Addr);
        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        DRep drep = LegacyDRepId.toDrep(sender1.drepId(), DRepType.ADDR_KEYHASH);
        System.out.println("Drep : " + sender1.drepId());

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
