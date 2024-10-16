package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Constr(alternative = 1)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatcherAddress {
    private String address;
}
