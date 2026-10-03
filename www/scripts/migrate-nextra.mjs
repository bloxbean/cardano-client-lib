/*
 * One-shot migration: docs/content (Nextra .mdx) -> www/src/content/docs (Starlight .md)
 *
 * This is deliberately NOT a build step. `www/src/content/docs` becomes the editable
 * source of truth after this runs once; `docs/` is frozen. Re-running overwrites the
 * migrated files, so run it only to redo the initial import.
 *
 * The old content is plain Markdown (verified: zero Nextra components, zero mermaid,
 * frontmatter already carries title/description), so the work here is path remapping
 * and link rewriting, not syntax conversion.
 *
 * Usage: node scripts/migrate-nextra.mjs [--dry]
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '../..');
const SRC = path.join(REPO, 'docs/content');
const DEST = path.resolve(HERE, '../src/content/docs');

const DRY = process.argv.includes('--dry');

/*
 * Source slug (relative to docs/content, no extension) -> target slug
 * (relative to src/content/docs, no extension).
 *
 * `null` means intentionally dropped. Anything absent is reported as UNMAPPED so a
 * page can never be silently lost.
 */
const MAP = {
  // ── Start here ──────────────────────────────────────────────────────────
  // Rewritten as the site entry point in src/content/docs/start-here/why-ccl.md.
  // The Nextra original was a feature list without a description or a reading path.
  'index': null,
  'gettingstarted/dependencies': 'start-here/installation',
  'gettingstarted/build': 'start-here/build-from-source',
  'gettingstarted/account-setup': 'start-here/accounts-and-addresses',
  'gettingstarted/concepts': 'start-here/core-concepts',
  'gettingstarted/key-apis': 'start-here/key-apis',

  // ── Learn (beginner path) ───────────────────────────────────────────────
  'gettingstarted/simple-transfer': 'learn/simple-transfer',
  'gettingstarted/tokens-distribution': 'learn/native-tokens',
  'tutorials/nft-minting': 'learn/minting-an-nft',
  'gettingstarted/multisig-quickstart': 'learn/multisig',
  'tutorials/smart-contract-calls': 'learn/plutus-scripts',

  // Legacy tutorials all use TxBuilder/SignerProviders (the `function` module),
  // which contradicts "QuickTx is recommended". Dropped — see plan D2.
  'tutorials/legacy-tutorials/simple-transfer': null,
  'tutorials/legacy-tutorials/tokens-distribution': null,
  'tutorials/legacy-tutorials/multisig-quickstart': null,

  // ── QuickTx ─────────────────────────────────────────────────────────────
  'apis/transaction/quicktx-api': 'quicktx/overview',
  'preview/unified-tx': 'quicktx/unified-tx',

  // ── TxPlan ──────────────────────────────────────────────────────────────
  'preview/txplan/overview': 'txplan/overview',
  'preview/txplan/advanced-usage': 'txplan/advanced-usage',

  // ── TxFlow (current FlowEngine/FlowRuntime API only) ────────────────────
  'preview/txflow/overview': 'txflow/overview',
  'preview/txflow/getting-started': 'txflow/getting-started',
  'preview/txflow/portable-authoring': 'txflow/portable-authoring',
  'preview/txflow/execution-engine': 'txflow/execution-engine',
  'preview/txflow/policies-results-observability': 'txflow/policies-results-observability',
  'preview/txflow/durable-runtime': 'txflow/durable-runtime',
  'preview/txflow/operations-testing': 'txflow/operations-testing',

  // Legacy FlowExecutor compatibility API — dropped per plan D2.
  'preview/txflow/legacy-flowexecutor': null,
  'preview/txflow/building-flows': null,
  'preview/txflow/chaining-modes': null,
  'preview/txflow/confirmation-rollback': null,
  'preview/txflow/retry-execution-results': null,
  'preview/txflow/advanced': null,

  // ── TxStream ────────────────────────────────────────────────────────────
  'preview/txflow/txstream-getting-started': 'txstream/getting-started',
  'preview/txflow/txstream-durability': 'txstream/durability',
  'preview/txflow/txstream-throughput': 'txstream/throughput',
  'preview/txflow/txstream-contracts': 'txstream/smart-contracts',

  // ── Governance ──────────────────────────────────────────────────────────
  'apis/governance/governance-api': 'governance/overview',

  // ── Smart contracts ─────────────────────────────────────────────────────
  'apis/core/plutus-api': 'smart-contracts/plutus-api',
  'annotations/plutus-blueprint-code-generation': 'smart-contracts/blueprint-codegen',
  'annotations/plutus-data-annotations': 'smart-contracts/plutusdata-annotations',
  'annotations/dependencies': 'smart-contracts/annotation-processor-setup',
  'integrations/aiken-integration-api': 'smart-contracts/aiken',
  'integrations/scalus-integration-api': 'smart-contracts/scalus',

  // ── Verified structures ─────────────────────────────────────────────────
  'preview/mpftrie/overview': 'verified-structures/overview',
  'preview/mpftrie/production-guide': 'verified-structures/production-guide',
  'preview/mpftrie/multi-version-gc': 'verified-structures/multi-version-gc',

  // ── Standards (CIPs) ────────────────────────────────────────────────────
  'apis/standards/cip8-api': 'standards/cip8',
  'apis/standards/cip20-api': 'standards/cip20',
  'apis/standards/cip25-api': 'standards/cip25',
  'apis/standards/cip27-api': 'standards/cip27',
  'apis/standards/cip30-api': 'standards/cip30',
  'apis/standards/cip67-api': 'standards/cip67',
  'apis/standards/cip68-api': 'standards/cip68',

  // ── Reference ───────────────────────────────────────────────────────────
  'gettingstarted/modules': 'reference/modules',
  'apis/core/account-api': 'reference/account-api',
  'apis/core/address-api': 'reference/address-api',
  'apis/core/hd-wallet-api': 'reference/hd-wallet-api',
  'apis/core/metadata-api': 'reference/metadata-api',
  'apis/providers/backend-services-api': 'reference/backend-services',
  'apis/providers/supplier-interfaces-api': 'reference/supplier-interfaces',
  'apis/utility/coin-selection-api': 'reference/coin-selection',
  'apis/utility/crypto-api': 'reference/crypto-api',
  'apis/transaction/composable-functions-api': 'reference/composable-functions',

  // ── Project ─────────────────────────────────────────────────────────────
  'preview/index': 'project/whats-new',
  'showcase': 'project/showcase',
  'support-this-project': 'project/support',
  // Rewritten for Astro/Starlight in src/content/docs/project/contributing-to-docs.md.
  // The Nextra original documents _meta.js and a workflow that no longer exists.
  'contributing-to-docs': null,
};

// Old section-index URLs that had no backing .mdx of their own.
const SECTION_ALIASES = {
  'preview': 'project/whats-new',
  'gettingstarted': 'start-here/installation',
  'tutorials': 'learn/simple-transfer',
  'apis': 'reference/modules',
};


/*
 * Prose fixes applied after link rewriting.
 *
 * Each of these referenced a page this migration intentionally drops, so the
 * link cannot simply be remapped — the surrounding sentence has to change too.
 * Keeping them here rather than hand-editing the output keeps the migration
 * reproducible: re-running the script yields the same tree.
 */
const POST_EDITS = {
  'preview/txflow/overview': [
    [
      "Maintaining an older integration? Start with [Legacy `FlowExecutor`](./legacy-flowexecutor). The\nolder topic pages are retained for compatibility work, but new applications should not use them as\nthe primary design guide.",
      ":::note[Maintaining an older `FlowExecutor` integration?]\n`FlowExecutor` is a compatibility API. It still works, but it combines definition and execution\nconcerns and is not the canonical durable-runtime path — this site documents `FlowEngine` and\n`FlowRuntime` only. For `FlowExecutor` reference material, see `txflow/README.md` in the source\ntree or the previous documentation site.\n:::",
    ],
  ],
  "apis/core/plutus-api": [
    [
      "#### Using the loaded script with QuickTx\n\nOnce you have a `PlutusScript`, use it with `ScriptTx` the same way as a manually created script:",
      "#### Using the loaded script with QuickTx\n\n:::note[`ScriptTx` is deprecated in 0.8.0]\nEvery `ScriptTx` operation is now available on `Tx`, and `ScriptTx` will be removed in a future\nrelease. The examples below still work; for new code, replace `new ScriptTx()` with `new Tx()`\nand add `.from(senderAddress)` instead of the builder's `.feePayer(...)`. See\n[Unified Tx API](/quicktx/unified-tx/).\n:::\n\nOnce you have a `PlutusScript`, use it with `ScriptTx` the same way as a manually created script:",
    ],
  ],
  "apis/standards/cip68-api": [
    [
      "```java\nPlutusData datum = referenceToken.getDatumAsPlutusData();",
      ":::note[`ScriptTx` is deprecated in 0.8.0]\nEvery `ScriptTx` operation is now available on `Tx`, and `ScriptTx` will be removed in a future\nrelease. The examples below still work; for new code, replace `new ScriptTx()` with `new Tx()`\nand add `.from(senderAddress)` instead of the builder's `.feePayer(...)`. See\n[Unified Tx API](/quicktx/unified-tx/).\n:::\n\n```java\nPlutusData datum = referenceToken.getDatumAsPlutusData();",
    ],
  ],
  'apis/transaction/quicktx-api': [
    [
      "### When to choose QuickTx vs. Composable Functions\n\n- Choose QuickTx if you want minimal code for common flows and a single entrypoint for build + sign + submit + wait.\n- Choose Composable Functions directly if you need to handcraft every step, reuse builders outside QuickTx, or integrate with highly customized pipelines.",
      "### When to choose QuickTx vs. Composable Functions\n\n- Choose QuickTx if you want minimal code for common flows and a single entrypoint for build + sign + submit + wait.\n- Choose [Composable Functions](/reference/composable-functions/) only if you need to handcraft every step, reuse builders outside QuickTx, or integrate with a highly customized pipeline. It is the internal implementation layer beneath QuickTx, and mistakes there fail in ways that are harder to diagnose.\n\n## When one transaction is not enough\n\nQuickTx builds and submits **one** transaction. When that stops being the shape of your problem,\nthree preview APIs sit above it \u2014 all built on the same transaction machinery:\n\n| You need | Use | Why not QuickTx |\n|---|---|---|\n| The same transaction shape with values supplied at run time, reviewable as config | [TxPlan](/txplan/getting-started/) | The decision belongs in a file operators can read, not in compiled code |\n| A second transaction that spends an output the first one created | [TxFlow](/txflow/your-first-flow/) | The new UTXO is not visible to a backend query yet \u2014 selection fails with \"not enough funds\" |\n| A continuous feed of transactions that must each land exactly once, across restarts | [TxStream](/txstream/getting-started/) | Idempotency and durable recovery are real machinery, not a retry loop |\n\nMost applications never need to leave this page. Move up only when you hit the specific wall in\nthe right-hand column.",
    ],
  ],
  'apis/transaction/composable-functions-api': [
    [
      'Composable Functions expose functional building blocks',
      ':::caution[Use QuickTx unless it cannot serve]\nThis is the **internal implementation layer** beneath QuickTx, documented for the rare case where\nQuickTx genuinely cannot express what you need — a custom balancing strategy, or slotting your own\nlogic between build stages.\n\nFor everything else, reach for [QuickTx](/quicktx/overview/) instead. It produces the same\ntransactions in a fraction of the code, and mistakes at this level (a missing balance step, inputs\nthat do not cover outputs, a signer applied in the wrong order) fail in ways that are much harder\nto diagnose.\n\nIf you are generating code with an AI agent, this page is explicitly **not** the recommended\nsurface — see the [AI Starter Pack](/ai/starter-pack/).\n:::\n\nComposable Functions expose functional building blocks',
    ],
  ],
  'preview/txflow/txstream-getting-started': [
    [
      "TxStream (`TxFlowStream`) is txflow's streaming submission API:",
      ":::tip[New to TxStream? Start here]\n**The problem:** you have many transactions to submit over time — a payout queue, an outbox\ntable, a message stream — and each must land **exactly once**, even if your process crashes and\nthe upstream queue redelivers the same item.\n\n**The shape of the answer:** you open a stream, submit work items with a business id you already\nhave (an order id, a message id), and each item becomes its own idempotent execution. Redelivering\nan identical item attaches to the existing work instead of paying twice.\n\nJump straight to [A minimal working stream](#a-minimal-working-stream) for the shortest version,\nthen come back for the guarantees below.\n\nSubmitting a *single* transaction? Use [QuickTx](/quicktx/overview/). Several transactions forming\n*one* workflow? Use [TxFlow](/txflow/your-first-flow/).\n:::\n\nTxStream (`TxFlowStream`) is txflow's streaming submission API:",
    ],
  ],
  'gettingstarted/simple-transfer': [
    [
      '> **Looking for the Composable Functions version?** Check the [Simple Ada Transfer - Composable Functions](/docs/tutorials/legacy-tutorials/simple-transfer) tutorial.',
      ':::tip[Why QuickTx?]\nQuickTx is the recommended way to build transactions. The lower-level\n[composable functions](/reference/composable-functions/) API can express the same transfer, but it\nis the internal implementation layer — reach for it only when QuickTx cannot serve your case.\n:::',
    ],
  ],
  'gettingstarted/tokens-distribution': [
    [
      '> **Looking for the Composable Functions version?** Check the [Simple Token Distribution - Composable Functions](/docs/tutorials/legacy-tutorials/tokens-distribution) tutorial.',
      ':::tip[Why QuickTx?]\nQuickTx is the recommended way to build transactions. The lower-level\n[composable functions](/reference/composable-functions/) API can express the same distribution, but\nit is the internal implementation layer — reach for it only when QuickTx cannot serve your case.\n:::',
    ],
  ],
  'gettingstarted/multisig-quickstart': [
    [
      '> **Looking for the Composable Functions version?** Check the [Multi-sig Transfer - Composable Functions](/docs/tutorials/legacy-tutorials/multisig-quickstart) tutorial.',
      ':::tip[Why QuickTx?]\nQuickTx is the recommended way to build transactions. The lower-level\n[composable functions](/reference/composable-functions/) API can express the same multi-sig\ntransfer, but it is the internal implementation layer — reach for it only when QuickTx cannot serve\nyour case.\n:::',
    ],
  ],
};


/*
 * Titles that only made sense inside the old navigation.
 *
 * "Dependencies", "Build", "Overview" and "Introduction" are fine as leaves of a
 * folder named `gettingstarted/` or `integrations/`, but the new site surfaces
 * page titles in search, in llms.txt and on the page itself, where they have to
 * stand on their own.
 */
const TITLE_OVERRIDES = {
  'gettingstarted/dependencies': 'Installation',
  'gettingstarted/build': 'Build from source',
  'gettingstarted/account-setup': 'Accounts & addresses',
  'gettingstarted/concepts': 'Core concepts',
  'gettingstarted/key-apis': 'Key APIs at a glance',
  'gettingstarted/modules': 'Modules & artifacts',
  'gettingstarted/simple-transfer': 'Your first transaction',
  'gettingstarted/tokens-distribution': 'Native tokens',
  'gettingstarted/multisig-quickstart': 'Multi-sig transfers',
  'tutorials/nft-minting': 'Minting an NFT',
  'tutorials/smart-contract-calls': 'Calling a Plutus script',
  'annotations/dependencies': 'Annotation processor setup',
  'annotations/plutus-data-annotations': 'PlutusData annotations',
  'annotations/plutus-blueprint-code-generation': 'Blueprint code generation',
  'integrations/aiken-integration-api': 'Aiken integration',
  'apis/standards/cip8-api': 'CIP-8 — Message signing',
  'apis/standards/cip20-api': 'CIP-20 — Transaction messages',
  'apis/standards/cip25-api': 'CIP-25 — NFT metadata',
  'apis/standards/cip27-api': 'CIP-27 — Royalties',
  'apis/standards/cip30-api': 'CIP-30 — Wallet bridge',
  'apis/standards/cip67-api': 'CIP-67 — Asset name labels',
  'apis/standards/cip68-api': 'CIP-68 — Datum metadata',
  'support-this-project': 'Support this project',
  'preview/index': "What's new in 0.8.0",
};

/*
 * The docs were written against 0.8.0-preview1. Rewrite that to whatever
 * gradle.properties currently says so dependency snippets cannot drift from the
 * version the repository actually builds.
 */

/*
 * Descriptions for the two pages that had none. Starlight tolerates a missing
 * description, but it is what search results and llms.txt show as the page
 * summary, so every page needs one.
 */
const DESCRIPTIONS = {
  'showcase': 'Projects and products built with Cardano Client Lib.',
  'support-this-project': 'Ways to support continued development of Cardano Client Lib.',
};

const STALE_VERSION = /0\.8\.0-preview1/g;

async function currentVersion() {
  const props = await fs.readFile(path.join(REPO, 'gradle.properties'), 'utf8');
  const v = props.match(/^\s*version\s*=\s*(.+)$/m)?.[1]?.trim();
  if (!v) throw new Error('could not read version from gradle.properties');
  return v;
}

const report = {
  migrated: [], dropped: [], unmapped: [], brokenLinks: [],
  strippedH1: 0, promotedH1: [], missingTitle: [], postEdits: 0, versionRewrites: 0,
};

const VERSION = await currentVersion();

async function walk(dir, acc = []) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full, acc);
    else if (entry.name.endsWith('.mdx')) acc.push(full);
  }
  return acc;
}

/** Resolve an old doc URL/relative path to a new absolute site path, or null. */
function resolveTarget(rawHref, fromSlug) {
  const [pathPart, hash = ''] = rawHref.split('#');
  let slug = pathPart.replace(/\/+$/, '');

  if (slug.startsWith('./') || slug.startsWith('../')) {
    // Relative to the *source* directory, then remapped.
    slug = path.posix.normalize(path.posix.join(path.posix.dirname(fromSlug), slug));
  } else if (slug.startsWith('/')) {
    slug = slug.replace(/^\/docs\//, '').replace(/^\/docs$/, '').replace(/^\//, '');
  } else {
    return null; // not an internal doc link
  }

  if (slug === '') return null;
  slug = slug.replace(/\.mdx?$/, '');

  let target = MAP[slug];
  if (target === undefined) target = SECTION_ALIASES[slug];

  if (target === undefined) return { status: 'unknown', slug, hash };
  if (target === null) return { status: 'dropped', slug, hash };
  return { status: 'ok', href: `/${target}/${hash ? '#' + hash : ''}` };
}

function transform(body, srcSlug) {
  /*
   * 1. Reconcile the H1 with the frontmatter title.
   *
   * Starlight renders `title:` as the page heading, so a leading `# H1` would
   * double up — but ~15 of these pages carry only `description:` in their
   * frontmatter and keep their real title in the H1. Dropping it there would
   * leave the page with no title at all, which Starlight rejects. So: strip the
   * H1 when a title already exists, and promote it when one does not.
   */
  const fm = body.match(/^---\n([\s\S]*?)\n---\n/);
  let out = body;
  if (fm) {
    const frontmatter = fm[1];
    const rest = body.slice(fm[0].length);
    const h1 = rest.match(/^\s*\n?#\s+(.+?)\n/);
    const hasTitle = /^title:/m.test(frontmatter);

    if (h1 && hasTitle) {
      out = fm[0] + rest.slice(h1[0].length).replace(/^\n+/, '\n');
      report.strippedH1++;
    } else if (h1 && !hasTitle) {
      const title = h1[1].trim().replace(/"/g, '\\"');
      out =
        `---\ntitle: "${title}"\n${frontmatter}\n---\n` +
        rest.slice(h1[0].length).replace(/^\n+/, '\n');
      report.promotedH1.push(`${srcSlug} -> title: ${title}`);
    } else if (!hasTitle) {
      report.missingTitle.push(srcSlug);
    }
  }

  // 2. Rewrite internal links.
  out = out.replace(/\]\((\.\.?\/[^)\s]*|\/[^)\s]*)\)/g, (whole, href) => {
    const r = resolveTarget(href, srcSlug);
    if (!r) return whole;
    if (r.status === 'ok') return `](${r.href})`;
    report.brokenLinks.push({ from: srcSlug, href, reason: r.status, target: r.slug });
    return whole; // left as-is so check-links flags it and it gets a prose fix
  });

  // 3. Re-title pages whose old name only worked inside the old folder tree.
  const override = TITLE_OVERRIDES[srcSlug];
  if (override) {
    out = out.replace(/^(---\n(?:[\s\S]*?\n)?)title:.*$/m, `$1title: "${override}"`);
  }

  // 3b. Add a description where the page had none.
  const desc = DESCRIPTIONS[srcSlug];
  if (desc && !/^description:/m.test(out.match(/^---\n([\s\S]*?)\n---/)?.[1] ?? '')) {
    out = out.replace(/^(---\n(?:[\s\S]*?\n)?title:.*)$/m, `$1\ndescription: "${desc}"`);
  }

  // 4. Pin version references to what the repository actually builds.
  const before = out;
  out = out.replace(STALE_VERSION, VERSION);
  if (out !== before) report.versionRewrites++;

  // 5. Prose fixes for links whose target this migration drops.
  for (const [needle, replacement] of POST_EDITS[srcSlug] ?? []) {
    if (!out.includes(needle)) {
      report.brokenLinks.push({ from: srcSlug, href: needle.slice(0, 60), reason: 'post-edit did not match' });
      continue;
    }
    out = out.replace(needle, replacement);
    report.postEdits++;
  }

  return out;
}

const files = await walk(SRC);

for (const file of files.sort()) {
  const srcSlug = path.relative(SRC, file).replace(/\.mdx$/, '');
  const target = MAP[srcSlug];

  if (target === null) {
    report.dropped.push(srcSlug);
    continue;
  }
  if (target === undefined) {
    report.unmapped.push(srcSlug);
    continue;
  }

  const body = await fs.readFile(file, 'utf8');
  const out = transform(body, srcSlug);
  const destFile = path.join(DEST, `${target}.md`);

  if (!DRY) {
    await fs.mkdir(path.dirname(destFile), { recursive: true });
    await fs.writeFile(destFile, out, 'utf8');
  }
  report.migrated.push(`${srcSlug} -> ${target}`);
}

// ── Report ─────────────────────────────────────────────────────────────────
console.log(`\n${DRY ? '[DRY RUN] ' : ''}Nextra -> Starlight migration\n`);
console.log(`  migrated:   ${report.migrated.length}`);
console.log(`  dropped:    ${report.dropped.length}  (intentional, see plan D2)`);
console.log(`  H1 stripped: ${report.strippedH1}  (title already in frontmatter)`);
console.log(`  H1 promoted to title: ${report.promotedH1.length}`);
console.log(`  prose post-edits: ${report.postEdits}`);
console.log(`  version rewrites -> ${VERSION}: ${report.versionRewrites} page(s)`);

if (report.missingTitle.length) {
  console.log(`\n  ✗ pages with no title at all (${report.missingTitle.length}):`);
  for (const m of report.missingTitle) console.log(`    - ${m}`);
  process.exitCode = 1;
}

if (report.dropped.length) {
  console.log('\n  Dropped pages:');
  for (const d of report.dropped) console.log(`    - ${d}`);
}

// A dropped-page link that a POST_EDIT rewrites away is already handled.
const unhandledLinks = report.brokenLinks.filter((b) => !(b.from in POST_EDITS));

if (unhandledLinks.length) {
  console.log(`\n  ⚠ Links needing a prose fix (${unhandledLinks.length}):`);
  for (const b of unhandledLinks) {
    console.log(`    - ${b.from}: ${b.href}  [${b.reason}${b.target ? ': ' + b.target : ''}]`);
  }
  process.exitCode = 1;
}

if (report.unmapped.length) {
  console.log(`\n  ✗ UNMAPPED — add to MAP or mark null (${report.unmapped.length}):`);
  for (const u of report.unmapped) console.log(`    - ${u}`);
  process.exitCode = 1;
} else {
  console.log('\n  ✓ every source page was either migrated or explicitly dropped\n');
}
