use cardano/transaction.{OutputReference, Transaction}

pub type IntList {
  Nil
  Cons { head: Int, tail: IntList }
}

pub type Redeemer {
  expected_sum: Int,
}

fn list_sum(xs: IntList) -> Int {
  when xs is {
    Nil -> 0
    Cons { head, tail } -> head + list_sum(tail)
  }
}

validator recursive_sum {
  spend(
    datum: Option<IntList>,
    redeemer: Redeemer,
    _own_ref: OutputReference,
    _self: Transaction,
  ) {
    expect Some(items) = datum
    list_sum(items) == redeemer.expected_sum
  }
}
