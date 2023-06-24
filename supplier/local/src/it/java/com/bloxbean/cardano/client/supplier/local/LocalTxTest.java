package com.bloxbean.cardano.client.supplier.local;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalTxTest {
    Account sender1;
    Account sender2;

    String sender1Addr;
    String sender2Addr;

    String receiver1 = "addr_test1qz3s0c370u8zzqn302nppuxl840gm6qdmjwqnxmqxme657ze964mar2m3r5jjv4qrsf62yduqns0tsw0hvzwar07qasqeamp0c";
    String receiver2 = "addr_test1qqwpl7h3g84mhr36wpetk904p7fchx2vst0z696lxk8ujsjyruqwmlsm344gfux3nsj6njyzj3ppvrqtt36cp9xyydzqzumz82";
    String receiver3 = "addr_test1qqqvjp4ffcdqg3fmx0k8rwamnn06wp8e575zcv8d0m3tjn2mmexsnkxp7az774522ce4h3qs4tjp9rxjjm46qf339d9sk33rqn";

    UtxoSupplier utxoSupplier;
    LocalProtocolSupplier protocolSupplier;
    TransactionProcessor transactionProcessor;

    @BeforeEach
    void setup() {
        LocalClientProviderFactory factory = new LocalClientProviderFactory("/Users/satya/work/cardano-node/preprod-8/db/node.socket", 1);
        LocalStateQueryClient localStateQueryClient = factory.getLocalStateQueryClient();
        factory.start();

        utxoSupplier = new LocalUtxoSupplier(localStateQueryClient);
        protocolSupplier = new LocalProtocolSupplier(localStateQueryClient);
        transactionProcessor = new LocalTransactionProcessor(factory.getTxSubmissionClient());

        //addr_test1qp73ljurtknpm5fgey5r2y9aympd33ksgw0f8rc5khheg83y35rncur9mjvs665cg4052985ry9rzzmqend9sqw0cdksxvefah
        String senderMnemonic = "drive useless envelope shine range ability time copper alarm museum near flee wrist live type device meadow allow churn purity wisdom praise drop code";
        sender1 = new Account(Networks.testnet(), senderMnemonic);
        sender1Addr = sender1.baseAddress();

        //addr_test1qz5fcpvkg7pekqvv9ld03t5sx2w2c2fac67fzlaxw5844s83l4p6tr389lhgcpe4797kt7xkcxqvcc4a6qjshzsmta8sh3ncs4
        String sender2Mnemonic = "access else envelope between rubber celery forum brief bubble notice stomach add initial avocado current net film aunt quick text joke chase robust artefact";
        sender2 = new Account(Networks.testnet(), sender2Mnemonic);
        sender2Addr = sender2.baseAddress();
    }

    @Test
    @Disabled
    void simplePayment() throws InterruptedException, ApiException {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(100), "This is tx through local supplier");

        QuickTxBuilder  quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolSupplier, transactionProcessor);

        Tx tx = new Tx()
                .payToAddress(receiver1, Amount.ada(1.5))
                .payToAddress(receiver2, Amount.ada(2.5))
                .attachMetadata(metadata)
                .from(sender1Addr);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .complete();

        System.out.println(result);
        assertTrue(result.isSuccessful());
        checkIfUtxoAvailable(result.getValue(), sender1Addr);
    }

    @Test
    @Disabled
    void alwaysTrueScript() throws ApiException, InterruptedException {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        int randInt = rand.nextInt();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder  quickTxBuilder = new QuickTxBuilder(utxoSupplier, protocolSupplier, transactionProcessor);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .complete();

        System.out.println(result);
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withTxEvaluator(new AikenTransactionEvaluator(utxoSupplier, protocolSupplier))
                .complete();

        System.out.println(result);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender1Addr);
    }

    private void checkIfUtxoAvailable(String txHash, String address) throws ApiException, InterruptedException {
        int counter = 0;
        while(counter < 60) {
            counter++;
            Thread.sleep(3000);
            System.out.println("Checking if txHash is available : " + txHash);
            List<Utxo> utxos = utxoSupplier.getAll(address);
            if (utxos == null || utxos.size() == 0) continue;

            Optional<Utxo> utxo = utxos.stream().filter(u -> u.getTxHash().equals(txHash)).findFirst();
            if (utxo.isPresent()) {
                System.out.println("Utxo found >>>");
                return;
            }
        }
    }
}
