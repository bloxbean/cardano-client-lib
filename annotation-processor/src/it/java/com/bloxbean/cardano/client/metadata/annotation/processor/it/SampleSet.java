package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/**
 * Integration test POJO covering {@code Set<T>} field support.
 * The annotation processor generates {@code SampleSetMetadataConverter} from this class.
 *
 * <p>Covered element types: String, BigInteger, Long, Integer, Short, Byte,
 * Boolean, Double, Float, Character, BigDecimal, byte[].
 */
@MetadataType
public class SampleSet {

    private Set<String> tags;
    private Set<BigInteger> amounts;
    private Set<Long> counters;
    private Set<Integer> ids;
    private Set<Short> codes;
    private Set<Byte> byteValues;
    private Set<Boolean> flags;
    private Set<Double> rates;
    private Set<Float> factors;
    private Set<Character> chars;
    private Set<BigDecimal> prices;
    private Set<byte[]> payloads;

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Set<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(Set<BigInteger> amounts) { this.amounts = amounts; }

    public Set<Long> getCounters() { return counters; }
    public void setCounters(Set<Long> counters) { this.counters = counters; }

    public Set<Integer> getIds() { return ids; }
    public void setIds(Set<Integer> ids) { this.ids = ids; }

    public Set<Short> getCodes() { return codes; }
    public void setCodes(Set<Short> codes) { this.codes = codes; }

    public Set<Byte> getByteValues() { return byteValues; }
    public void setByteValues(Set<Byte> byteValues) { this.byteValues = byteValues; }

    public Set<Boolean> getFlags() { return flags; }
    public void setFlags(Set<Boolean> flags) { this.flags = flags; }

    public Set<Double> getRates() { return rates; }
    public void setRates(Set<Double> rates) { this.rates = rates; }

    public Set<Float> getFactors() { return factors; }
    public void setFactors(Set<Float> factors) { this.factors = factors; }

    public Set<Character> getChars() { return chars; }
    public void setChars(Set<Character> chars) { this.chars = chars; }

    public Set<BigDecimal> getPrices() { return prices; }
    public void setPrices(Set<BigDecimal> prices) { this.prices = prices; }

    public Set<byte[]> getPayloads() { return payloads; }
    public void setPayloads(Set<byte[]> payloads) { this.payloads = payloads; }
}
