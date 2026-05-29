use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{OutputReference, Transaction}

pub type Owner {
  owner: VerificationKeyHash,
}

validator option_redeemer {
  spend(
    datum: Option<Owner>,
    redeemer: Option<ByteArray>,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(datum) = datum
    let signed = list.has(self.extra_signatories, datum.owner)
    when redeemer is {
      Some(msg) -> signed && msg == "ok"
      None -> False
    }
  }
}
