package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;

import java.util.List;
import java.util.Optional;

/**
 * Test helper that wraps {@link InMemoryJmtStore} and records read counts.
 */
final class TestCountingStore implements JmtStore {

    private final InMemoryJmtStore delegate = new InMemoryJmtStore();

    long nodeLookups;
    long valueLookups;

    @Override
    public Optional<VersionedRoot> latestRoot() {
        return delegate.latestRoot();
    }

    @Override
    public Optional<byte[]> rootHash(long version) {
        return delegate.rootHash(version);
    }

    @Override
    public Optional<NodeEntry> getNode(long version, NibblePath path) {
        nodeLookups++;
        return delegate.getNode(version, path);
    }

    @Override
    public Optional<JmtNode> getNode(NodeKey nodeKey) {
        return delegate.getNode(nodeKey);
    }

    @Override
    public Optional<NodeEntry> floorNode(long version, NibblePath path) {
        nodeLookups++;
        return delegate.floorNode(version, path);
    }

    @Override
    public Optional<byte[]> getValue(byte[] keyHash) {
        valueLookups++;
        return delegate.getValue(keyHash);
    }

    @Override
    public CommitBatch beginCommit(long version, CommitConfig config) {
        return delegate.beginCommit(version, config);
    }

    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
        return delegate.staleNodesUpTo(versionInclusive);
    }

    @Override
    public int pruneUpTo(long versionInclusive) {
        return delegate.pruneUpTo(versionInclusive);
    }

    @Override
    public void close() {
        delegate.close();
    }

    void resetCounters() {
        nodeLookups = 0;
        valueLookups = 0;
    }
}
