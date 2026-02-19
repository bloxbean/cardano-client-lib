package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.SortedSet;

/**
 * Integration test POJO covering {@code SortedSet<T>} field support.
 * The annotation processor generates {@code SampleSortedSetMetadataConverter} from this class.
 *
 * Note: {@code SortedSet<byte[]>} is intentionally absent — {@code byte[]} does not
 * implement {@link Comparable} so a {@code TreeSet<byte[]>} would throw at runtime.
 */
@MetadataType
public class SampleSortedSet {

    private SortedSet<String> tags;
    private SortedSet<BigInteger> amounts;
    private SortedSet<Integer> ids;
    private SortedSet<Boolean> flags;
    private SortedSet<BigDecimal> prices;

    public SortedSet<String> getTags() { return tags; }
    public void setTags(SortedSet<String> tags) { this.tags = tags; }

    public SortedSet<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(SortedSet<BigInteger> amounts) { this.amounts = amounts; }

    public SortedSet<Integer> getIds() { return ids; }
    public void setIds(SortedSet<Integer> ids) { this.ids = ids; }

    public SortedSet<Boolean> getFlags() { return flags; }
    public void setFlags(SortedSet<Boolean> flags) { this.flags = flags; }

    public SortedSet<BigDecimal> getPrices() { return prices; }
    public void setPrices(SortedSet<BigDecimal> prices) { this.prices = prices; }
}
