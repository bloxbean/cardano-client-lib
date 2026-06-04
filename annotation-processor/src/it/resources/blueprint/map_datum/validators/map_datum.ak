use aiken/collection/list
use aiken/collection/pairs
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{OutputReference, Transaction}

pub type Vault {
  owner: VerificationKeyHash,
  balances: Pairs<ByteArray, Int>,
}

pub type Redeemer {
  key: ByteArray,
}

validator map_datum {
  spend(
    datum: Option<Vault>,
    redeemer: Redeemer,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(datum) = datum
    let signed = list.has(self.extra_signatories, datum.owner)
    expect Some(amount) = pairs.get_first(datum.balances, redeemer.key)
    signed && amount > 0
  }
}
