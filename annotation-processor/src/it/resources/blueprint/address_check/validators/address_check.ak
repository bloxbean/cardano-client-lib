use aiken/collection/list
use cardano/address.{Address, VerificationKey}
use cardano/transaction.{OutputReference, Transaction}

pub type Vault {
  admin: Address,
}

pub type Redeemer {
  marker: ByteArray,
}

validator address_check {
  spend(
    datum: Option<Vault>,
    _redeemer: Redeemer,
    _own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(datum) = datum
    expect VerificationKey(vk_hash) = datum.admin.payment_credential
    list.has(self.extra_signatories, vk_hash)
  }
}
