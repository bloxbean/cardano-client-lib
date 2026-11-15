package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dijkstra's {@code script_require_guard}, {@code [6, credential]}: holds when the transaction has the credential as a
 * guard. It is written as given in any era, and the ledger judges it (ADR 0004 D1).
 * <p>
 * Experimental: Dijkstra is not final; this may change in any release.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequireGuard implements NativeScript {
    private Credential credential;

    @Override
    public DataItem serializeAsDataItem() {
        Array array = new Array();
        array.add(new UnsignedInteger(6));
        array.add(CredentialSerializer.serialize(credential));
        return array;
    }

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param array the script, {@code [6, credential]}
     * @return the script
     * @throws CborDeserializationException if it is not a {@code script_require_guard}
     */
    public static RequireGuard deserialize(Array array) throws CborDeserializationException {
        return NativeScriptCodec.decode(array, RequireGuard.class);
    }
}
