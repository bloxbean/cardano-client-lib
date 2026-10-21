---
title: "Using Cardano Client Lib with AI"
description: "Machine-readable artifacts, agent rules, and the fastest way to get an AI coding agent writing correct CCL code."
---

Coding agents write a lot of Cardano off-chain Java now. This site publishes the artifacts an
agent needs so it generates code against the **current** API rather than a stale snapshot from its
training data.

## Point your agent here first

| Artifact | URL | What it is |
|---|---|---|
| Curated index | [`/llms.txt`](/llms.txt) | Every page, grouped, with the "which API do I use" table at the top. Follows the [llmstxt.org](https://llmstxt.org) convention. |
| Full corpus | [`/llms-full.txt`](/llms-full.txt) | The entire documentation site as one plain-text file, for bulk ingestion. |
| Starter pack | [`/ai/starter-pack/`](/ai/starter-pack/) · [raw](/ai/starter-pack.md) | The rules that keep an agent from making the common mistakes. **Read this before generating code.** |
| Module catalog | [`/ai/catalog.json`](/ai/catalog.json) | Machine-readable: every published module, its artifact id, its canonical entry-point class, and its recommendation tier. Generated from `settings.gradle` and `gradle.properties`, so it cannot drift from the build. |

The catalog and both `.txt` files are regenerated on every build. If the library gains a module,
these artifacts describe it the same day.

## Why this exists

Asked to "send 5 ADA on preview with cardano-client-lib", agents reliably produce code that
compiles and is wrong. The recurring failures are specific:

- Pairing `Networks.preview()` with `Constants.BLOCKFROST_MAINNET_URL` — the network and the
  backend URL are configured separately, and nothing stops you mismatching them.
- Calling `complete()` and then reporting success, when `complete()` only submits. It does not
  wait for the transaction to be included in a block.
- Reaching for the `function` module's `TxBuilder` composition primitives instead of QuickTx.
  It works, but it is the low-level layer and mistakes there are silent.
- Generating `WatchableQuickTxBuilder`, a class that **has never existed in a released version**.
  TxFlow is the multi-step API.
- Emitting Babbage-era patterns for Conway-only operations (DRep registration, votes, treasury
  withdrawals).

The starter pack addresses each of these directly.

## Rules files for specific tools

Drop the starter pack into whichever file your tool reads, so the rules load automatically
instead of relying on the agent to fetch a URL:

```bash
# Claude Code
curl -o CLAUDE.md https://cardano-client.dev/ai/starter-pack.md

# Cursor
mkdir -p .cursor/rules && \
  curl -o .cursor/rules/cardano-client-lib.mdc https://cardano-client.dev/ai/starter-pack.md

# GitHub Copilot
mkdir -p .github && \
  curl -o .github/copilot-instructions.md https://cardano-client.dev/ai/starter-pack.md
```

For an agent that can fetch URLs at run time, pointing it at `/llms.txt` is enough — the index
links onward to everything else.

## Verifying what an agent produces

Generated transaction code should be run before you trust it. The cheapest loop is
[Yaci DevKit](https://github.com/bloxbean/yaci-devkit), a local Cardano devnet with pre-funded
accounts and instant blocks — no faucet, no API key, no waiting on a public testnet.

Point the backend at `http://localhost:8080/api/v1/` and the same code you would run on preview
works locally, so an agent can execute what it wrote and read a real transaction hash back.

## Related

- [AI Starter Pack](/ai/starter-pack/) — the rules themselves
- [Key APIs at a glance](/start-here/key-apis/) — the human version of the same decision table
- [Modules & artifacts](/reference/modules/) — full dependency reference
