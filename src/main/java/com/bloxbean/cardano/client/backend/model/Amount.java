package com.bloxbean.cardano.client.backend.model;

import lombok.*;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Amount {
    private String unit;
    private BigInteger quantity;
}
