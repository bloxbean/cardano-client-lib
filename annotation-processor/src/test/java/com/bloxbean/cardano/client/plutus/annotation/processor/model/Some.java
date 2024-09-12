package com.bloxbean.cardano.client.plutus.annotation.processor.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.Data;

@Constr
@Data
public class Some implements MyOptional {

    private String something;

}
