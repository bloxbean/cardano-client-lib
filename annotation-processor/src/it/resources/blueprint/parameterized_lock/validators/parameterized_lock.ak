use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{OutputReference, Transaction}

pub type Redeemer {
  msg: ByteArray,
}

validator parameterized_lock(authority: VerificationKeyHash) {
  spend(
    _datum: Option<Data>,
    redeemer: Redeemer,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    let signed = list.has(self.extra_signatories, authority)
    signed && redeemer.msg == "open"
  }
}
