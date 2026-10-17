package com.bloxbean.cardano.client.txflow.testsupport;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.signing.DefaultSignerRegistry;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.model.TransactionInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mocked chain that lets real QuickTx builds run under TxFlow: the sender holds funds, every
 * submission is accepted and immediately included, and "protocol" addresses can be seeded with
 * confirmed outputs.
 */
public final class FakeChain {
    /** Fixed test account; its base address is the funded sender, signing as {@link #SIGNER}. */
    public static final Account ACCOUNT = new Account(Networks.testnet(),
            "test test test test test test test test test test test test test test test test test test test test test test test sauce");
    public static final String SENDER = ACCOUNT.baseAddress();
    public static final String SIGNER = "account://sender";
    public static final String RECEIVER = address("22");
    public static final String PROTOCOL = address("33");

    public final UtxoSupplier utxoSupplier = mock(UtxoSupplier.class);
    public final ProtocolParamsSupplier protocolParamsSupplier = mock(ProtocolParamsSupplier.class);
    public final TransactionProcessor transactionProcessor = mock(TransactionProcessor.class);
    public final ChainDataSupplier chainDataSupplier = mock(ChainDataSupplier.class);
    public final DefaultSignerRegistry signers = new DefaultSignerRegistry().addAccount(SIGNER, ACCOUNT);
    public final List<String> submitted = new CopyOnWriteArrayList<>();
    private final Map<String, List<Utxo>> confirmed = new ConcurrentHashMap<>();
    /** When set, a submitted transaction's outputs are immediately visible by exact lookup. */
    public volatile boolean confirmOnSubmit;

    public FakeChain() {
        confirmed.put(SENDER, List.of(utxo(SENDER, "7e1eecf7439fb5119a6762985a61c9fb3ca8158d9fc38361f0c4746430d5e0c7",
                0, 1_000)));
        when(utxoSupplier.getPage(anyString(), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            Integer page = invocation.getArgument(2);
            return page != null && page > 1 ? List.of()
                    : confirmed.getOrDefault((String) invocation.getArgument(0), List.of());
        });
        when(utxoSupplier.getAll(anyString())).thenAnswer(invocation ->
                new ArrayList<>(confirmed.getOrDefault((String) invocation.getArgument(0), List.of())));
        when(utxoSupplier.getTxOutput(anyString(), anyInt())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            int index = invocation.getArgument(1);
            Optional<Utxo> found = confirmed.values().stream().flatMap(List::stream)
                    .filter(utxo -> utxo.getTxHash().equals(hash) && utxo.getOutputIndex() == index)
                    .findFirst();
            return found.isEmpty() && confirmOnSubmit && submitted.contains(hash)
                    ? Optional.of(utxo(SENDER, hash, index, 1)) : found;
        });
        when(protocolParamsSupplier.getProtocolParams()).thenReturn(ProtocolParams.builder()
                .minFeeA(44).minFeeB(155381).minUtxo("1000000").coinsPerUtxoSize("4312")
                .maxTxSize(16384).minFeeRefScriptCostPerByte(BigDecimal.valueOf(15))
                .build());
        try {
            when(transactionProcessor.submitTransaction(any(byte[].class))).thenAnswer(invocation -> {
                String hash = TransactionUtil.getTxHash((byte[]) invocation.getArgument(0));
                submitted.add(hash);
                Result<String> result = Result.success("submitted");
                result.withValue(hash);
                return result;
            });
            when(chainDataSupplier.getChainTipHeight()).thenReturn(100L);
            when(chainDataSupplier.getTransactionInfo(anyString())).thenAnswer(invocation ->
                    Optional.of(TransactionInfo.builder().txHash(invocation.getArgument(0))
                            .blockHeight(10L).build()));
        } catch (ApiException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Seeds confirmed outputs at an address; returns the seeded output. */
    public Utxo seed(String address, String txHash, int index, long ada) {
        Utxo utxo = utxo(address, txHash, index, ada);
        List<Utxo> list = new ArrayList<>(confirmed.getOrDefault(address, List.of()));
        list.add(utxo);
        confirmed.put(address, List.copyOf(list));
        return utxo;
    }

    private static Utxo utxo(String address, String txHash, int index, long ada) {
        return Utxo.builder().address(address).txHash(txHash).outputIndex(index)
                .amount(List.of(Amount.ada(ada))).build();
    }

    private static String address(String keyByte) {
        return AddressProvider.getEntAddress(Credential.fromKey(keyByte.repeat(28)), Networks.testnet())
                .toBech32();
    }
}
