package com.bloxbean.cardano.client.quicktx.serialization;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;

import java.math.BigInteger;
import java.util.List;

/**
 * Placeholder implementation used to defer metadata resolution until variables are applied.
 */
public class PlaceholderMetadata implements Metadata {

    private final String template;

    public PlaceholderMetadata(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Placeholder metadata must be resolved before use");
    }

    @Override
    public Map getData() throws MetadataSerializationException {
        throw unsupported();
    }

    @Override
    public byte[] getMetadataHash() throws MetadataSerializationException {
        throw unsupported();
    }

    @Override
    public byte[] serialize() throws MetadataSerializationException {
        throw unsupported();
    }

    @Override
    public Metadata merge(Metadata metadata1) {
        throw unsupported();
    }

    @Override
    public Metadata put(BigInteger key, BigInteger value) {
        throw unsupported();
    }

    @Override
    public Metadata putNegative(BigInteger key, BigInteger value) {
        throw unsupported();
    }

    @Override
    public Metadata put(BigInteger key, byte[] value) {
        throw unsupported();
    }

    @Override
    public Metadata put(BigInteger key, String value) {
        throw unsupported();
    }

    @Override
    public Metadata put(BigInteger key, MetadataMap mm) {
        throw unsupported();
    }

    @Override
    public Metadata put(BigInteger key, MetadataList list) {
        throw unsupported();
    }

    @Override
    public Object get(BigInteger key) {
        throw unsupported();
    }

    @Override
    public void remove(BigInteger key) {
        throw unsupported();
    }

    @Override
    public List keys() {
        throw unsupported();
    }
}
