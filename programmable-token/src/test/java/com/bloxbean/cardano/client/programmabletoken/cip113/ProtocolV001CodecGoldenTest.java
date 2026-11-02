package com.bloxbean.cardano.client.programmabletoken.cip113;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.Cip113Redeemers;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.ProgrammableLogicGlobalParams;
import com.bloxbean.cardano.client.programmabletoken.cip113.model.RegistryNode;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Byte-for-byte agreement with an independent CIP-113 v0.0.1 implementation.
 *
 * <p>Every expected value below was produced by the cip113-programmable-tokens-platform backend's
 * encoders ({@code core/CoreRedeemers}, {@code core/CoreProgrammableLogicGlobalParams},
 * {@code model/onchain/RegistryNode}) — the off-chain code that builds this protocol's
 * transactions on mainnet — from the same inputs, on 2026-10-22. {@code BlueprintCodecAgreementTest}
 * checks constructor indices and field order against the blueprint; this checks the actual bytes
 * against code that the deployed validators have already accepted.</p>
 */
class ProtocolV001CodecGoldenTest {

    @Test
    void baseSpendRedeemer() {
        // CoreRedeemers.baseSpend(3, 1)
        assertBytes(Cip113Redeemers.baseSpend(3, 1), "d8799f0301ff");
    }

    @Test
    void dispatcherActs() {
        // CoreRedeemers.dispatchTransfer() / dispatchThirdParty() / dispatchUnfracking()
        assertBytes(Cip113Redeemers.transferAct(), "d87980");
        assertBytes(Cip113Redeemers.thirdPartyAct(), "d87a80");
        assertBytes(Cip113Redeemers.unfrackingAct(), "d87b80");
    }

    @Test
    void transferRedeemer() {
        // CoreRedeemers.transferRedeemer(List.of(tokenExists(2), tokenDoesNotExist(5)))
        assertBytes(Cip113Redeemers.transfer(List.of(
                        Cip113Redeemers.tokenExists(2), Cip113Redeemers.tokenDoesNotExist(5))),
                "d8799f9fd8799f02ffd87a9f05ffffff");
        // CoreRedeemers.transferRedeemer(List.of())
        assertBytes(Cip113Redeemers.transfer(List.of()), "d8799f80ff");
    }

    @Test
    void thirdPartyAndUnfrackingRedeemers() {
        // CoreRedeemers.thirdPartyRedeemer(4, 2) / unfrackingRedeemer(4, 2)
        assertBytes(Cip113Redeemers.thirdParty(4, 2), "d8799f0402ff");
        assertBytes(Cip113Redeemers.unfracking(4, 2), "d8799f0402ff");
    }

    @Test
    void issuanceMintRedeemer() {
        // CoreRedeemers.issuanceRedeemer(7)
        assertBytes(Cip113Redeemers.issuanceMint(7), "d8799f07ff");
    }

    @Test
    void issuanceLogicRedeemerWithTwoPolicies() {
        // CoreRedeemers.issuanceLogicRedeemer(List.of(
        //     IssuanceEntry("11"*28, mintProofRefInput(3)),
        //     IssuanceEntry("22"*28, mintProofOutputIndex(1))))
        Map<String, PlutusData> proofs = new LinkedHashMap<>();
        proofs.put("11".repeat(28), Cip113Redeemers.mintRefInput(3));
        proofs.put("22".repeat(28), Cip113Redeemers.mintOutputIndex(1));
        assertBytes(Cip113Redeemers.issuanceLogic(proofs),
                "a2581c11111111111111111111111111111111111111111111111111111111d8799f03ff"
                        + "581c22222222222222222222222222222222222222222222222222222222d87a9f01ff");
    }

    @Test
    void registryRedeemers() {
        // CoreRedeemers.registryInsert("ab"*28, bytes("cd"*28))
        assertBytes(Cip113Redeemers.registryInsert("ab".repeat(28), Credential.fromScript("cd".repeat(28))),
                "d87a9f581cabababababababababababababababababababababababababababab"
                        + "d87a9f581ccdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdffff");
        // CoreRedeemers.registrySpend()
        assertBytes(Cip113Redeemers.registrySpend(), "d87980");
    }

    @Test
    void protocolParamsDatum() {
        // new CoreProgrammableLogicGlobalParams(s01, s02, s03, s04, s05, null).toPlutusData()
        assertBytes(params(null),
                "d8799f"
                        + "d87a9f581c01010101010101010101010101010101010101010101010101010101ff"
                        + "d87a9f581c02020202020202020202020202020202020202020202020202020202ff"
                        + "d87a9f581c03030303030303030303030303030303030303030303030303030303ff"
                        + "d87a9f581c04040404040404040404040404040404040404040404040404040404ff"
                        + "d87a9f581c05050505050505050505050505050505050505050505050505050505ff"
                        + "d87a80ff");
        // ... with pending_upgrade_cred = Some(VerificationKey("06"*28))
        String withPending = "d8799f"
                + "d87a9f581c01010101010101010101010101010101010101010101010101010101ff"
                + "d87a9f581c02020202020202020202020202020202020202020202020202020202ff"
                + "d87a9f581c03030303030303030303030303030303030303030303030303030303ff"
                + "d87a9f581c04040404040404040404040404040404040404040404040404040404ff"
                + "d87a9f581c05050505050505050505050505050505050505050505050505050505ff"
                + "d8799fd8799f581c06060606060606060606060606060606060606060606060606060606ffffff";
        assertBytes(params(Credential.fromKey("06".repeat(28))), withPending);

        // And the decoder reads the same bytes back.
        ProgrammableLogicGlobalParams decoded = ProgrammableLogicGlobalParams.fromPlutusData(deserialize(withPending));
        assertThat(HexUtil.encodeHexString(decoded.getPendingUpgradeCred().getBytes()))
                .isEqualTo("06".repeat(28));
    }

    @Test
    void registryNode() {
        // new RegistryNode("ab"*28, "ff"*30, s01, s02, s03, EMPTY_VKEY, "cd"*28).toPlutusData()
        RegistryNode node = RegistryNode.builder()
                .key("ab".repeat(28)).next("ff".repeat(30))
                .mintingLogicScript(Credential.fromScript("01".repeat(28)))
                .transferLogicScript(Credential.fromScript("02".repeat(28)))
                .thirdPartyTransferLogicScript(Credential.fromScript("03".repeat(28)))
                .unfrackingLogicScript(Credential.fromKey(""))
                .globalStateCs("cd".repeat(28))
                .build();
        assertBytes(node.toPlutusData(),
                "d8799f581cabababababababababababababababababababababababababababab"
                        + "581effffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                        + "d87a9f581c01010101010101010101010101010101010101010101010101010101ff"
                        + "d87a9f581c02020202020202020202020202020202020202020202020202020202ff"
                        + "d87a9f581c03030303030303030303030303030303030303030303030303030303ff"
                        + "d8799f40ff"
                        + "581ccdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdff");
    }

    private static PlutusData params(Credential pending) {
        return ProgrammableLogicGlobalParams.builder()
                .programmableLogicGlobalCred(Credential.fromScript("01".repeat(28)))
                .issuanceLogicCred(Credential.fromScript("02".repeat(28)))
                .transferCred(Credential.fromScript("03".repeat(28)))
                .thirdPartyCred(Credential.fromScript("04".repeat(28)))
                .upgradeCred(Credential.fromScript("05".repeat(28)))
                .pendingUpgradeCred(pending)
                .build()
                .toPlutusData();
    }

    private static PlutusData deserialize(String hex) {
        try {
            return PlutusData.deserialize(HexUtil.decodeHexString(hex));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void assertBytes(PlutusData data, String expectedHex) {
        try {
            assertThat(HexUtil.encodeHexString(data.serializeToBytes())).isEqualTo(expectedHex);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
