package com.bloxbean.cardano.client.supplier.ogmios.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AmountDto {
    private String unit;
    private int quantity;
}
