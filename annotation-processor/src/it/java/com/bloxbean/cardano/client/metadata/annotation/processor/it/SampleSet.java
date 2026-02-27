package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/**
 * Integration test POJO covering {@code Set<T>} field support.
 * The annotation processor generates {@code SampleSetMetadataConverter} from this class.
 */
@MetadataType
public class SampleSet {

    private Set<String> tags;
    private Set<BigInteger> amounts;
    private Set<Integer> ids;
    private Set<Boolean> flags;
    private Set<BigDecimal> prices;
    private Set<byte[]> payloads;

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Set<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(Set<BigInteger> amounts) { this.amounts = amounts; }

    public Set<Integer> getIds() { return ids; }
    public void setIds(Set<Integer> ids) { this.ids = ids; }

    public Set<Boolean> getFlags() { return flags; }
    public void setFlags(Set<Boolean> flags) { this.flags = flags; }

    public Set<BigDecimal> getPrices() { return prices; }
    public void setPrices(Set<BigDecimal> prices) { this.prices = prices; }

    public Set<byte[]> getPayloads() { return payloads; }
    public void setPayloads(Set<byte[]> payloads) { this.payloads = payloads; }
}
