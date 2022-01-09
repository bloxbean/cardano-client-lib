package com.bloxbean.cardano.client.transaction.spec;

import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Policy {

    //Optional - name distinguishes between multiple Policies in a project
    private String name;
    private NativeScript policyScript;
    private List<SecretKey> policyKeys;

    public Policy(String name, NativeScript policyScript) {
        this.name = name;
        this.policyScript = policyScript;
    }

    public Policy(NativeScript policyScript) {
        this.policyScript = policyScript;
    }

    public Policy(NativeScript policyScript, List<SecretKey> policyKeys) {
        this.policyScript = policyScript;
        this.policyKeys = policyKeys;
    }

    public Policy addKey(SecretKey key) {
        if (policyKeys == null) {
            policyKeys = new ArrayList<>();
        }
        policyKeys.add(key);
        return this;
    }

    @JsonIgnore
    public String getPolicyId() throws CborSerializationException {
        return policyScript.getPolicyId();
    }
}