package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BaseITTest;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.BalanceTxBuilders;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled
public class PoolRegistrationIT extends BaseITTest {
    ProtocolParams protocolParams;

    @BeforeEach
    public void setup() throws ApiException {
        protocolParams = getBackendService().getEpochService().getProtocolParameters().getValue();
    }

//    @Test
//    void registerPool() throws CborSerializationException, CborDeserializationException, UnknownHostException {
//        String senderMnemonic = "minor duty peace brand idle false force video pattern bulk fringe uncle real quit double maze slam tray rifle curtain clog empower orient admit";
//        Account senderAccount = new Account(Networks.mainnet());
//
//        SecretKey coldKey = new SecretKey("58202e3236f45d42e15996c7becc7b8cd656e3de6df84c730e6c0998f897776cf6af");
//        VerificationKey vKey = new VerificationKey("582006dff473cfc52d754465a9c02f6ebb15ba7ebf568d9d86abcc55d147b3380fe2");
//        System.out.println("vkey: " + HexUtil.encodeHexString(vKey.getBytes()));
//
//        String vrfVkeyCbor = "58404682ed74c2aeb2161ef2be4e65678b6a1f1bdd1074fef8c661db3bfb94a621a2a1e7509d428dffdf4ce0feebae64682eeb6897fb12a8c0198c7458dc049882de";
//
//        PoolRegistration poolRegistration = PoolRegistration.builder()
//                .operator(vKey.getBytes())
//                .vrfKeyHash(KeyGenCborUtil.cborToBytes(vrfVkeyCbor))
//                .pledge(adaToLovelace(10000))
//                .cost(adaToLovelace(500))
//                .margin(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(100)))
//                .rewardAccount(senderAccount.stakeAddress())
//                .poolOwners(Set.of(HexUtil.encodeHexString(senderAccount.getBaseAddress().getBytes())))
//                .relays(List.of(new SingleHostAddr(6000, (Inet4Address) Inet4Address.getByAddress(new byte[]{(byte) 192, (byte) 168, 0, 99}), null)))
//                .poolMetadataHash("6bf124f217d0e5a0a8adb1dbd8540e1334280d49ab861127868339f43b3948af")
//                .poolMetadataUrl("test.com")
//                .build();
//
//        String poolId = HexUtil.encodeHexString(vKey.getBytes());
//        System.out.println(poolId);
//
//        HdKeyPair stakeHdKeyPair = senderAccount.stakeHdKeyPair();
//        byte[] stakePublicKey = stakeHdKeyPair.getPublicKey().getKeyData();
//
//        StakeCredential stakeCredential = StakeCredential.fromKey(stakePublicKey);
//        StakeDelegation stakeDelegation = new StakeDelegation(stakeCredential, StakePoolId.fromHexPoolId(poolId));
//
//        Utxo utxo = Utxo.builder()
//                .txHash("d1b45e78da82b7832a440499c0b1166fb5bf9dee0b7707e6145ac78e73489459")
//                .outputIndex(0)
//                .amount(List.of(new Amount(LOVELACE, BigInteger.valueOf(5000000000000L))))
//                .build();
//
//        TxOutputBuilder txOutBuilder = (context, outputs) -> {
//        };
//
//        TxBuilder builder = txOutBuilder
//                .buildInputs(InputBuilders.createFromUtxos(List.of(utxo), senderAccount.baseAddress()))
//                .andThen((context, txn) -> {
//                    txn.getBody().getCerts().add(poolRegistration);
//                })
//                //.andThen(BalanceTxBuilders.balanceTx(senderAddr, 2));
//
//        SecretKey poolOperatorColdKey = new SecretKey("58202e3236f45d42e15996c7becc7b8cd656e3de6df84c730e6c0998f897776cf6af");
//
//        TxSigner signer = signerFrom(senderAccount)
//                .andThen(signerFrom(poolOperatorColdKey));
//
//        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
//                .buildAndSign(builder, signer);
//
//
//    }

    @Test
    void retirePool() throws CborSerializationException, ApiException {
        String senderMnemonic = "ready tree spawn ozone permit vacuum weasel lunar foster letter income melody chalk cat define lecture seek biology small lesson require artwork exact gorilla";
        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddr = senderAccount.baseAddress();

        UtxoSelectionStrategy selectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(getBackendService().getUtxoService()));

        Set<Utxo> utxos = selectionStrategy.select(senderAddr, new Amount(LOVELACE, adaToLovelace(2)), Collections.EMPTY_SET);

        if (utxos == null || utxos.size() == 0)
            throw new RuntimeException("No utxo found");

        PoolRetirement poolRetirement = PoolRetirement.builder()
                .epoch(227)
                .poolKeyHash(HexUtil.decodeHexString("5b05c8f23150a9b75f438b98ed59ea5f57e66dcec445145d39f9dcd9"))
                .build();
        TxOutputBuilder txOutBuilder = (context, outputs) -> {
        };

        TxBuilder builder = txOutBuilder
                .buildInputs(InputBuilders.createFromUtxos(List.of(utxos.toArray(new Utxo[0])), senderAddr))
                .andThen((context, txn) -> {
                    txn.getBody().getCerts().add(poolRetirement);
                })
                .andThen(BalanceTxBuilders.balanceTx(senderAddr, 2));

        SecretKey poolOperatorColdKey = new SecretKey("58202e3236f45d42e15996c7becc7b8cd656e3de6df84c730e6c0998f897776cf6af");

        TxSigner signer = signerFrom(senderAccount)
                .andThen(signerFrom(poolOperatorColdKey));

        Transaction signedTransaction = TxBuilderContext.init(new DefaultUtxoSupplier(getBackendService().getUtxoService()), protocolParams)
                .buildAndSign(builder, signer);

        Result<String> result = getBackendService().getTransactionService().submitTransaction(signedTransaction.serialize());

        System.out.println(result);
        assertThat(result.isSuccessful()).isEqualTo(true);
        waitForTransaction(result);
    }


    protected void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = getBackendService().getTransactionService()
                            .getTransaction(result.getValue());
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
