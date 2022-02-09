package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxInputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.UtxoUtil;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Provides helper methods to create {@link TxInputBuilder} function to build a list of {@link TransactionInput} from a list of {@link TransactionOutput}
 */
public class InputBuilders {

    public static TxInputBuilder defaultUtxoSelector(String sender, String changeAddress) {

        return ((context, outputs) -> {
            if (outputs == null || outputs.size() == 0)
                throw new TxBuildException("No output found. UtxoSelector transformer should be called after OutputTransformer");

            //Total value required
            Value value = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();
            value = outputs.stream()
                    .map(output -> output.getValue())
                    .reduce(value, (value1, value2) -> value1.plus(value2));

            //Check if mint value is there in context. Then we need to ignore mint outputs from total value
            List<MultiAsset> mintMultiAssets = context.getMintMultiAssets();
            if (mintMultiAssets != null && mintMultiAssets.size() > 0)
                value = value.minus(new Value(BigInteger.ZERO, mintMultiAssets));

            Set<Utxo> utxoSet = getUtxosForValue(context, sender, value, Collections.EMPTY_SET);

            List<TransactionInput> _inputs = utxoSet.stream()
                    .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                    .collect(Collectors.toList());

            //Copy assets to change address
            TransactionOutput changeOutput = new TransactionOutput(changeAddress, new Value(BigInteger.ZERO, new ArrayList<>()));
            utxoSet.stream().forEach(utxo -> UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo));

            //Substract output values from change
            Value changedValue = changeOutput.getValue().minus(value);
            changeOutput.setValue(changedValue);

            BigInteger additionalLovelace = MinAdaCheckers.minAdaChecker().apply(context, changeOutput);

            if (additionalLovelace.compareTo(BigInteger.ZERO) == 1) { //Need more inputs
                Value additionalValue = Value.builder()
                        .coin(additionalLovelace).build();

                Set<Utxo> additionalUtxos = getUtxosForValue(context, sender, additionalValue, utxoSet);

                List<TransactionInput> additionalInputs = additionalUtxos.stream()
                        .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                        .collect(Collectors.toList());

                additionalUtxos.stream().forEach(utxo -> UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo));

                _inputs.addAll(additionalInputs);

            }

            return new Tuple<>(_inputs, List.of(changeOutput));

        });
    }

    private static Set<Utxo> getUtxosForValue(TxBuilderContext context, String sender, Value value, Set<Utxo> excludeUtxos) {
        Set<Utxo> utxoSet = new HashSet<>();

        List<Utxo> lovelaceUtxos;
        try {
            lovelaceUtxos = context.getUtxoSelectionStrategy().selectUtxos(sender, LOVELACE, value.getCoin(), excludeUtxos);
        } catch (ApiException apiException) {
            throw new TxBuildException(apiException);
        }

        List<Utxo> multiAssetUtoxs = value.getMultiAssets().stream()
                .flatMap(multiAsset -> multiAsset.getAssets().stream()
                        .filter(asset -> !asset.getValue().equals(BigInteger.ZERO)) //TODO -- Check if need to remove
                        .map(asset -> new Tuple<String, Asset>(multiAsset.getPolicyId(), asset)))
                .map(tuple -> {
                    String unit = AssetUtil.getUnit(tuple._1, tuple._2);
                    try {
                        List<Utxo> utxoList = (List<Utxo>) context.getUtxoSelectionStrategy().selectUtxos(sender, unit, tuple._2.getValue(), excludeUtxos);
                        if (utxoList != null && utxoList.size() != 0)
                            return utxoList;
                        else
                            throw new ApiRuntimeException(String.format("No utxo found at address=%s, unit= %s, value=%s", sender, unit, tuple._2.getValue()));
                    } catch (ApiException apiException) {
                        throw new TxBuildException("Error fetching utxos for qty: " + tuple._2 + ", and asset: " + tuple._2);
                    }
                })
                .flatMap(list -> list.stream())
                .collect(Collectors.toList());

        utxoSet.addAll(lovelaceUtxos);
        utxoSet.addAll(multiAssetUtoxs);
        return utxoSet;
    }


    public static TxInputBuilder selectorFromUtxos(List<Utxo> utxos, String changeAddress) {
        return selectorFromUtxos(utxos, changeAddress, null);
    }

    public static TxInputBuilder selectorFromUtxos(List<Utxo> utxos, String changeAddress, String datumHash) {
        return (context, outputs) -> {
            //Total value required
            Value value = Value.builder().coin(BigInteger.ZERO).multiAssets(new ArrayList<>()).build();
            value = outputs.stream()
                    .map(output -> output.getValue())
                    .reduce(value, (value1, value2) -> value1.plus(value2));

            List<TransactionInput> _inputs = utxos.stream()
                    .map(utxo -> new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()))
                    .collect(Collectors.toList());

            List<TransactionOutput> changeOutputs = new ArrayList<>();
            if (changeAddress != null && !changeAddress.isEmpty()) {
                //Copy assets to change address
                TransactionOutput changeOutput = new TransactionOutput(changeAddress, new Value(BigInteger.ZERO, new ArrayList<>()));
                utxos.stream().forEach(utxo -> UtxoUtil.copyUtxoValuesToOutput(changeOutput, utxo));

                //Substract output values from change
                Value changedValue = changeOutput.getValue().minus(value);
                changeOutput.setValue(changedValue);

                if (datumHash != null && !datumHash.isEmpty())
                    changeOutput.setDatumHash(HexUtil.decodeHexString(datumHash));

                if (!changeOutput.getValue().getCoin().equals(BigInteger.ZERO) ||
                        (changeOutput.getValue().getMultiAssets() != null && changeOutput.getValue().getMultiAssets().size() > 0)) {
                    changeOutputs.add(changeOutput);
                }
            }

            return new Tuple<>(_inputs, changeOutputs);
        };
    }

    public static TxInputBuilder selectorFromUtxos(List<Utxo> utxos) {
        return (context, outputs) -> {
            //checkTransactionBodyForNull(transaction);

            List<TransactionInput> inputs = new ArrayList<>();
            utxos.forEach(utxo -> {
                System.out.println("SCRIPT UTXO --- " + utxo.getTxHash());

                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                inputs.add(input);
            });

            return new Tuple<>(inputs, Collections.EMPTY_LIST);
        };
    }

    public static TxInputBuilder selectorFromUtxos(Supplier<List<Utxo>> supplier) {
        return (context, outputs) -> {
            //checkTransactionBodyForNull(transaction);

            List<TransactionInput> inputs = new ArrayList<>();
            supplier.get().forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                inputs.add(input);
            });

            return new Tuple<>(inputs, Collections.EMPTY_LIST);
        };
    }

    public static TxBuilder collateralFrom(List<Utxo> utxos) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            utxos.forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transaction.getBody().getCollateral().add(input);
            });
        };
    }

    public static TxBuilder collateralFrom(Supplier<List<Utxo>> supplier) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            supplier.get().forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transaction.getBody().getCollateral().add(input);
            });
        };
    }

    public static TxBuilder collateralFrom(String txHash, int txIndex) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            TransactionInput input = TransactionInput.builder()
                    .transactionId(txHash)
                    .index(txIndex)
                    .build();
            transaction.getBody().getCollateral().add(input);

        };
    }

    private static void checkTransactionBodyForNull(Transaction transaction) {
        if (transaction.getBody() == null)
            transaction.setBody(new TransactionBody());

        if (transaction.getBody().getInputs() == null)
            transaction.getBody().setInputs(new ArrayList<>());

        if (transaction.getBody().getCollateral() == null)
            transaction.getBody().setCollateral(new ArrayList<>());
    }

}
