package com.bloxbean.cardano.client.crypto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Keys {

    private SecretKey skey;
    private VerificationKey vkey;
}