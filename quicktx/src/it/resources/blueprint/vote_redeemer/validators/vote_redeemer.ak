use cardano/certificate.{Certificate}
use cardano/governance.{Voter}
use cardano/transaction.{Transaction}

// One parameterised script per DRep. The publish handler lets the DRep register and the vote
// handler runs for its votes. Both accept only the redeemer equal to the applied parameter.
validator vote_redeemer(expected: Int) {
  publish(redeemer: Int, _certificate: Certificate, _self: Transaction) {
    redeemer == expected
  }

  vote(redeemer: Int, _voter: Voter, _self: Transaction) {
    redeemer == expected
  }

  else(_) {
    fail
  }
}
