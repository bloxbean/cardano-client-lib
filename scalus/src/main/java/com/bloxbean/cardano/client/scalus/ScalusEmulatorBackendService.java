package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.scalus.bridge.EmulatorBridge;
import com.bloxbean.cardano.client.scalus.bridge.EmulatorHandle;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigBridge;
import com.bloxbean.cardano.client.scalus.bridge.SlotConfigHandle;
import com.bloxbean.cardano.client.scalus.bridge.SubmitResult;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link BackendService} implementation backed by the Scalus in-memory emulator
 * via the scalus-bridge.
 * <p>
 * This backend provides deterministic, fast, fully-offline transaction processing
 * with complete Cardano ledger rule enforcement. It's ideal for unit testing and
 * local development without requiring a running Cardano node.
 * <p>
 * Usage:
 * <pre>
 * var backend = ScalusEmulatorBackendService.builder()
 *     .protocolParams(protocolParams)
 *     .slotConfig(SlotConfigBridge.preview())
 *     .initialFunds(Map.of(testAddr, Amount.ada(1000)))
 *     .build();
 *
 * QuickTxBuilder builder = new QuickTxBuilder(backend);
 * var result = builder.compose(tx).withSigner(signer).completeAndWait();
 * </pre>
 */
@Slf4j
public class ScalusEmulatorBackendService implements BackendService {

    private final EmulatorHandle emulatorHandle;
    private final ProtocolParams protocolParams;

    // Track submitted transaction hashes for querying
    private final Set<String> submittedTxHashes = new LinkedHashSet<>();

    // Cached service instances
    private final TransactionService transactionService;
    private final UtxoService utxoService;
    private final EpochService epochService;
    private final BlockService blockService;

    @Builder
    public ScalusEmulatorBackendService(
            ProtocolParams protocolParams,
            SlotConfigHandle slotConfig,
            Map<String, Amount> initialFunds,
            int networkId
    ) {
        this.protocolParams = protocolParams != null ? protocolParams : buildDefaultProtocolParams();
        SlotConfigHandle sc = slotConfig != null ? slotConfig : SlotConfigBridge.preview();
        Map<String, Amount> funds = initialFunds != null ? initialFunds : Map.of();

        this.emulatorHandle = EmulatorBridge.create(this.protocolParams, sc, funds, networkId);

        this.transactionService = new EmulatorTransactionService();
        this.utxoService = new EmulatorUtxoService();
        this.epochService = new EmulatorEpochService();
        this.blockService = new EmulatorBlockService();
    }

    @Override
    public TransactionService getTransactionService() {
        return transactionService;
    }

    @Override
    public UtxoService getUtxoService() {
        return utxoService;
    }

    @Override
    public EpochService getEpochService() {
        return epochService;
    }

    @Override
    public AssetService getAssetService() {
        throw new UnsupportedOperationException("AssetService not supported by emulator backend");
    }

    @Override
    public BlockService getBlockService() {
        return blockService;
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        throw new UnsupportedOperationException("NetworkInfoService not supported by emulator backend");
    }

    @Override
    public PoolService getPoolService() {
        throw new UnsupportedOperationException("PoolService not supported by emulator backend");
    }

    @Override
    public AddressService getAddressService() {
        throw new UnsupportedOperationException("AddressService not supported by emulator backend");
    }

    @Override
    public AccountService getAccountService() {
        throw new UnsupportedOperationException("AccountService not supported by emulator backend");
    }

    @Override
    public MetadataService getMetadataService() {
        throw new UnsupportedOperationException("MetadataService not supported by emulator backend");
    }

    @Override
    public ScriptService getScriptService() {
        throw new UnsupportedOperationException("ScriptService not supported by emulator backend");
    }

    /**
     * Advance the emulator's slot.
     */
    public void advanceSlot(long slot) {
        EmulatorBridge.setSlot(emulatorHandle, slot);
    }

    /**
     * Get a snapshot of the emulator for rollback testing.
     */
    public EmulatorHandle snapshot() {
        return EmulatorBridge.snapshot(emulatorHandle);
    }

    private ProtocolParams buildDefaultProtocolParams() {
        return ProtocolParams.builder()
                .minFeeA(44).minFeeB(155381)
                .maxBlockSize(90112).maxTxSize(16384).maxBlockHeaderSize(1100)
                .keyDeposit("2000000").poolDeposit("500000000")
                .eMax(18).nOpt(500)
                .a0(java.math.BigDecimal.valueOf(0.3))
                .rho(java.math.BigDecimal.valueOf(0.003))
                .tau(java.math.BigDecimal.valueOf(0.2))
                .protocolMajorVer(10).protocolMinorVer(0)
                .minPoolCost("170000000").coinsPerUtxoSize("4310")
                .priceMem(java.math.BigDecimal.valueOf(0.0577))
                .priceStep(java.math.BigDecimal.valueOf(0.0000721))
                .maxTxExMem("14000000").maxTxExSteps("10000000000")
                .maxBlockExMem("62000000").maxBlockExSteps("20000000000")
                .maxValSize("5000")
                .collateralPercent(java.math.BigDecimal.valueOf(150))
                .maxCollateralInputs(3)
                .costModels(new LinkedHashMap<>())
                .build();
    }

    // ---- Inner service implementations ----

    private class EmulatorTransactionService implements TransactionService {

        @Override
        @SuppressWarnings("unchecked")
        public Result<String> submitTransaction(byte[] cborData) throws ApiException {
            try {
                SubmitResult result = EmulatorBridge.submit(emulatorHandle, cborData, protocolParams);

                if (result.isSuccess()) {
                    String txHash = result.txHash();
                    submittedTxHashes.add(txHash);
                    return (Result<String>) Result.success(txHash).withValue(txHash);
                } else {
                    return Result.error("Transaction submission failed: " + result.errorMessage());
                }
            } catch (Exception e) {
                return Result.error("Failed to submit transaction: " + e.getMessage());
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
            if (submittedTxHashes.contains(txnHash)) {
                TransactionContent content = new TransactionContent();
                content.setHash(txnHash);
                return (Result<TransactionContent>) Result.success("").withValue(content);
            }
            return Result.error("Transaction not found: " + txnHash);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
            List<TransactionContent> results = new ArrayList<>();
            for (String hash : txnHashCollection) {
                if (submittedTxHashes.contains(hash)) {
                    TransactionContent content = new TransactionContent();
                    content.setHash(hash);
                    results.add(content);
                }
            }
            return (Result<List<TransactionContent>>) Result.success("").withValue(results);
        }

        @Override
        public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
            throw new UnsupportedOperationException("getTransactionUtxos not supported by emulator");
        }

        @Override
        public Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash) throws ApiException {
            throw new UnsupportedOperationException("getTransactionRedeemers not supported by emulator");
        }
    }

    private class EmulatorUtxoService implements UtxoService {

        @Override
        public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException {
            return getUtxos(address, count, page, OrderEnum.asc);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException {
            try {
                List<Utxo> allUtxos = EmulatorBridge.getUtxos(emulatorHandle, address);

                // Apply pagination
                int start = page * count;
                int end = Math.min(start + count, allUtxos.size());
                List<Utxo> pagedResult = start < allUtxos.size()
                        ? allUtxos.subList(start, end)
                        : Collections.emptyList();

                return (Result<List<Utxo>>) Result.success("").withValue(pagedResult);
            } catch (Exception e) {
                throw new ApiException("Failed to get UTxOs: " + e.getMessage(), e);
            }
        }

        @Override
        public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page) throws ApiException {
            return getUtxos(address, unit, count, page, OrderEnum.asc);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<List<Utxo>> getUtxos(String address, String unit, int count, int page, OrderEnum order) throws ApiException {
            try {
                List<Utxo> allUtxos = EmulatorBridge.getUtxos(emulatorHandle, address);

                // Filter by unit first, then paginate
                List<Utxo> filtered = allUtxos.stream()
                        .filter(utxo -> utxo.getAmount().stream().anyMatch(a -> a.getUnit().equals(unit)))
                        .collect(Collectors.toList());

                int start = page * count;
                int end = Math.min(start + count, filtered.size());
                List<Utxo> pagedResult = start < filtered.size()
                        ? filtered.subList(start, end)
                        : Collections.emptyList();

                return (Result<List<Utxo>>) Result.success("").withValue(pagedResult);
            } catch (Exception e) {
                throw new ApiException("Failed to get UTxOs: " + e.getMessage(), e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException {
            try {
                Optional<Utxo> utxoOpt = EmulatorBridge.getTxOutput(emulatorHandle, txHash, outputIndex);

                if (utxoOpt.isPresent()) {
                    return (Result<Utxo>) Result.success("").withValue(utxoOpt.get());
                }
                return Result.error("UTxO not found: " + txHash + "#" + outputIndex);
            } catch (Exception e) {
                throw new ApiException("Failed to get tx output: " + e.getMessage(), e);
            }
        }
    }

    private class EmulatorEpochService implements EpochService {

        @Override
        @SuppressWarnings("unchecked")
        public Result<EpochContent> getLatestEpoch() throws ApiException {
            EpochContent epoch = new EpochContent();
            return (Result<EpochContent>) Result.success("").withValue(epoch);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
            EpochContent content = new EpochContent();
            return (Result<EpochContent>) Result.success("").withValue(content);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
            return (Result<ProtocolParams>) Result.success("").withValue(protocolParams);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<ProtocolParams> getProtocolParameters() throws ApiException {
            return (Result<ProtocolParams>) Result.success("").withValue(protocolParams);
        }
    }

    private class EmulatorBlockService implements BlockService {

        @Override
        @SuppressWarnings("unchecked")
        public Result<Block> getLatestBlock() throws ApiException {
            Block block = new Block();
            return (Result<Block>) Result.success("").withValue(block);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<Block> getBlockByHash(String blockHash) throws ApiException {
            Block block = new Block();
            return (Result<Block>) Result.success("").withValue(block);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException {
            Block block = new Block();
            return (Result<Block>) Result.success("").withValue(block);
        }
    }
}
