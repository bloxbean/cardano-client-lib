package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nexus Metadata Service. Unlike Koios, the SDK metadata service supports all 5 methods
 * (by-txHash and by-label, JSON and CBOR), so Nexus gives full bloxbean parity here.
 */
public class NexusMetadataService implements MetadataService {

    private final adlabs.nexus.client.backend.api.metadata.MetadataService metadataService;
    private final Network network;

    public NexusMetadataService(adlabs.nexus.client.backend.api.metadata.MetadataService metadataService, Network network) {
        this.metadataService = metadataService;
        this.network = network;
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByTxnHash(String txnHash) throws ApiException {
        try {
            return NexusResultMapper.map(metadataService.getTxMetadata(network, txnHash),
                    list -> list.stream()
                            .map(m -> new MetadataJSONContent(txnHash, m.getLabel(), m.getJson()))
                            .collect(Collectors.toList()));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByTxnHash(String txnHash) throws ApiException {
        try {
            return NexusResultMapper.map(metadataService.getTxMetadataCbor(network, txnHash),
                    list -> list.stream()
                            .map(m -> new MetadataCBORContent(txnHash, m.getLabel(), m.getCbor()))
                            .collect(Collectors.toList()));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // Nexus SDK has no order param for label listing; order is ignored (as with Nexus utxo/address listings).
    @Override
    public Result<List<MetadataLabel>> getMetadataLabels(int count, int page, OrderEnum order) throws ApiException {
        try {
            return NexusResultMapper.map(metadataService.getMetadataLabels(network, page, count),
                    list -> list.stream()
                            .map(this::toMetadataLabel)
                            .collect(Collectors.toList()));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        try {
            String labelStr = label.toString();
            return NexusResultMapper.map(metadataService.getMetadataByLabel(network, labelStr, page, count),
                    list -> list.stream()
                            .map(m -> new MetadataJSONContent(m.getTxHash(), labelStr, m.getJson()))
                            .collect(Collectors.toList()));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        try {
            String labelStr = label.toString();
            return NexusResultMapper.map(metadataService.getMetadataCborByLabel(network, labelStr, page, count),
                    list -> list.stream()
                            .map(m -> new MetadataCBORContent(m.getTxHash(), labelStr, m.getCbor()))
                            .collect(Collectors.toList()));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // bloxbean MetadataLabel.count is Integer; SDK MetadataLabel.count is Long.
    private MetadataLabel toMetadataLabel(adlabs.nexus.client.backend.api.metadata.model.MetadataLabel m) {
        return new MetadataLabel(m.getLabel(), m.getCip10(), m.getCount() == null ? null : m.getCount().intValue());
    }
}
