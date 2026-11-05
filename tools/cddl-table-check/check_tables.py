#!/usr/bin/env python3
"""ADR 0003 I3: checks the hand-written ledger tables in ledger-cbor against the pinned CDDL, era by era.

For each era with a table it compares the certificate tags and their items, the transaction body and witness set keys
with their required flags and types, the rules a certificate is made of (credential, drep, relay, anchor,
pool_metadata, move_instantaneous_reward), the rules a transaction body is made of (transaction_input, both forms of
an output, datum_option, value, multiasset, mint, withdrawals), and the block and header layouts the era names in
EraSpec. Run it by hand, and on each bump of the ledger pin:

    tools/cddl-table-check/check_tables.py --ledger <cardano-ledger checkout> [--commit <sha>]

It reads the Java tables as text and the CDDL with `git show`. It exits 1 on any difference.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

PINNED = "f649f9751074d2ab3de033fc3912f29c9862c1f5"
ERAS = ["Shelley", "Allegra", "Mary", "Alonzo", "Babbage", "Conway"]
SOURCES = (Path(__file__).resolve().parents[2]
           / "ledger-cbor/src/main/java/com/bloxbean/cardano/client/transaction/raw")

# CDDL type -> FieldType, where the name decides. Other names are looked up as rules of the era's CDDL.
TYPES = {
    "stake_credential": "CREDENTIAL", "drep_credential": "CREDENTIAL", "committee_cold_credential": "CREDENTIAL",
    "committee_hot_credential": "CREDENTIAL",
    "pool_keyhash": "HASH28", "genesis_hash": "HASH28", "genesis_delegate_hash": "HASH28", "addr_keyhash": "HASH28",
    "script_hash": "HASH28",
    "vrf_keyhash": "HASH32", "auxiliary_data_hash": "HASH32", "script_data_hash": "HASH32", "hash32": "HASH32",
    "coin": "COIN", "positive_coin": "POSITIVE_COIN", "epoch": "UINT", "slot": "UINT", "network_id": "NETWORK_ID",
    "drep": "DREP", "unit_interval": "UNIT_INTERVAL", "reward_account": "REWARD_ACCOUNT",
    "anchor/ nil": "OPTIONAL_ANCHOR", "pool_metadata/ nil": "OPTIONAL_POOL_METADATA",
    "port/ nil": "OPTIONAL_PORT", "ipv4/ nil": "OPTIONAL_IPV4", "ipv6/ nil": "OPTIONAL_IPV6",
    "dns_name": "DNS_NAME", "url": "URL", "bytes": "BYTES", "move_instantaneous_reward": "MIR",
    "transaction_output": "TRANSACTION_OUTPUT", "update": "UPDATE", "redeemers": "REDEEMERS",
    "[* relay]": "RELAY_LIST", "[* certificate]": "CERTIFICATE_LIST", "certificates": "NONEMPTY_CERTIFICATE_SET",
    "set<addr_keyhash>": "KEY_HASH_SET", "0/ 1": "UINT", "{* stake_credential => delta_coin}/ coin": "MIR_TARGET",
    "header_body": "HEADER_BODY", "block_number": "UINT", "hash32/ nil": "OPTIONAL_HASH32", "vkey": "VKEY",
    "signature": "SIGNATURE", "vrf_vkey": "VRF_VKEY", "vrf_cert": "VRF_CERT", "kes_vkey": "KES_VKEY",
    "kes_signature": "KES_SIGNATURE", "sequence_number": "SEQUENCE_NUMBER", "kes_period": "KES_PERIOD",
    "major_protocol_version": "MAJOR_PROTOCOL_VERSION", "uint .size 4": "MINOR_PROTOCOL_VERSION",
    "block_body_size : uint .size 4": "UINT32", "operational_cert": "OPERATIONAL_CERT",
    "protocol_version": "PROTOCOL_VERSION", "bytes .size 80": "VRF_PROOF",
    "address": "ADDRESS", "value": "VALUE", "datum_option": "DATUM_OPTION", "script_ref": "SCRIPT_REF", "data": "DATA",
    "uint .size 2": "UINT16", "uint": "UINT", "asset_name": "ASSET_NAME", "int64": "INT64",
    "nonzero_int64": "NONZERO_INT64",
}
SHAPES = [
    (r"\[\* ", "LIST"), (r"\[\+ ", "NONEMPTY_LIST"), (r"set<", "SET"), (r"nonempty_set<", "NONEMPTY_SET"),
    (r"nonempty_oset<", "NONEMPTY_SET"), (r"nonempty_list<", "NONEMPTY_LIST"), (r"\{\* ", "MAP"),
    (r"\{\+ ", "NONEMPTY_MAP"),
]


def cddl(ledger, commit, era):
    path = f"{commit}:eras/{era.lower()}/impl/cddl/data/{era.lower()}.cddl"
    done = subprocess.run(["git", "-C", ledger, "show", path], capture_output=True, text=True, check=True)
    return re.sub(r";[^\n]*", "", done.stdout)


def rule(text, name):
    found = re.search(r"^" + re.escape(name) + r"\s*=", text, re.M)
    if not found:
        return None
    rest = text[found.end():]
    following = re.search(r"^[a-z_0-9$<>%]+\s*/?=", rest, re.M)
    return " ".join(rest[:following.start() if following else None].split())


def split_top(body, separator):
    """Splits on a separator outside any brackets."""
    parts, depth, current, i = [], 0, "", 0
    while i < len(body):
        if body.startswith(separator, i) and depth == 0:
            parts.append(current.strip())
            current, i = "", i + len(separator)
            continue
        depth += body[i] in "([{<"
        depth -= body[i] in ")]}>" and not body.startswith("=>", i - 1)
        current += body[i]
        i += 1
    return parts + [current.strip()] if current.strip() else parts


def field_type(text, cddl_text):
    text = " ".join(text.split())
    if text in TYPES:
        return TYPES[text]
    named = re.match(r"^\w+\s*:\s*(.+)$", text)
    if named and not text.startswith("{"):
        text = named.group(1)
    if text in TYPES:
        return TYPES[text]
    for prefix, name in SHAPES:
        if re.match(prefix, text):
            return name
    template = re.match(r"^(\w+)<", text)
    if template:
        return field_type(rule(cddl_text, template.group(1) + "<a0>"), cddl_text)
    resolved = rule(cddl_text, text)
    if resolved is None:
        raise ValueError(f"no type for {text!r}")
    return field_type(resolved, cddl_text)


def group_items(text, cddl_text):
    """(tag, item, item...) or (name, ...), with a group such as pool_params expanded."""
    items = []
    for item in split_top(text.strip()[1:-1] if text.strip().startswith("(") else text, ","):
        group = rule(cddl_text, item) if re.fullmatch(r"\w+", item) else None
        if group and group.startswith("("):
            items.extend(group_items(group, cddl_text))
        else:
            items.append(item)
    return items


def union_members(rule_name, cddl_text):
    """The members of a rule [a // b // c], each a group by name or written out: {tag: [FieldType...]}."""
    body = rule(cddl_text, rule_name).strip()
    members = {}
    for alternative in split_top(body[1:-1], "//"):
        group = rule(cddl_text, alternative) or alternative
        items = group_items(group.strip(), cddl_text)
        members[int(items[0])] = [field_type(item, cddl_text) for item in items[1:]]
    return members


def keyed_rule(rule_name, cddl_text):
    """{key: (FieldType, required)} of a map rule."""
    body = rule(cddl_text, rule_name).strip()
    fields = {}
    for entry in split_top(body[1:-1], ","):
        match = re.fullmatch(r"(\?\s*)?(\d+)\s*:\s*(.+)", entry)
        fields[int(match.group(2))] = (field_type(match.group(3), cddl_text), match.group(1) is None)
    return fields


def valid_in(era, start, end):
    return ERAS.index(start) <= ERAS.index(era) and (end is None or ERAS.index(era) <= ERAS.index(end))


def java(name):
    return (SOURCES / f"{name}.java").read_text()


def era_of(token):
    return None if token == "null" else token.replace("Era.", "")


def rows(name):
    """The constants of an enum table, as (constant, [arguments])."""
    source = java(name)
    body = source[source.index("enum " + name + " "):]
    body = body[body.index("{") + 1:body.index(";")]
    body = re.sub(r"//[^\n]*", "", body)
    return [(m.group(1), [a.strip() for a in m.group(2).split(",")])
            for m in re.finditer(r"([A-Z][A-Z0-9_]+)\(([^)]*)\)", body)]


def cert_table(era):
    table = {}
    for _, args in rows("CertKind"):
        if valid_in(era, era_of(args[1]), era_of(args[2])):
            table[int(args[0])] = args[3:]
    return table


def body_table(era):
    return {int(a[0]): (a[1], a[2] == "true") for _, a in rows("TxBodyField")
            if valid_in(era, era_of(a[3]), era_of(a[4]))}


def witness_table(era):
    return {int(a[0]): (a[1], False) for _, a in rows("WitnessSetField") if valid_in(era, era_of(a[2]), era_of(a[3]))}


def nested_kinds(view):
    source = java(view)
    body = source[source.index("enum Kind") :]
    body = body[body.index("{") + 1:body.index(";")]
    return {int(m.group(1)): [t.strip().replace("FieldType.", "") for t in m.group(2).split(",") if t.strip()]
            for m in re.finditer(r"[A-Z_0-9]+\((\d+)((?:, FieldType\.\w+)*)\)", body)}


def member_table(view):
    return {int(tag): [item.strip().replace("FieldType.", "")] for tag, item
            in re.findall(r"FieldSpec\.member\((\d+), Era\.\w+, null, (FieldType\.\w+)\)", java(view))}


def output_table(era):
    return {int(a[0]): (a[1], a[2] == "true") for _, a in rows("TxOutputField") if valid_in(era, era_of(a[3]), None)}


def table_rows(view, name):
    """The rows of a positional table written as FieldSpec.of(...): (FieldType, required), by position."""
    source = java(view)
    start = source.index(f'RecordSpec.positional("{name}"')
    body = source[start:source.index(";", start)]
    return {int(m[0]): (m[1], m[2] == "true") for m in
            re.findall(r"FieldSpec\.of\((\d+), FieldType\.(\w+), (true|false)", body)}


def array_fields(rule_name, cddl_text):
    """{position: (FieldType, required)} of an array rule, with its optional items."""
    fields = {}
    for position, item in enumerate(split_top(rule(cddl_text, rule_name).strip()[1:-1], ",")):
        fields[position] = (field_type(item.lstrip("? "), cddl_text), not item.startswith("?"))
    return fields


def value_rule(cddl_text):
    """(the coin's FieldType, the array form's items, the multiasset's quantity), of coin / [coin, multiasset<q>]."""
    coin, array = split_top(rule(cddl_text, "value"), "/")
    items = split_top(array[1:-1], ",")
    quantity = re.fullmatch(r"multiasset<(\w+)>", items[1]).group(1)
    return field_type(coin, cddl_text), [field_type(item, cddl_text) for item in items], field_type(quantity, cddl_text)


def assets_rule(text, cddl_text):
    """(policy id, the assets' shape, asset name, quantity) of {* policy_id => {+ asset_name => quantity}}."""
    match = re.fullmatch(r"\{([*+]) (\w+) => \{([*+]) (\w+) => (\w+)\}\}", text)
    return [field_type(match.group(2), cddl_text), "NONEMPTY_MAP" if match.group(3) == "+" else "MAP",
            field_type(match.group(4), cddl_text), field_type(match.group(5), cddl_text)]


def multiasset_table(name, era):
    """What RawMultiAsset's table of this name holds in the era, in the order of assets_rule."""
    args = re.search(r'table\("' + name + r'", FieldType\.(\w+), FieldType\.(\w+)\)', java("RawMultiAsset"))
    source = java("RawMultiAsset")
    key = re.search(r'name, FieldType\.(\w+),\s*FieldSpec\.of\(0, FieldType\.(\w+), false, Era\.Mary', source)
    asset = re.search(r'name \+ " assets", FieldType\.(\w+),', source).group(1)
    return [key.group(1), key.group(2), asset, args.group(2) if era == "Conway" else args.group(1)]


def entries_table(view):
    """(key type, value type) of a map of like entries."""
    found = re.search(r'RecordSpec\.entries\("\w+", FieldType\.(\w+),\s*FieldSpec\.of\(0, FieldType\.(\w+)', java(view))
    return [found.group(1), found.group(2)]


def positional(view, name=r"\w+"):
    source = java(view)
    args = re.search(r'RecordSpec\.positional\("' + name + r'",\s*([^)]*)\)', source).group(1)
    return [a.strip().replace("FieldType.", "") for a in args.split(",")]


def era_layouts(era):
    """The block and header layouts that the era's EraSpec row names."""
    return next((a[1], a[2]) for _, a in rows("EraSpec") if a[0] == f"Era.{era}")


def array_items(rule_name, cddl_text):
    """The FieldTypes of an array rule, with a group such as operational_cert expanded."""
    return [field_type(item, cddl_text) for item in group_items(rule(cddl_text, rule_name).strip()[1:-1], cddl_text)]


def compare(what, expected, actual, problems):
    if expected == actual:
        return
    for key in sorted(set(expected) | set(actual), key=str):
        if expected.get(key) != actual.get(key):
            problems.append(f"{what} {key}: CDDL {expected.get(key)}, table {actual.get(key)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ledger", required=True, help="a cardano-ledger checkout")
    parser.add_argument("--commit", default=PINNED, help="the ledger commit, by default the ADR's pin")
    options = parser.parse_args()

    problems_total = 0
    print(f"cardano-ledger {options.commit}")
    for era in ERAS:
        text = cddl(options.ledger, options.commit, era)
        problems = []
        certs, body, witnesses = cert_table(era), body_table(era), witness_table(era)
        compare("certificate", union_members("certificate", text), certs, problems)
        compare("body key", keyed_rule("transaction_body", text), body, problems)
        compare("witness key", keyed_rule("transaction_witness_set", text), witnesses, problems)
        compare("relay", union_members("relay", text), nested_kinds("RawRelay"), problems)
        compare("credential", union_members("credential", text), member_table("RawCredential"), problems)
        compare("transaction_input", {"items": array_items("transaction_input", text)},
                {"items": positional("RawInput")}, problems)
        legacy = "alonzo_transaction_output" if rule(text, "alonzo_transaction_output") else "transaction_output"
        expected = array_fields(legacy, text)
        if expected[1][0] == "COIN":  # to Allegra a value is a coin, which RawValue.RECORD reads
            expected[1] = ("VALUE", True)
        actual = {position: row for position, row in table_rows("RawOutput", "alonzo_transaction_output").items()
                  if position in expected or ERAS.index(era) >= ERAS.index("Alonzo")}
        compare("legacy output", expected, actual, problems)
        if rule(text, "babbage_transaction_output"):
            compare("map output", keyed_rule("babbage_transaction_output", text), output_table(era), problems)
            compare("datum_option", union_members("datum_option", text), member_table("RawDatum"), problems)
        withdrawals = re.fullmatch(r"\{[*+] (\w+) => (\w+)\}", rule(text, "withdrawals")).groups()
        compare("withdrawals", {"items": [field_type(item, text) for item in withdrawals]},
                {"items": entries_table("RawWithdrawal")}, problems)
        if rule(text, "value"):
            coin, items, quantity = value_rule(text)
            compare("value", {"coin": coin, "items": items, "quantity": quantity},
                    {"coin": "COIN", "items": [row[0] for row in table_rows("RawValue", "value").values()],
                     "quantity": multiasset_table("multiasset", era)[3]}, problems)
            compare("multiasset", {"items": assets_rule(rule(text, "multiasset<a0>").replace("a0", "uint"), text)[:3]},
                    {"items": multiasset_table("multiasset", era)[:3]}, problems)
            mint = rule(text, "mint")
            quantity = re.fullmatch(r"multiasset<(\w+)>", mint)
            mint = rule(text, "multiasset<a0>").replace("a0", quantity.group(1)) if quantity else mint
            compare("mint", {"items": assets_rule(mint, text)}, {"items": multiasset_table("mint", era)}, problems)
        if rule(text, "drep"):
            compare("drep", union_members("drep", text), nested_kinds("RawDRep"), problems)
        if rule(text, "anchor"):
            compare("anchor", {"items": [field_type(i, text) for i in split_top(rule(text, "anchor")[1:-1], ",")]},
                    {"items": positional("RawAnchor")}, problems)
        compare("pool_metadata",
                {"items": [field_type(i, text) for i in split_top(rule(text, "pool_metadata")[1:-1], ",")]},
                {"items": positional("RawPoolMetadata")}, problems)
        if rule(text, "move_instantaneous_reward"):
            compare("move_instantaneous_reward",
                    {"items": [field_type(i, text) for i in
                               split_top(rule(text, "move_instantaneous_reward")[1:-1], ",")]},
                    {"items": positional("RawMir")}, problems)
        block_layout, header_layout = era_layouts(era)
        layouts = dict(rows("BlockLayout") + rows("HeaderLayout"))
        compare("block", {"items": str(len(split_top(rule(text, "block")[1:-1], ",")))},
                {"items": layouts[block_layout][0]}, problems)
        compare("header", {"items": array_items("header", text)}, {"items": positional("RawHeader", "header")},
                problems)
        compare("header_body", {"items": array_items("header_body", text)}, {"items": layouts[header_layout]}, problems)
        compare("vrf_cert", {"items": array_items("vrf_cert", text)}, {"items": positional("RawVrfCert")}, problems)
        if rule(text, "operational_cert").startswith("["):
            compare("operational_cert", {"items": array_items("operational_cert", text)},
                    {"items": positional("RawHeader", "operational_cert")}, problems)
            compare("protocol_version", {"items": array_items("protocol_version", text)},
                    {"items": positional("RawHeader", "protocol_version")}, problems)
        status = "OK" if not problems else f"{len(problems)} DIFFERENCES"
        print(f"{era}: {status}; tags {sorted(certs)}, body keys {sorted(body)}, witness keys {sorted(witnesses)}, "
              f"{block_layout}, {header_layout}")
        for problem in problems:
            print("  " + problem)
        problems_total += len(problems)
    if problems_total:
        print(f"{problems_total} differences")
        sys.exit(1)
    print("All tables match the CDDL.")


if __name__ == "__main__":
    main()
