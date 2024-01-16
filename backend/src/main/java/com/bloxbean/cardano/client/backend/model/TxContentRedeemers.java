package com.bloxbean.cardano.client.backend.model;

import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TxContentRedeemers {

  private Integer txIndex;
  private RedeemerTag purpose;
  private String scriptHash;
  private String redeemerDataHash;
  private String datumHash;
  private String unitMem;
  private String unitSteps;
  private String fee;
}
