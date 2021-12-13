package com.bloxbean.cardano.client.crypto.cip1852;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DerivationPath {
      private Segment purpose;
      private Segment coinType;
      private Segment account;
      private Segment role;
      private Segment index;

    public static DerivationPath createShelleyDerivationPath() {
          return createShelleyDerivationPath(0);
      }

    public static DerivationPath createShelleyDerivationPath(int index) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(index, false))
                .build();
    }

}
