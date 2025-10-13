use aiken/merkle_patricia_forestry.{Branch, ProofStep, from_root, has} as mpf

const proof_steps: List<ProofStep> =
  [
    Branch {
            skip: 0,
            neighbors: #"5619607e64ba34c0a5dd8929126e2688669ecc8f6d26509f9f885b1894dddaaa85c09af929492a871e4fae32d9d5c36e352471cd659bcdb61de08f1722acc3b1b3e0c42fc6ab5173a2233c3a1caea8080de9949e9ea1fe61d649de31d8bcd5d30000000000000000000000000000000000000000000000000000000000000000"
        },
  ]

// 2) Paste your Java outputs (raw bytes, hex):
const expected_root: ByteArray = #"877bd23c3e9c2df4b870294c3bf99397adb6f615dd4e6c2160aaf089baa0a22c"
const key: ByteArray = #"6170706c65"
const value: ByteArray = #"393030"

pub fn validate_mpf_proof() -> Bool {
  // Create a MerklePatriciaForestry from the expected root and verify the proof
  let trie = mpf.from_root(expected_root)
  mpf.has(trie, key, value, proof_steps)
}

test test_mpf_validation() {
  validate_mpf_proof() == True
}

