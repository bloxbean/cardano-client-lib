use aiken/collection/list
use aiken/crypto.{VerificationKeyHash}
use cardano/transaction.{Transaction}

pub type MintAction {
  Mint
  Burn
}

validator mint_policy(authority: VerificationKeyHash) {
  mint(_redeemer: MintAction, _policy_id: ByteArray, self: Transaction) {
    list.has(self.extra_signatories, authority)
  }
}
