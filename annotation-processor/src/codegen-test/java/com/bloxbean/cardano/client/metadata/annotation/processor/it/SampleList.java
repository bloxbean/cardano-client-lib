package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Integration test POJO covering {@code List<T>} field support.
 * The annotation processor generates {@code SampleListMetadataConverter} from this class.
 *
 * <p>Covered element types:
 * <ul>
 *   <li>{@code List<String>}     → MetadataList of text (sub-list for long strings)</li>
 *   <li>{@code List<BigInteger>} → MetadataList of integers</li>
 *   <li>{@code List<Long>}       → MetadataList of integers (via BigInteger.valueOf)</li>
 *   <li>{@code List<Integer>}    → MetadataList of integers (via BigInteger.valueOf)</li>
 *   <li>{@code List<Short>}      → MetadataList of integers (via BigInteger.valueOf)</li>
 *   <li>{@code List<Byte>}       → MetadataList of integers (via BigInteger.valueOf)</li>
 *   <li>{@code List<Boolean>}    → MetadataList of BigInteger 0/1</li>
 *   <li>{@code List<Double>}     → MetadataList of text (via String.valueOf)</li>
 *   <li>{@code List<Float>}      → MetadataList of text (via String.valueOf)</li>
 *   <li>{@code List<Character>}  → MetadataList of text (via String.valueOf)</li>
 *   <li>{@code List<BigDecimal>} → MetadataList of text (via toPlainString)</li>
 *   <li>{@code List<byte[]>}     → MetadataList of bytes</li>
 * </ul>
 */
@MetadataType
public class SampleList {

    private List<String> tags;
    private List<BigInteger> amounts;
    private List<Long> counters;
    private List<Integer> ids;
    private List<Short> codes;
    private List<Byte> byteValues;
    private List<Boolean> flags;
    private List<Double> rates;
    private List<Float> factors;
    private List<Character> chars;
    private List<BigDecimal> prices;
    private List<byte[]> payloads;

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(List<BigInteger> amounts) { this.amounts = amounts; }

    public List<Long> getCounters() { return counters; }
    public void setCounters(List<Long> counters) { this.counters = counters; }

    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }

    public List<Short> getCodes() { return codes; }
    public void setCodes(List<Short> codes) { this.codes = codes; }

    public List<Byte> getByteValues() { return byteValues; }
    public void setByteValues(List<Byte> byteValues) { this.byteValues = byteValues; }

    public List<Boolean> getFlags() { return flags; }
    public void setFlags(List<Boolean> flags) { this.flags = flags; }

    public List<Double> getRates() { return rates; }
    public void setRates(List<Double> rates) { this.rates = rates; }

    public List<Float> getFactors() { return factors; }
    public void setFactors(List<Float> factors) { this.factors = factors; }

    public List<Character> getChars() { return chars; }
    public void setChars(List<Character> chars) { this.chars = chars; }

    public List<BigDecimal> getPrices() { return prices; }
    public void setPrices(List<BigDecimal> prices) { this.prices = prices; }

    public List<byte[]> getPayloads() { return payloads; }
    public void setPayloads(List<byte[]> payloads) { this.payloads = payloads; }
}
