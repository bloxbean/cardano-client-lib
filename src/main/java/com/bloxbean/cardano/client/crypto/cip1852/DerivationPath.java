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


    public static DerivationPath createExternalAddressDerivationPath() {
          return createExternalAddressDerivationPath(0);
    }

    public static DerivationPath createExternalAddressDerivationPath(int index) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(0, false))
                .index(new Segment(index, false))
                .build();
    }

    public static DerivationPath createExternalAddressDerivationPathForAccount(int account) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(account, true))
                .role(new Segment(0, false))
                .index(new Segment(0, false))
                .build();
    }

    public static DerivationPath createInternalAddressDerivationPath(int index) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(1, false))
                .index(new Segment(index, false))
                .build();
    }

    public static DerivationPath createInternalAddressDerivationPathForAccount(int account) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(account, true))
                .role(new Segment(1, false))
                .index(new Segment(0, false))
                .build();
    }

    public static DerivationPath createStakeAddressDerivationPath() {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(0, true))
                .role(new Segment(2, false))
                .index(new Segment(0, false))
                .build();
    }

    public static DerivationPath createStakeAddressDerivationPathForAccount(int account) {
        return DerivationPath.builder()
                .purpose(new Segment(1852, true))
                .coinType(new Segment(1815, true))
                .account(new Segment(account, true)) //set account
                .role(new Segment(2, false)) //Stake address
                .index(new Segment(0, false))
                .build();
    }


}
