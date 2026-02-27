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
 *   <li>{@code List<Integer>}    → MetadataList of integers (via BigInteger.valueOf)</li>
 *   <li>{@code List<Boolean>}    → MetadataList of BigInteger 0/1</li>
 *   <li>{@code List<Double>}     → MetadataList of text (via String.valueOf)</li>
 *   <li>{@code List<BigDecimal>} → MetadataList of text (via toPlainString)</li>
 *   <li>{@code List<byte[]>}     → MetadataList of bytes</li>
 * </ul>
 */
@MetadataType
public class SampleList {

    private List<String> tags;
    private List<BigInteger> amounts;
    private List<Integer> ids;
    private List<Boolean> flags;
    private List<Double> rates;
    private List<BigDecimal> prices;
    private List<byte[]> payloads;

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<BigInteger> getAmounts() { return amounts; }
    public void setAmounts(List<BigInteger> amounts) { this.amounts = amounts; }

    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }

    public List<Boolean> getFlags() { return flags; }
    public void setFlags(List<Boolean> flags) { this.flags = flags; }

    public List<Double> getRates() { return rates; }
    public void setRates(List<Double> rates) { this.rates = rates; }

    public List<BigDecimal> getPrices() { return prices; }
    public void setPrices(List<BigDecimal> prices) { this.prices = prices; }

    public List<byte[]> getPayloads() { return payloads; }
    public void setPayloads(List<byte[]> payloads) { this.payloads = payloads; }
}
