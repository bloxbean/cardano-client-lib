package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Serializable credential value used by programmable-token semantic intents. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammableTokenCredential {
    private CredentialType type;
    private String hash;

    public static ProgrammableTokenCredential from(Credential credential) {
        if (credential == null) return null;
        return new ProgrammableTokenCredential(credential.getType(),
                HexUtil.encodeHexString(credential.getBytes()));
    }

    public Credential toCredential() {
        validate();
        byte[] bytes = HexUtil.decodeHexString(hash);
        return type == CredentialType.Script
                ? Credential.fromScript(bytes) : Credential.fromKey(bytes);
    }

    public void validate() {
        if (type == null) throw new IllegalStateException("credential type is required");
        if (hash == null) throw new IllegalStateException("credential hash is required");
        try {
            HexUtil.decodeHexString(hash);
        } catch (RuntimeException e) {
            throw new IllegalStateException("credential hash must be hexadecimal", e);
        }
    }
}
