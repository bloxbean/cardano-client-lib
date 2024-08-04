package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.*;

import java.math.BigInteger;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MintAsset {
    private String assetName;
    private BigInteger quantity;

    private String receiver;
    private PlutusData receiverDatum; //For script receivers

    public MintAsset(String assetName, BigInteger quantity, String receiver) {
        this.assetName = assetName;
        this.quantity = quantity;
        this.receiver = receiver;
    }
}
