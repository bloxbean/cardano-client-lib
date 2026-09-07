/*
 * Builds the machine-readable catalog served at /ai/catalog.json and consumed by
 * the landing page.
 *
 * Everything here is derived from the repository itself — settings.gradle for the
 * module list, gradle.properties for the version — so the site cannot advertise a
 * module or version the build does not actually contain.
 */

import fs from 'node:fs/promises';
import fsSync from 'node:fs';
import path from 'node:path';

/*
 * Resolve the repository root by walking up from the working directory until we
 * find the Gradle build.
 *
 * `import.meta.url` cannot be used here: this module is imported by
 * src/pages/index.astro, so Vite bundles it into dist/.prerender/chunks/ at
 * build time and a path relative to the module would point inside dist/.
 * The working directory is stable in every context that runs this (astro
 * build, astro dev, and `node scripts/...` from the docsite).
 */
function findRepoRoot(start) {
  let dir = path.resolve(start);
  for (let i = 0; i < 6; i += 1) {
    if (fsSync.existsSync(path.join(dir, 'gradle.properties')) &&
        fsSync.existsSync(path.join(dir, 'settings.gradle'))) {
      return dir;
    }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  throw new Error(
    `could not locate the repository root (gradle.properties + settings.gradle) above ${start}`,
  );
}

export const REPO_ROOT = findRepoRoot(process.cwd());
export const DOCSITE_ROOT = path.join(REPO_ROOT, 'www');

/*
 * Canonical entry point per module, plus the audience guidance an agent needs.
 * `tier` drives the "what should I use" table in llms.txt and the starter pack:
 *   recommended — the default choice for this job
 *   preview     — works, API may still shift before 0.8.0 final
 *   advanced    — correct but low-level; prefer a `recommended` module first
 *   support     — infrastructure you wire up rather than call directly
 */
const MODULE_INFO = {
  common: {
    purpose: 'Shared utilities, network definitions (Networks.mainnet/preprod/preview) and ADA conversion.',
    entry: 'com.bloxbean.cardano.client.common.model.Networks',
    tier: 'support',
    artifact: 'cardano-client-common',
  },
  'common-spec': {
    purpose: 'Shared CBOR spec types used across transaction and ledger modules.',
    entry: 'com.bloxbean.cardano.client.spec',
    tier: 'support',
    artifact: 'cardano-client-common-spec',
  },
  'core-api': {
    purpose: 'Core abstractions: UtxoSupplier, ProtocolParamsSupplier, TransactionProcessor.',
    entry: 'com.bloxbean.cardano.client.api.UtxoSupplier',
    tier: 'support',
    artifact: 'cardano-client-core-api',
  },
  core: {
    purpose: 'Account handling and the high-level transaction APIs QuickTx builds on.',
    entry: 'com.bloxbean.cardano.client.account.Account',
    tier: 'recommended',
    artifact: 'cardano-client-core',
  },
  'crypto-ext': {
    purpose: 'Extended cryptographic primitives (BLS12-381, additional hashes).',
    entry: 'com.bloxbean.cardano.client.crypto.bls',
    tier: 'support',
    artifact: 'cardano-client-crypto-ext',
  },
  'backend-modules:nexus': {
    purpose: 'Nexus backend implementation.',
    entry: 'com.bloxbean.cardano.client.backend.nexus',
    tier: 'recommended',
    artifact: 'cardano-client-backend-nexus',
  },
  'supplier:ogmios-supplier': {
    purpose: 'UtxoSupplier/ProtocolParamsSupplier backed by Ogmios, usable without a full backend.',
    entry: 'com.bloxbean.cardano.client.supplier.ogmios',
    tier: 'support',
    artifact: 'cardano-client-ogmios-supplier',
  },
  'supplier:kupo-supplier': {
    purpose: 'UtxoSupplier backed by Kupo.',
    entry: 'com.bloxbean.cardano.client.supplier.kupo',
    tier: 'support',
    artifact: 'cardano-client-kupo-supplier',
  },
  'plutus-aiken': {
    purpose: 'Aiken interop: load Aiken blueprints and apply parameters to Aiken-built validators.',
    entry: 'com.bloxbean.cardano.client.plutus.aiken',
    tier: 'recommended',
    artifact: 'cardano-client-plutus-aiken',
  },
  'verified-structures:verified-structures-core': {
    purpose: 'Shared node-store and proof abstractions for the verified data structures.',
    entry: 'com.bloxbean.cardano.vds.core',
    tier: 'support',
    artifact: 'cardano-client-verified-structures-core',
  },
  'verified-structures:rocksdb-core': {
    purpose: 'RocksDB node-store shared by the embedded trie backends.',
    entry: 'com.bloxbean.cardano.vds.rocksdb',
    tier: 'support',
    artifact: 'cardano-client-rocksdb-core',
  },
  'verified-structures:rdbms-core': {
    purpose: 'JDBC node-store shared by the relational trie backends.',
    entry: 'com.bloxbean.cardano.vds.rdbms',
    tier: 'support',
    artifact: 'cardano-client-rdbms-core',
  },
  'verified-structures:merkle-patricia-forestry-rocksdb': {
    purpose: 'Embedded RocksDB storage for MpfTrie. Single-process.',
    entry: 'com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore',
    tier: 'preview',
    artifact: 'cardano-client-merkle-patricia-forestry-rocksdb',
  },
  'verified-structures:merkle-patricia-forestry-rdbms': {
    purpose: 'Relational storage for MpfTrie (PostgreSQL/H2/SQLite). Multi-process.',
    entry: 'com.bloxbean.cardano.vds.mpf.rdbms',
    tier: 'preview',
    artifact: 'cardano-client-merkle-patricia-forestry-rdbms',
  },
  'verified-structures:jellyfish-merkle-rocksdb': {
    purpose: 'Embedded RocksDB storage for the Jellyfish Merkle Tree.',
    entry: 'com.bloxbean.cardano.vds.jmt.rocksdb',
    tier: 'preview',
    artifact: 'cardano-client-jellyfish-merkle-rocksdb',
  },
  'verified-structures:jellyfish-merkle-rdbms': {
    purpose: 'Relational storage for the Jellyfish Merkle Tree.',
    entry: 'com.bloxbean.cardano.vds.jmt.rdbms',
    tier: 'preview',
    artifact: 'cardano-client-jellyfish-merkle-rdbms',
  },
  quicktx: {
    purpose: 'Declarative transaction builder. The default way to build any transaction.',
    entry: 'com.bloxbean.cardano.client.quicktx.QuickTxBuilder',
    tier: 'recommended',
    artifact: 'cardano-client-quicktx',
  },
  txflow: {
    purpose:
      'Multi-step transaction workflows with dependency tracking, rollback handling and ' +
      'durable restart. Also hosts TxStream for continuous submission.',
    entry: 'com.bloxbean.cardano.client.txflow.exec.FlowEngine',
    tier: 'preview',
    artifact: 'cardano-client-txflow',
  },
  'txflow-extensions:txflow-store-rdbms': {
    purpose: 'Relational durable store for TxFlow/TxStream restart recovery.',
    entry: 'com.bloxbean.cardano.client.txflow.store.rdbms',
    tier: 'preview',
    artifact: 'cardano-client-txflow-store-rdbms',
  },
  function: {
    purpose:
      'Composable TxBuilder primitives. This is the internal implementation layer under ' +
      'QuickTx — use QuickTx unless it genuinely cannot express what you need.',
    entry: 'com.bloxbean.cardano.client.function.TxBuilder',
    tier: 'advanced',
    artifact: 'cardano-client-function',
  },
  governance: {
    purpose: 'Conway-era governance: DRep registration, voting, committee and treasury actions.',
    entry: 'com.bloxbean.cardano.client.governance',
    tier: 'recommended',
    artifact: 'cardano-client-governance',
  },
  address: {
    purpose: 'Cardano address construction, parsing and derivation.',
    entry: 'com.bloxbean.cardano.client.address.AddressProvider',
    tier: 'recommended',
    artifact: 'cardano-client-address',
  },
  crypto: {
    purpose: 'BIP32-Ed25519, BIP39 mnemonics and CIP-1852 key derivation.',
    entry: 'com.bloxbean.cardano.client.crypto',
    tier: 'support',
    artifact: 'cardano-client-crypto',
  },
  'hd-wallet': {
    purpose: 'HD wallet account and address enumeration over a derivation path.',
    entry: 'com.bloxbean.cardano.client.wallet.Wallet',
    tier: 'recommended',
    artifact: 'cardano-client-hd-wallet',
  },
  plutus: {
    purpose: 'Plutus script types, PlutusData encoding and CIP-57 blueprint loading.',
    entry: 'com.bloxbean.cardano.client.plutus.spec.PlutusData',
    tier: 'recommended',
    artifact: 'cardano-client-plutus',
  },
  'transaction-spec': {
    purpose: 'CDDL-faithful transaction types and CBOR serialization.',
    entry: 'com.bloxbean.cardano.client.transaction.spec.Transaction',
    tier: 'support',
    artifact: 'cardano-client-transaction-spec',
  },
  coinselection: {
    purpose:
      'UTXO selection strategies. Note the default filters UTXOs carrying a datum hash — ' +
      'that matters when spending script outputs.',
    entry: 'com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy',
    tier: 'support',
    artifact: 'cardano-client-coinselection',
  },
  metadata: {
    purpose: 'Transaction auxiliary data and metadata construction.',
    entry: 'com.bloxbean.cardano.client.metadata.MetadataBuilder',
    tier: 'recommended',
    artifact: 'cardano-client-metadata',
  },
  backend: {
    purpose: 'Backend service abstraction (UTXO, transaction, epoch, network queries).',
    entry: 'com.bloxbean.cardano.client.backend.api.BackendService',
    tier: 'support',
    artifact: 'cardano-client-backend',
  },
  'backend-modules:blockfrost': {
    purpose: 'Blockfrost backend implementation.',
    entry: 'com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService',
    tier: 'recommended',
    artifact: 'cardano-client-backend-blockfrost',
  },
  'backend-modules:koios': {
    purpose: 'Koios backend implementation.',
    entry: 'com.bloxbean.cardano.client.backend.koios.KoiosBackendService',
    tier: 'recommended',
    artifact: 'cardano-client-backend-koios',
  },
  'backend-modules:ogmios': {
    purpose: 'Ogmios/Kupo backend implementation.',
    entry: 'com.bloxbean.cardano.client.backend.ogmios.OgmiosBackendService',
    tier: 'recommended',
    artifact: 'cardano-client-backend-ogmios',
  },
  'annotation-processor': {
    purpose: 'Compile-time codegen for PlutusData converters and CIP-57 blueprints.',
    entry: '@Constr / @Blueprint annotations',
    tier: 'recommended',
    artifact: 'cardano-client-annotation-processor',
  },
  'verified-structures:merkle-patricia-forestry': {
    purpose:
      'Merkle Patricia Forestry trie. In MPF mode this is the Aiken-compatible structure for ' +
      'on-chain proof verification.',
    entry: 'com.bloxbean.cardano.vds.mpf.MpfTrie',
    tier: 'preview',
    artifact: 'cardano-client-merkle-patricia-forestry',
  },
  'verified-structures:jellyfish-merkle': {
    purpose:
      'Jellyfish Merkle Tree for off-chain authenticated state. NOT Aiken-compatible — do not ' +
      'use it for on-chain proof verification.',
    entry: 'com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree',
    tier: 'preview',
    artifact: 'cardano-client-jellyfish-merkle',
  },
};

/** CIP modules get a uniform description generated from this table. */
const CIP_TITLES = {
  cip8: 'Message signing (COSE_Sign1)',
  cip20: 'Transaction message / comment metadata',
  cip25: 'NFT metadata standard',
  cip27: 'CNFT community royalties',
  cip30: 'dApp-wallet web bridge data structures',
  cip67: 'Asset name label prefixes',
  cip68: 'Datum metadata standard',
  cip102: 'Royalty datum standard',
};

async function readVersion() {
  const props = await fs.readFile(path.join(REPO_ROOT, 'gradle.properties'), 'utf8');
  const version = props.match(/^\s*version\s*=\s*(.+)$/m)?.[1]?.trim();
  const group = props.match(/^\s*group\s*=\s*(.+)$/m)?.[1]?.trim();
  if (!version) throw new Error('could not read version from gradle.properties');
  return { version, group: group ?? 'com.bloxbean.cardano' };
}

async function readModules() {
  const settings = await fs.readFile(path.join(REPO_ROOT, 'settings.gradle'), 'utf8');
  return [...settings.matchAll(/^\s*include\s+'([^']+)'/gm)]
    .map((m) => m[1].replace(/^:/, ''))
    .filter((name) => {
      // Aggregator/test-only projects are not something a user depends on.
      if (['cip', 'backend-modules', 'supplier', 'verified-structures', 'txflow-extensions'].includes(name)) return false;
      if (['integration-test', 'it-support', 'test-support'].includes(name)) return false;
      if (name.endsWith(':txflow-soak')) return false;
      if (name.endsWith(':load-tools')) return false;
      return true;
    });
}

export async function generateCatalog({ logger } = {}) {
  const log = logger ?? console;
  const { version, group } = await readVersion();
  const moduleNames = await readModules();

  const modules = moduleNames.map((name) => {
    const info = MODULE_INFO[name];
    if (info) return { name, ...info };

    const cip = name.startsWith('cip:') ? name.slice(4) : null;
    if (cip && CIP_TITLES[cip]) {
      return {
        name,
        purpose: `CIP-${cip.replace('cip', '')} — ${CIP_TITLES[cip]}.`,
        entry: `com.bloxbean.cardano.client.cip.${cip}`,
        tier: 'recommended',
        artifact: `cardano-client-${cip}`,
      };
    }
    return {
      name,
      purpose: '',
      entry: '',
      tier: 'support',
      artifact: `cardano-client-${name.split(':').pop()}`,
    };
  });

  const catalog = {
    generatedAt: new Date().toISOString().slice(0, 10),
    library: 'cardano-client-lib',
    repository: 'https://github.com/bloxbean/cardano-client-lib',
    docs: 'https://cardano-client.dev',
    version,
    group,
    javaBaseline: 17,
    ledgerEra: 'Conway',
    canonical: {
      singleTransaction: 'com.bloxbean.cardano.client.quicktx.QuickTxBuilder',
      declarativePlan: 'com.bloxbean.cardano.client.quicktx.serialization.TxPlan',
      multiStepWorkflow: 'com.bloxbean.cardano.client.txflow.exec.FlowEngine',
      continuousSubmission: 'com.bloxbean.cardano.client.txflow.stream.TxFlowStream',
      onChainProof: 'com.bloxbean.cardano.vds.mpf.MpfTrie (MPF mode)',
      notRecommendedForGeneratedCode: [
        'com.bloxbean.cardano.client.function.* — internal implementation layer',
        'WatchableQuickTxBuilder — does not exist; superseded by TxFlow',
      ],
    },
    modules,
    counts: {
      modules: modules.length,
      cips: modules.filter((m) => m.name.startsWith('cip:')).length,
      backends: modules.filter((m) => m.name.startsWith('backend-modules:')).length,
    },
  };

  log.info?.(`[catalog] ${modules.length} modules, version ${version}`);
  return catalog;
}

export async function writeCatalog({ outDir, logger, catalog } = {}) {
  const data = catalog ?? (await generateCatalog({ logger }));
  const dest = path.join(outDir, 'ai');
  await fs.mkdir(dest, { recursive: true });
  await fs.writeFile(
    path.join(dest, 'catalog.json'),
    `${JSON.stringify(data, null, 2)}\n`,
    'utf8',
  );
  (logger ?? console).info?.('[catalog] wrote ai/catalog.json');
  return data;
}
