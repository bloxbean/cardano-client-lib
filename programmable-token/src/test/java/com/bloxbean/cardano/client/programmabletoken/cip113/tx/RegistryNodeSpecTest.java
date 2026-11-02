package com.bloxbean.cardano.client.programmabletoken.cip113.tx;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Exception;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RegistryNodeSpec#validate()} mirrors what {@code registry_mint} and
 * {@code linked_list.ak:is_inserted_directory_node} reject on chain. Catching these at build time
 * turns a script failure at submit into a message that says which field is wrong.
 */
class RegistryNodeSpecTest {

    private static final Credential SCRIPT =
            Credential.fromScript("4ab26c95029067185f709d140300cccb15b0b20bbd62a7e9aa2e2e10");
    private static final Credential KEY =
            Credential.fromKey("11111111111111111111111111111111111111111111111111111111");

    @Test
    void acceptsAWellFormedSpec() {
        assertThatCode(() -> valid().build().validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsAKeyCredentialForIssuance() {
        // The policy id is derived from this credential's hash and the on-chain check is
        // `expect Script(hashed_param)`, so a key credential can never be registered.
        assertThatThrownBy(() -> valid().mintingLogicScript(KEY).build().validate())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("must be a Script credential");
    }

    @Test
    void rejectsEmptyLogicCredentials() {
        // Empty credentials are valid only on the registry's origin node.
        Credential empty = Credential.fromKey(new byte[0]);

        assertThatThrownBy(() -> valid().transferLogicScript(empty).build().validate())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("transferLogicScript must be a 28-byte credential");

        assertThatThrownBy(() -> valid().thirdPartyTransferLogicScript(empty).build().validate())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("thirdPartyTransferLogicScript must be a 28-byte credential");
    }

    @Test
    void rejectsAMalformedGlobalStatePolicy() {
        assertThatThrownBy(() -> valid().globalStateCs("abcd").build().validate())
                .isInstanceOf(Cip113Exception.class)
                .hasMessageContaining("28-byte policy id");
    }

    @Test
    void allowsNoGlobalState() {
        assertThatCode(() -> valid().globalStateCs(null).build().validate()).doesNotThrowAnyException();
        assertThatCode(() -> valid().globalStateCs("").build().validate()).doesNotThrowAnyException();
    }

    @Test
    void unfrackingDefaultsToForbidden() {
        RegistryNodeSpec spec = valid().build();
        assertThat(spec.getUnfrackingLogicScript().getType()).isEqualTo(CredentialType.Key);
        assertThat(spec.getUnfrackingLogicScript().getBytes()).isEmpty();
    }

    private static RegistryNodeSpec.RegistryNodeSpecBuilder valid() {
        return RegistryNodeSpec.builder()
                .mintingLogicScript(SCRIPT)
                .transferLogicScript(SCRIPT)
                .thirdPartyTransferLogicScript(SCRIPT);
    }
}
