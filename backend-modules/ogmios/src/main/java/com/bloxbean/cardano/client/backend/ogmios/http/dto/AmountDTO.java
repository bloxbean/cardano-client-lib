package com.bloxbean.cardano.client.backend.ogmios.http.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AmountDTO {

    private String unit;
    private int quantity;
}
