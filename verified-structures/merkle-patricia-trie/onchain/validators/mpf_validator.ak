use aiken/merkle_patricia_forestry.{Branch, ProofStep, from_root, has} as mpf

const proof_steps: List<ProofStep> =
  [
    Branch { skip: 0,
    neighbors: #"ded71135faab7faa0e7d70703d78795a3d96ece6059ccec07c45a53fcb4c5d4e290e296065b6c93385bc6debca5e9b090c7d0cd0fb79e9d6d62b744dc8eea4cef5794572ebf76521e476a0b76946cd06d2728bb9d82f56ae0f65e13ee69377727a089c677a3b536f29ecddd0e824444299843527c1488def989ae84cb9588e43" },
      Branch { skip: 0, neighbors: #"e6f04b72dcf8dbb0c78d7c06e3f0689ff50d66f0a2844bfbd31f3d352f6144f9dba0942db6c457ce16d3c27835ce9800fea1f3fc8ebea91cb76a63c44d562cf06c456cceb7c6bd9e87d1b439b7780429e3a805a2390a4ecd7c3745bdda5de43a354666cc3395df6b5fcec19236c79288713449afa0fc096a167e708043e0f534" },
      Branch { skip: 0, neighbors: #"e2f2b8335c6c1fddffe6379ef59b306a9d4950aa6fe75fb0887208e4e0bbd43bb0bfe597365312e977c52422c2a1b29ccaf65a5e2c524f4a108c33861f333bb13ffe87f9bd8efcf98b2126073242dabe1bcb7f7c26119ca643a3dd288dcb6d08dd203fa2d72b0d287917e9a5ea2574c80b59aea54b6283f602fbf49510b1a4f9" },
      Branch { skip: 0,
      neighbors: #"79a8e1fde9cf7193a051e50fe5f92a6cd5abfd6ec9fff81a50c2480ba02c3ce385c09af929492a871e4fae32d9d5c36e352471cd659bcdb61de08f1722acc3b18a2291951dbf180645494c5cc8299f91d59eb71a6cf6df936cd10b4ff13d79210000000000000000000000000000000000000000000000000000000000000000" },
  ]

// 2) Paste your Java outputs (raw bytes, hex):
const expected_root: ByteArray = #"14a667f09ea69b26fada862eea29fb97843ebf421c8cd31c1530d1596ae200b3"
const key: ByteArray = #"b4b82fe7915e9fc9cbc744dfeccb6039bdb10b707a4555abafa3f1f6"
const value: ByteArray = #"d866821b00000000001b68c9581c209c6caf1fdabf53984cdcdce4e8d53f680165f33557a47c54b1dd6a"

pub fn validate_mpf_proof() -> Bool {
  // Create a MerklePatriciaForestry from the expected root and verify the proof
  let trie = mpf.from_root(expected_root)
  mpf.has(trie, key, value, proof_steps)
}

test test_mpf_validation() {
  validate_mpf_proof() == True
}

