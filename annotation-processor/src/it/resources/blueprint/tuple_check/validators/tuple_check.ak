use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{OutputReference, Transaction}

pub type Holder {
  owner: VerificationKeyHash,
  entry: (Int, ByteArray),
}

pub type Redeemer {
  expected_n: Int,
  expected_label: ByteArray,
}

validator tuple_check {
  spend(
    datum: Option<Holder>,
    redeemer: Redeemer,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(h) = datum
    let (n, label) = h.entry
    let signed = list.has(self.extra_signatories, h.owner)
    signed && n == redeemer.expected_n && label == redeemer.expected_label
  }
}
