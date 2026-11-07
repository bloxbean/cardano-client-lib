# cddl-table-check

Checks the hand-written ledger tables in `ledger-cbor` against the CDDL of a pinned cardano-ledger commit, era by
era (ADR 0003, I3). It covers the certificate tags, the transaction body, witness set, auxiliary data, parameter
update, update and governance records, and the block and header layouts in `EraSpec`. The script's docstring lists
every rule it compares.

Run it by hand, and on each bump of the ledger pin:

```bash
tools/cddl-table-check/check_tables.py --ledger <cardano-ledger checkout> [--commit <sha>]
```

`--commit` defaults to the ADR's pin. The script reads the Java tables as text and the CDDL with `git show`, so the
checkout needs that commit but not a matching working tree. It exits 1 on any difference.

The CDDL does not show every change of meaning. Parameter update key 17, for example, is `coin` in both Alonzo and
Babbage, but cardano-ledger reads it as coins per UTxO word in Alonzo and coins per UTxO byte from Babbage. Such rows
are checked against the Haskell sources by hand and noted in the table.
