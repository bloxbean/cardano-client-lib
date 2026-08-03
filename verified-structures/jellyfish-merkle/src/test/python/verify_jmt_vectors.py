#!/usr/bin/env python3
"""Independent standard-library verifier for classic-radix16-blake2b256-v1 vectors."""

import hashlib
import json
import pathlib
import sys


NULL_HASH = bytes(32)


def digest(data):
    return hashlib.blake2b(data, digest_size=32).digest()


def nibbles(value):
    result = []
    for byte in value:
        result.extend((byte >> 4, byte & 0x0F))
    return result


def commit_leaf(key_hash, value_hash):
    require(len(key_hash) == 32 and len(value_hash) == 32, "invalid leaf hash length")
    return digest(b"\x00" + key_hash + value_hash)


def commit_branch(children):
    require(len(children) == 16, "branch must have 16 slots")
    bitmap = 0
    preimage = bytearray(b"\x01\x00\x00")
    for index, child in enumerate(children):
        if child is not None:
            require(len(child) == 32, "invalid child hash length")
            bitmap |= 1 << index
            preimage.extend(child)
        else:
            preimage.extend(NULL_HASH)
    preimage[1] = (bitmap >> 8) & 0xFF
    preimage[2] = bitmap & 0xFF
    return digest(bytes(preimage))


def build_root(leaves, depth=0):
    require(leaves, "cannot build an empty subtree")
    if len(leaves) == 1:
        key_hash, value_hash = next(iter(leaves.items()))
        return commit_leaf(key_hash, value_hash)
    require(depth < 64, "distinct keys did not diverge within 256 bits")
    groups = {}
    for key_hash, value_hash in leaves.items():
        nibble = nibbles(key_hash)[depth]
        groups.setdefault(nibble, {})[key_hash] = value_hash
    children = [None] * 16
    for nibble, group in groups.items():
        children[nibble] = build_root(group, depth + 1)
    return commit_branch(children)


def read_length(data, offset, additional):
    if additional < 24:
        return additional, offset
    widths = {24: 1, 25: 2, 26: 4, 27: 8}
    require(additional in widths, "indefinite/reserved CBOR length")
    width = widths[additional]
    require(offset + width <= len(data), "truncated CBOR length")
    return int.from_bytes(data[offset:offset + width], "big"), offset + width


def read_cbor(data, offset=0):
    require(offset < len(data), "truncated CBOR item")
    initial = data[offset]
    offset += 1
    major = initial >> 5
    length, offset = read_length(data, offset, initial & 0x1F)
    if major == 0:
        return length, offset
    if major == 2:
        require(offset + length <= len(data), "truncated CBOR byte string")
        return data[offset:offset + length], offset + length
    if major == 4:
        items = []
        for _ in range(length):
            item, offset = read_cbor(data, offset)
            items.append(item)
        return items, offset
    raise VectorError("unsupported CBOR major type: %d" % major)


def decode_exact(data):
    item, offset = read_cbor(data)
    require(offset == len(data), "trailing CBOR data")
    return item


def decode_node(encoded):
    item = decode_exact(encoded)
    require(isinstance(item, list) and item, "node must be a non-empty CBOR array")
    require(item[0] in (b"\x00", b"\x01", b"\x02"), "unknown node tag")
    if item[0] == b"\x01":
        require(len(item) == 3, "leaf field count")
        require(all(isinstance(value, bytes) and len(value) == 32 for value in item[1:]),
                "leaf hash length")
        return ("leaf", item[1], item[2])
    if item[0] == b"\x00":
        require(len(item) >= 2 and isinstance(item[1], int) and item[1] <= 0xFFFF,
                "internal bitmap")
        child_count = item[1].bit_count()
        require(len(item) in (2 + child_count, 3 + child_count), "internal field count")
        children = item[2:2 + child_count]
        require(all(isinstance(value, bytes) and len(value) == 32 for value in children),
                "internal child hash length")
        return ("internal", item[1], children)
    raise VectorError("extension nodes are outside the v1 vector corpus")


def verify_wire(root, key, value, including, wire):
    try:
        encoded_nodes = decode_exact(wire)
        require(isinstance(encoded_nodes, list) and len(encoded_nodes) <= 65,
                "proof node count")
        nodes = [decode_node(encoded) for encoded in encoded_nodes]
        key_hash = digest(key)
        path = nibbles(key_hash)
        depths = [-1] * len(nodes)
        depth = 0
        terminal_missing = False
        terminal_leaf = None
        for index, node in enumerate(nodes):
            if node[0] == "internal":
                require(depth < len(path), "proof exceeds key depth")
                depths[index] = depth
                if not (node[1] & (1 << path[depth])):
                    require(index == len(nodes) - 1, "nodes after missing branch")
                    terminal_missing = True
                    break
                depth += 1
            elif node[0] == "leaf":
                require(index == len(nodes) - 1, "nodes after leaf")
                terminal_leaf = node
                break
        if including:
            require(value is not None and terminal_leaf is not None, "inclusion terminal")
            require(terminal_leaf[1] == key_hash, "leaf key mismatch")
            value_hash = digest(value)
            require(terminal_leaf[2] == value_hash, "leaf value mismatch")
            computed = commit_leaf(key_hash, value_hash)
        elif terminal_leaf is not None:
            require(terminal_leaf[1] != key_hash, "matching leaf disproves non-inclusion")
            require(nibbles(terminal_leaf[1])[:depth] == path[:depth], "conflicting leaf path")
            computed = commit_leaf(terminal_leaf[1], terminal_leaf[2])
        else:
            require(terminal_missing or not nodes, "missing proof terminal")
            computed = None

        for index in range(len(nodes) - 1, -1, -1):
            node = nodes[index]
            if node[0] != "internal":
                continue
            bitmap = node[1]
            compact = iter(node[2])
            children = [next(compact) if bitmap & (1 << slot) else None for slot in range(16)]
            slot = path[depths[index]]
            children[slot] = computed
            computed = commit_branch(children)
        return (computed if computed is not None else NULL_HASH) == root
    except (VectorError, ValueError, IndexError, StopIteration):
        return False


def verify_document(document):
    require(document["schema"] == "jmt-golden-vectors-v1", "vector schema")
    require(document["profile"] == "classic-radix16-blake2b256-v1", "profile")
    require(document["hash"] == "blake2b-256" and document["hashLength"] == 32, "hash profile")

    state = {}
    for operation in document["operations"]:
        for update in operation["updates"]:
            key = bytes.fromhex(update["key"])
            value = bytes.fromhex(update["value"])
            key_hash = bytes.fromhex(update["keyHash"])
            value_hash = bytes.fromhex(update["valueHash"])
            require(digest(key) == key_hash, "key hash mismatch")
            require(digest(value) == value_hash, "value hash mismatch")
            state[key_hash] = value_hash
        require(build_root(state) == bytes.fromhex(operation["root"]),
                "operation root mismatch at version %s" % operation["version"])
        for encoded in operation["encodedNodes"]:
            decode_node(bytes.fromhex(encoded["cbor"]))

    for proof in document["proofs"]:
        value = None if proof["value"] is None else bytes.fromhex(proof["value"])
        actual = verify_wire(bytes.fromhex(proof["root"]), bytes.fromhex(proof["key"]),
                             value, proof["including"], bytes.fromhex(proof["wire"]))
        require(actual == proof["expectedValid"], "proof result mismatch: " + proof["id"])


class VectorError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VectorError(message)


def main():
    default = pathlib.Path(__file__).parents[1] / "resources" / "jmt" / "golden-vectors-v1.json"
    path = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else default
    with path.open("r", encoding="utf-8") as source:
        verify_document(json.load(source))
    print("Verified JMT golden vectors with independent Python implementation: %s" % path)


if __name__ == "__main__":
    try:
        main()
    except (VectorError, KeyError, TypeError, ValueError, OSError, json.JSONDecodeError) as error:
        print("JMT vector verification failed: %s" % error, file=sys.stderr)
        sys.exit(1)
