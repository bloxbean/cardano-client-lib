package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.SortedSet;

/**
 * Integration test POJO covering {@code SortedSet<T>} field support.
 * The annotation processor generates {@code SampleSortedSetMetadataConverter} from this class.
 *
 * <p>Covered element types: String, BigInteger, Long, Integer, Short, Byte,
 * Boolean, Double, Float, Character, BigDecimal.
 * All of these implement {@link Comparable} so {@link java.util.TreeSet} works correctly.
 *
 * <p>Note: {@code SortedSet<byte[]>} is intentionally absent — {@code byte[]} does not
 * implement {@link Comparable} so a {@code TreeSet<byte[]>} would throw at runtime.
 */
@MetadataType
public class SampleSortedSet {

    private SortedSet<String> tags;
    private SortedSet<BigInteger> amounts;
    private SortedSet<Long> counters;
    private SortedSet<Integer> ids;
    private SortedSet<Short> codes;
    private SortedSet<Byte> byteValues;
    private SortedSet<Boolean> flags;
    private SortedSet<Double> rates;
    private SortedSet<Float> factors;
    private SortedSet<Character> chars;
    private SortedSet<BigDecimal> prices;

    public SortedSet<String> getTags() { return tags; }
    public void setTags(SortedSet<String> tags) { this.tags = tags; }

    public SortedSet<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(SortedSet<BigInteger> amounts) { this.amounts = amounts; }

    public SortedSet<Long> getCounters() { return counters; }
    public void setCounters(SortedSet<Long> counters) { this.counters = counters; }

    public SortedSet<Integer> getIds() { return ids; }
    public void setIds(SortedSet<Integer> ids) { this.ids = ids; }

    public SortedSet<Short> getCodes() { return codes; }
    public void setCodes(SortedSet<Short> codes) { this.codes = codes; }

    public SortedSet<Byte> getByteValues() { return byteValues; }
    public void setByteValues(SortedSet<Byte> byteValues) { this.byteValues = byteValues; }

    public SortedSet<Boolean> getFlags() { return flags; }
    public void setFlags(SortedSet<Boolean> flags) { this.flags = flags; }

    public SortedSet<Double> getRates() { return rates; }
    public void setRates(SortedSet<Double> rates) { this.rates = rates; }

    public SortedSet<Float> getFactors() { return factors; }
    public void setFactors(SortedSet<Float> factors) { this.factors = factors; }

    public SortedSet<Character> getChars() { return chars; }
    public void setChars(SortedSet<Character> chars) { this.chars = chars; }

    public SortedSet<BigDecimal> getPrices() { return prices; }
    public void setPrices(SortedSet<BigDecimal> prices) { this.prices = prices; }
}
