package com.bloxbean.cardano.client.backend.nexus;
import java.util.Collections;
import java.util.List;

public final class NexusPagination {
    private NexusPagination() {}
    public static <T> List<T> subList(List<T> full, int count, int page) {
        if (full == null || full.isEmpty() || page < 1 || count < 1) return Collections.emptyList();
        int from = (page - 1) * count;
        if (from >= full.size()) return Collections.emptyList();
        int to = Math.min(from + count, full.size());
        return full.subList(from, to);
    }
}
