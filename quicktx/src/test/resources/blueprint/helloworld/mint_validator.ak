use aiken/dict
use aiken/list
use aiken/transaction.{ScriptContext}

type Action {
  Mint
  Burn
}

validator {
  fn mint(rdmr: Action, ctx: ScriptContext) -> Bool {
    when rdmr is {
      Mint -> True
      Burn -> False
    }
  }
}
