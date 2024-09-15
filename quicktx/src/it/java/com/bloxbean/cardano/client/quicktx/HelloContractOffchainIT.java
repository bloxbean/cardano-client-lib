package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class HelloContractOffchainIT extends TestDataBaseIT {
    String compiledCode = "590169010100323232323232323225333002323232323253330073370e900118049baa0011323232533300a3370e900018061baa005132533300f00116132533333301300116161616132533301130130031533300d3370e900018079baa004132533300e3371e6eb8c04cc044dd5004a4410d48656c6c6f2c20576f726c642100100114a06644646600200200644a66602a00229404c94ccc048cdc79bae301700200414a2266006006002602e0026eb0c048c04cc04cc04cc04cc04cc04cc04cc04cc040dd50051bae301230103754602460206ea801054cc03924012465787065637420536f6d6528446174756d207b206f776e6572207d29203d20646174756d001616375c0026020002601a6ea801458c038c03c008c034004c028dd50008b1805980600118050009805001180400098029baa001149854cc00d2411856616c696461746f722072657475726e65642066616c736500136565734ae7155ceaab9e5573eae855d12ba401";

    PlutusScript plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v3);
    String scrtiptAddr = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();

    public void lock() {
        //create datum
        PlutusData datum = ConstrPlutusData.of(0, BytesPlutusData.of(sender1.getBaseAddress().getPaymentCredentialHash().get()));

        Tx tx = new Tx()
                .payToContract(scrtiptAddr, Amount.ada(10), datum)
                .payToContract(scrtiptAddr, Amount.ada(2), BytesPlutusData.of("Hello"))
                .from(sender1.baseAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender1))
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertThat(result.isSuccessful()).isTrue();
        checkIfUtxoAvailable(result.getValue(), scrtiptAddr);
    }

    public void unlock() throws ApiException {
        PlutusData datum = ConstrPlutusData.of(0, BytesPlutusData.of(sender1.getBaseAddress().getPaymentCredentialHash().get()));

        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        Utxo scriptUtxo = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scrtiptAddr, datum).orElseThrow();

        PlutusData redeemer = ConstrPlutusData.of(0, BytesPlutusData.of("Hello, World!"));

        ScriptTx sctipTx = new ScriptTx()
                .collectFrom(scriptUtxo, redeemer)
                .payToAddress(receiver1, Amount.ada(10))
                .attachSpendingValidator(plutusScript);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(sctipTx)
                .feePayer(receiver1)
                .collateralPayer(sender1.baseAddress())
                .withSigner(SignerProviders.signerFrom(sender1))
                .withRequiredSigners(sender1.getBaseAddress())
                .withTxInspector(tx -> {
                    System.out.println("Tx: " + JsonUtil.getPrettyJson(tx));
                })
                .preBalanceTx((context, txn) -> {
                    txn.getWitnessSet().getRedeemers().get(0)
                            .setExUnits(ExUnits.builder()
                                    .mem(BigInteger.valueOf(31507))
                                    .steps(BigInteger.valueOf(9666253))
                                    .build());
                })
                .completeAndWait(System.out::println);

        System.out.println(result);
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    public void lockAndUnlock() throws ApiException {
        lock();
        unlock();
    }

}
