package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Integration test POJO covering string-coercible scalar types.
 * The annotation processor generates {@code SampleStringCoercibleMetadataConverter} from this class.
 *
 * <p>Covered types (all use DEFAULT enc — stored as Cardano text):
 * <ul>
 *   <li>{@code URI}      → {@code uri.toString()} / {@code URI.create(String)}</li>
 *   <li>{@code URL}      → {@code url.toString()} / {@code new URL(String)}</li>
 *   <li>{@code UUID}     → {@code uuid.toString()} / {@code UUID.fromString(String)}</li>
 *   <li>{@code Currency} → {@code currency.getCurrencyCode()} / {@code Currency.getInstance(String)}</li>
 *   <li>{@code Locale}   → {@code locale.toLanguageTag()} / {@code Locale.forLanguageTag(String)}</li>
 * </ul>
 */
@MetadataType
public class SampleStringCoercible {

    private URI website;
    private URL endpoint;
    private UUID id;
    private Currency currency;
    private Locale locale;

    public URI getWebsite() { return website; }
    public void setWebsite(URI website) { this.website = website; }

    public URL getEndpoint() { return endpoint; }
    public void setEndpoint(URL endpoint) { this.endpoint = endpoint; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public Locale getLocale() { return locale; }
    public void setLocale(Locale locale) { this.locale = locale; }
}
