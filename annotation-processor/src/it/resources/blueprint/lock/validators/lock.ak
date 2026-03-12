use aiken/collection/list
use cardano/transaction.{OutputReference, Transaction}

pub type Owner {
  owner: ByteArray,
}

pub type Redeemer {
  msg: ByteArray,
}

validator lock {
  spend(
    datum: Option<Owner>,
    redeemer: Redeemer,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(datum) = datum
    let must_say_hello = redeemer.msg == "Hello, World!"
    let must_be_signed =
      list.has(self.extra_signatories, datum.owner)
    must_say_hello && must_be_signed
  }
}
