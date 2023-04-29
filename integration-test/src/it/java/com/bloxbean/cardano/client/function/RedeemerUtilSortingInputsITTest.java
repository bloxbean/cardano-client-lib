package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.CollateralBuilders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromUtxos;
import static com.bloxbean.cardano.client.function.helper.ScriptCallContextProviders.scriptCallContext;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that the inputs are properly sorted and derived script index in redeemer is correct
 */
public class RedeemerUtilSortingInputsITTest extends BaseITTest {
    BackendService backendService;
    UtxoService utxoService;
    TransactionService transactionService;
    ProtocolParams protocolParams;
    UtxoSupplier utxoSupplier;

    Account sender;
    String senderAddress;

    @BeforeEach
    public void setup() throws ApiException {
        backendService = getBackendService();
        utxoService = backendService.getUtxoService();
        transactionService = backendService.getTransactionService();
        protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        utxoSupplier = new DefaultUtxoSupplier(utxoService);

        //addr_test1qrurqpg3dxvkk4g45negeyaj7gju4mp3napqf36jsek9nw7xdzk39aguj8rcmq3es5ym6z7vgv6xungaypq56r72t2wq9ypt03
        String senderMnemonic = "olive that walk sorry chat leisure attract river adult void host brand student income spy charge roast kiss balcony craft crouch kite agree south";
        sender = new Account(Networks.testnet(), senderMnemonic);
        senderAddress = sender.baseAddress();
    }

    @Test
    public void distributeAndSpend() throws Exception {
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        BigInteger scriptAmt = new BigInteger("2479280");
        String scriptAddress = AddressProvider.getEntAddress(plutusScript, Networks.testnet()).getAddress();
        BigIntPlutusData plutusData = new BigIntPlutusData(BigInteger.valueOf(11));

        String txHash = distributeFund(sender, senderAddress, ADAConversionUtil.adaToLovelace(2), 11, scriptAddress, scriptAmt, plutusData);
        System.out.println("Distribute TxHash: " + txHash);
        String spendTxHash = spend(txHash, 11, scriptAmt, plutusScript, plutusData);
        System.out.println("Spend TxHash: " + spendTxHash);
    }

    private String spend(String txHash, int noOfOutputs, BigInteger scriptAmt, PlutusScript plutusScript, PlutusData plutusData) throws Exception {
        Output output = Output.builder()
                .address(senderAddress)
                .assetName(LOVELACE)
                .qty(ADAConversionUtil.adaToLovelace(3))
                .build();

        List<Utxo> utxos = new ArrayList<>();
        Utxo scriptUtxo = Utxo.builder()
                .txHash(txHash)
                .outputIndex(11)
                .amount(List.of(
                        new Amount(LOVELACE, scriptAmt)
                ))
                .inlineDatum(plutusData.serializeToHex())
                .build();
        for (int i = 3; i < noOfOutputs; i++) {
            Utxo utxo = Utxo.builder()
                    .txHash(txHash)
                    .outputIndex(i)
                    .amount(List.of(new Amount(LOVELACE, ADAConversionUtil.adaToLovelace(2))))
                    .build();
            utxos.add(utxo);

            if (i == 5) { //randomly add script output
                utxos.add(scriptUtxo);
            }
        }

        //Mem and steps are set to 0, as we are going to evaluate those in this test
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(0))
                .steps(BigInteger.valueOf(0)).build();

        TxBuilder txBuilder = output.outputBuilder()
                .buildInputs(createFromUtxos(utxos, senderAddress))
                .andThen(scriptCallContext(plutusScript, scriptUtxo, null, PlutusData.unit(), RedeemerTag.Spend, exUnits))
                .andThen(CollateralBuilders.collateralFrom(List.of(utxos.get(0), utxos.get(1), utxos.get(2))))
                .andThen((context, txn) -> { //Evaluate ExUnits
                    //update estimate ExUnits
                    ExUnits estimatedExUnits;
                    try {
                        estimatedExUnits = evaluateExUnits(txn);
                        txn.getWitnessSet().getRedeemers().get(0).setExUnits(estimatedExUnits);
                    } catch (Exception e) {
                        throw new ApiRuntimeException("Script cost evaluation failed", e);
                    }
                })
                .andThen(balanceTx(senderAddress, 1));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender));

        System.out.println(signedTxn);
        System.out.println(signedTxn.serializeToHex());

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertThat(result.isSuccessful()).isTrue();
        waitForTransaction(result);

        return result.getValue();
    }

    private String distributeFund(Account sender, String recevingAddress, BigInteger amount, int noOfOutputs,
                                  String scriptAddress, BigInteger scriptAmt, PlutusData plutusData) throws CborSerializationException, ApiException {
        TxOutputBuilder txOutputBuilder = (context, outputs) -> {
        };
        for (int i = 0; i < noOfOutputs; i++) {
            txOutputBuilder = txOutputBuilder.and((context, outputs) -> {
                TransactionOutput transactionOutput = TransactionOutput.builder()
                        .address(recevingAddress)
                        .value(Value.builder()
                                .coin(amount)
                                .build()
                        ).build();
                outputs.add(transactionOutput);
            });
        }

        Output output = Output.builder()
                .address(scriptAddress)
                .assetName(LOVELACE)
                .qty(scriptAmt)
                .datum(plutusData)
                .inlineDatum(true)
                .build();
        txOutputBuilder = txOutputBuilder.and(output.outputBuilder());

        TxBuilder txBuilder = txOutputBuilder
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(balanceTx(senderAddress, 1));

        Transaction signedTxn = TxBuilderContext.init(utxoSupplier, protocolParams)
                .buildAndSign(txBuilder, signerFrom(sender));

        System.out.println(signedTxn);
        System.out.printf(signedTxn.serializeToHex());

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTxn.serialize());
        System.out.println(result);
        assertThat(result.isSuccessful()).isTrue();

        waitForTransaction(result);
        return result.getValue();
    }

    private ExUnits evaluateExUnits(Transaction transaction) throws ApiException, CborSerializationException {
        if (backendType.equals(BLOCKFROST)) {
            Result<List<EvaluationResult>> evalResults = transactionService.evaluateTx(transaction.serialize());
            if (evalResults.isSuccessful()) {
                return evalResults.getValue().get(0).getExUnits();
            } else {
                return null;
            }
        } else {
            //Hard coded value for other backend types where evaluateTx is not yet supported
            return ExUnits.builder()
                    .mem(BigInteger.valueOf(4676948))
                    .steps(BigInteger.valueOf(630892334)).build();
        }
    }


    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = backendService.getTransactionService().getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
