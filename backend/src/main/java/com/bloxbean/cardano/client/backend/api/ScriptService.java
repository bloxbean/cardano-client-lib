package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.api.exception.ApiException;

public interface ScriptService {

  /**
   * Datum value
   * Query JSON value of a datum by its hash.
   *
   * @param datumHash Hash of the datum. (required)
   * @return ScriptDatum
   */
  Result<ScriptDatum> getScriptDatum(String datumHash) throws ApiException;


}
