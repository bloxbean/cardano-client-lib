package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

/**
 * Nested POJO for testing {@code @MetadataType} composition.
 */
@MetadataType
public class SampleNestedAddress {

    private String street;
    private String city;
    private String zip;

    public SampleNestedAddress() {}

    public SampleNestedAddress(String street, String city, String zip) {
        this.street = street;
        this.city = city;
        this.zip = zip;
    }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }
}
