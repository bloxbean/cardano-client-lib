package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GovernanceTxIT extends QuickTxBaseIT {
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

        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
     //   String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        String senderMnemonic = "damp wish scrub sentence vibrant gauge tumble raven game extend winner acid side amused vote edge affair buzz hospital slogan patient drum day vital";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        //addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
        String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
        sender2 = new Account(Networks.testnet(), sender2Mnemonic);
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
}
