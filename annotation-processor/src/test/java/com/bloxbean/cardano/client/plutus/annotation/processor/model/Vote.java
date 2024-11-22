package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Constr
public class Vote {
    private Integer i;
    private String s;
}
