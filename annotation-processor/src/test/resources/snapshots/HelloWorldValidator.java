package com.test.multiple.demo;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.AbstractValidatorExtender;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;
import java.lang.String;

/**
 * Auto generated code. DO NOT MODIFY
 */
public class HelloWorldValidator extends AbstractValidatorExtender<HelloWorldValidator> implements LockUnlockValidatorExtender<HelloWorldValidator> {
  public static final String TITLE = "demo.hello_world";

  public static final String DESCRIPTION = null;

  public static final String COMPILED_CODE = "58e901000032323232323223223225333006323253330083371e6eb8c008c028dd5002a4410d48656c6c6f2c20576f726c642100100114a06644646600200200644a66601c00229404c94ccc030cdc79bae301000200414a226600600600260200026eb0c02cc030c030c030c030c030c030c030c030c024dd5180098049baa002375c600260126ea80188c02c0045261365653330043370e900018029baa001132325333009300b002149858dd7180480098031baa0011653330023370e900018019baa0011323253330073009002149858dd7180380098021baa001165734aae7555cf2ab9f5742ae881";

  public static final String HASH = "c1fe430f19ac248a8a7ea47db106002c4327e542c3fdc60ad6481103";

  private Network network;

  private String scriptAddress;

  private PlutusScript plutusScript;

  public HelloWorldValidator(Network network) {
    this.network = network;
  }

  public Network getNetwork() {
    return this.network;
  }

  public void setNetwork(Network network) {
    this.network = network;
  }

  /**
   * Returns the address of the validator script
   */
  public String getScriptAddress() {
    if(scriptAddress == null) {
      var script = getPlutusScript();
      scriptAddress = AddressProvider.getEntAddress(script, network).toBech32();
    }
    return scriptAddress;
  }

  public PlutusScript getPlutusScript() {
    if (plutusScript == null) {
      plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(COMPILED_CODE, PlutusVersion.v2);
    }
    return plutusScript;
  }
}
