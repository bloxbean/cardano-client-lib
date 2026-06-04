use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{OutputReference, Transaction}

pub type Owner {
  owner: VerificationKeyHash,
}

pub type Action {
  Greet { msg: ByteArray }
  Withdraw
  Cancel { reason: ByteArray }
}

validator multi_action {
  spend(
    datum: Option<Owner>,
    redeemer: Action,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(datum) = datum
    let signed = list.has(self.extra_signatories, datum.owner)
    when redeemer is {
      Greet { msg } -> signed && msg == "hi"
      Withdraw -> signed
      Cancel { .. } -> True
    }
  }
}
