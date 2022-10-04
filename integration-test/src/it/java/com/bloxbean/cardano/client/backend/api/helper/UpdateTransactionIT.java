package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BaseITTest;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.ProtocolParamUpdate;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.assertj.core.api.Assertions.assertThat;

public class UpdateTransactionIT extends BaseITTest {

    ProtocolParams protocolParams;
    String senderMnemonic;
    Account senderAccount;
    String senderAddress;

    @BeforeEach
    public void setup() throws ApiException {
        protocolParams = getBackendService().getEpochService().getProtocolParameters().getValue();

        senderMnemonic = "song dignity manage hub picture rival thumb gain picture leave rich axis eight scheme coral vendor guard paper report come cat draw educate group";
        senderAccount = new Account(Networks.testnet(), senderMnemonic);
        senderAddress = senderAccount.baseAddress();
    }

    @Test
    //This test verifies that the right error message is thrown while submitting an update transaction as it's not possible to submit protocol update txn to a
    //public network.
    void updateProtocolParamTest() throws ApiException, CborSerializationException {
        SecretKey gdsk1 = new SecretKey("5820bb55053d8144555f1ec4a8ec63905b35d1081b52fbcea896c10c6f641780b5c9"); //genesis delegate key
        SecretKey gdsk2 = new SecretKey("5820e5db1345e3fc7d45975cbd0415d3398d36270fcbe94d57dddab4c38a81ed7b8b");
        SecretKey gdsk3 = new SecretKey("582043df262e2aff4d84f852afc45e1cf6d9f363063831c568def88bf0cff48818f7");

        //select an utxo to pay transaction fee
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(getBackendService().getUtxoService());
        UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        Set<Utxo> utxoSet = utxoSelectionStrategy.select(senderAddress, new Amount(LOVELACE, adaToLovelace(4)),  Collections.emptySet());

        //Get current epoch
        int epoch = getBackendService().getEpochService().getLatestEpoch().getValue().getEpoch();

        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        String genesisHash1 = "E34A75849B978BB20C366F531F978B3111B91AAE0E469108DCA8E433";
        String genesisHash2 = "337BC5EF0F1ABF205624555C13A37258C42B46B1259A6B1A6D82574E";
        String genesisHash3 = "B5C3FED76C54BFDCDA7993E4171EFF2FB853F8AB920AB80047278A91";

        ProtocolParamUpdate protocolParamUpdate = ProtocolParamUpdate.builder()
                .maxTxSize(Integer.valueOf(20384))
                .maxCollateralInputs(Integer.valueOf(2)).build();


        Update.UpdateBuilder builder = Update.builder();
        builder.epoch(epoch);
        Update update = builder
                .build();
        update.addProtocolParameterUpdate(genesisHash1, protocolParamUpdate);
        update.addProtocolParameterUpdate(genesisHash2, protocolParamUpdate);
        update.addProtocolParameterUpdate(genesisHash3, protocolParamUpdate);

        TxBuilder txBuilder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(new ArrayList<>(utxoSet), senderAddress))
                .andThen((context, txn) -> {
                    txn.getBody().setUpdate(update);
                })
                .andThen(balanceTx(senderAddress, 4));

        TxSigner signer = signerFrom(senderAccount).andThen(signerFrom(gdsk1))
                .andThen(signerFrom(gdsk2))
                .andThen(signerFrom(gdsk3));

        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, new DefaultProtocolParamsSupplier(getBackendService().getEpochService()))
                .buildAndSign(txBuilder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponse()).contains("NonGenesisUpdatePPUP");
    }

}
