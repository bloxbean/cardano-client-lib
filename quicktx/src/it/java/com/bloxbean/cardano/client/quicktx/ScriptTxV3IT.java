package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.ScriptUtxoFinders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScriptTxV3IT extends TestDataBaseIT {

    @Test
    void alwaysTrueScript() throws ApiException {
        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("49480100002221200101")
                .build();

        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).toBech32();
        BigInteger scriptAmt = new BigInteger("2479280");

        Random rand = new Random();
        long randInt = System.currentTimeMillis();
        BigIntPlutusData plutusData =  new BigIntPlutusData(BigInteger.valueOf(randInt)); //any random number

        Tx tx = new Tx();
        tx.payToContract(scriptAddress, Amount.lovelace(scriptAmt), plutusData)
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);

        System.out.println(result.getResponse());
        checkIfUtxoAvailable(result.getValue(), scriptAddress);

        Optional<Utxo> optionalUtxo  = ScriptUtxoFinders.findFirstByInlineDatum(utxoSupplier, scriptAddress, plutusData);
        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(optionalUtxo.get(), plutusData)
                .payToAddress(receiver1, Amount.lovelace(scriptAmt))
                .attachSpendingValidator(plutusScript)
                .withChangeAddress(scriptAddress, plutusData);

        Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(sender2Addr)
                .withSigner(SignerProviders.signerFrom(sender2))
                .withRequiredSigners(sender2.getBaseAddress())
//                .withTxEvaluator(!backendType.equals(BLOCKFROST)?
//                        new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier): null)
                .withTxEvaluator(new TransactionEvaluator() {
                    @Override
                    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
                        EvaluationResult evaluationResult = new EvaluationResult();
                        evaluationResult.setRedeemerTag(RedeemerTag.Spend);
                        evaluationResult.setIndex(0);
                        evaluationResult.setExUnits(ExUnits.builder()
                                .mem(BigInteger.valueOf(42061))
                                .steps(BigInteger.valueOf(14890343))
                                .build()
                        );
                        return Result.success("success").withValue(List.of(evaluationResult));
                    }
                })
                .withVerifier(txn -> {
                    System.out.println(JsonUtil.getPrettyJson(txn));
                    assertThat(txn.getBody().getRequiredSigners()).hasSize(1);
                    assertThat(txn.getBody().getRequiredSigners().get(0)) //Verify sender's payment cred hash in required signer
                            .isEqualTo(sender2.getBaseAddress().getPaymentCredentialHash().get());
                })
                .completeAndWait(System.out::println);

        System.out.println(result1);
        assertTrue(result1.isSuccessful());

        checkIfUtxoAvailable(result1.getValue(), sender2Addr);

        // Example of getting the redeemer datum hash and then getting the datum values.
        List<TxContentRedeemers> redeemers = getBackendService().getTransactionService()
                .getTransactionRedeemers(result1.getValue()).getValue();

        long gottenValue = getBackendService().getScriptService()
                .getScriptDatum(redeemers.get(0).getRedeemerDataHash())
                .getValue().getJsonValue().get("int").asLong();
        assertThat(gottenValue).isEqualTo(randInt);
    }

}
