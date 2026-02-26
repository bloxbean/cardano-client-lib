package com.test;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address;
import com.bloxbean.cardano.client.plutus.aiken.blueprint.std.VerificationKeyHash;

/**
 * Test POJO that combines all three field-type families:
 * <ul>
 *   <li>{@code Address} — implements {@code Data<T>} (dataType=true)</li>
 *   <li>{@code VerificationKeyHash} — implements {@code RawData} via ByteArrayWrapper (rawDataType=true)</li>
 *   <li>{@code Model2} — regular {@code @Constr} class (neither dataType nor rawDataType)</li>
 * </ul>
 */
@Constr
public class SharedTypeFieldModel {
    public Address address;
    public VerificationKeyHash vkh;
    public Model2 nested;

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public VerificationKeyHash getVkh() { return vkh; }
    public void setVkh(VerificationKeyHash vkh) { this.vkh = vkh; }

    public Model2 getNested() { return nested; }
    public void setNested(Model2 nested) { this.nested = nested; }
}
