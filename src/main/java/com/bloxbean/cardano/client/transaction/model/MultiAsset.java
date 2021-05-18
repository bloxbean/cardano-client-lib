package com.bloxbean.cardano.client.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MultiAsset {
    private String policyId;
    private List<Asset> assets = new ArrayList<>();

    @Override
    public String toString() {
        try {
            return "MultiAsset{" +
                    "policyId=" + policyId +
                    ", assets=" + assets +
                    '}';
        } catch (Exception e) {
            return "MultiAsset { Error : " + e.getMessage() + " }";
        }
    }
}
