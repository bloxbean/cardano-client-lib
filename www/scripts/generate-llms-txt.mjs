/*
 * Generates the AI ingestion artifacts:
 *
 *   /llms.txt            curated index (llmstxt.org convention)
 *   /llms-full.txt       every doc page concatenated, for bulk ingestion
 *   /ai/index.md         raw markdown of the "Using CCL with AI" page
 *   /ai/starter-pack.md  raw markdown of the AI Starter Pack
 *
 * The section ordering below mirrors the site sidebar so an agent reading
 * llms.txt sees the same "what should I use" hierarchy a human sees.
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateCatalog } from './generate-catalog.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DOCS = path.resolve(HERE, '../src/content/docs');
const SITE = 'https://cardano-client.dev';

/** Sidebar order -> llms.txt section order. Slugs not listed still land in llms-full.txt. */
const SECTIONS = [
  ['Start here', 'start-here'],
  ['Learn: a guided path', 'learn'],
  ['QuickTx (recommended for single transactions)', 'quicktx'],
  ['TxPlan (declarative YAML/JSON plans, preview)', 'txplan'],
  ['TxFlow (multi-step workflows, preview)', 'txflow'],
  ['TxStream (continuous submission, preview)', 'txstream'],
  ['Governance (Conway era)', 'governance'],
  ['Smart contracts', 'smart-contracts'],
  ['Verified structures (preview)', 'verified-structures'],
  ['Standards (CIPs)', 'standards'],
  ['Reference', 'reference'],
  ['AI agents', 'ai'],
  ['Project', 'project'],
];

async function walk(dir, acc = []) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full, acc);
    else if (entry.name.endsWith('.md') || entry.name.endsWith('.mdx')) acc.push(full);
  }
  return acc;
}

function parse(raw) {
  const m = raw.match(/^---\n([\s\S]*?)\n---\n?/);
  if (!m) return { data: {}, body: raw };
  const data = {};
  for (const line of m[1].split('\n')) {
    const kv = line.match(/^(\w[\w-]*):\s*(.*)$/);
    if (kv) data[kv[1]] = kv[2].trim().replace(/^["'](.*)["']$/, '$1');
  }
  return { data, body: raw.slice(m[0].length) };
}

async function loadPages() {
  const files = await walk(DOCS);
  const pages = [];
  for (const file of files) {
    const raw = await fs.readFile(file, 'utf8');
    const { data, body } = parse(raw);
    const slug = path
      .relative(DOCS, file)
      .replace(/\.mdx?$/, '')
      .replace(/\/index$/, '');
    pages.push({
      slug,
      url: `${SITE}/${slug}/`,
      title: data.title ?? slug,
      description: data.description ?? '',
      body: body.trim(),
      section: slug.split('/')[0],
    });
  }
  return pages.sort((a, b) => a.slug.localeCompare(b.slug));
}

function buildLlmsTxt(pages, catalog) {
  const byTier = (t) => catalog.modules.filter((m) => m.tier === t);
  const L = [];

  L.push('# Cardano Client Lib');
  L.push('');
  L.push(
    '> A Java library for building, signing and submitting Cardano transactions. ' +
      `Version ${catalog.version}, ${catalog.ledgerEra} era, Java ${catalog.javaBaseline}+.`,
  );
  L.push('');
  L.push('## Pick the right API first');
  L.push('');
  L.push('| Task | Use | Notes |');
  L.push('|---|---|---|');
  L.push(`| One transaction (send, mint, delegate, vote) | \`${catalog.canonical.singleTransaction}\` | Default choice. |`);
  L.push(`| A plan expressed as config rather than code | \`${catalog.canonical.declarativePlan}\` | YAML/JSON deserializable. |`);
  L.push(`| Several dependent transactions in order | \`${catalog.canonical.multiStepWorkflow}\` | Handles UTXO chaining and rollback. |`);
  L.push(`| Continuous submission over time | \`${catalog.canonical.continuousSubmission}\` | Exactly-once, durable restart. |`);
  L.push(`| Proof an Aiken validator can verify | \`${catalog.canonical.onChainProof}\` | MPF mode only. |`);
  L.push('');
  L.push('Do NOT generate code against these:');
  L.push('');
  for (const n of catalog.canonical.notRecommendedForGeneratedCode) L.push(`- ${n}`);
  L.push('');
  L.push(`Read ${SITE}/ai/starter-pack/ before generating code — it lists the mistakes agents`);
  L.push('most often make with this library and how to avoid them.');
  L.push('');

  for (const [label, section] of SECTIONS) {
    const inSection = pages.filter((p) => p.section === section);
    if (!inSection.length) continue;
    L.push(`## ${label}`);
    L.push('');
    for (const p of inSection) {
      L.push(`- [${p.title}](${p.url})${p.description ? `: ${p.description}` : ''}`);
    }
    L.push('');
  }

  L.push('## Modules');
  L.push('');
  L.push(`All artifacts use group \`${catalog.group}\` and version \`${catalog.version}\`.`);
  L.push('');
  for (const tier of ['recommended', 'preview', 'advanced', 'support']) {
    const mods = byTier(tier);
    if (!mods.length) continue;
    L.push(`### ${tier}`);
    L.push('');
    for (const m of mods) L.push(`- \`${m.artifact}\` — ${m.purpose} Entry point: \`${m.entry}\``);
    L.push('');
  }

  L.push('## Optional');
  L.push('');
  L.push(`- [Full documentation as one file](${SITE}/llms-full.txt)`);
  L.push(`- [Machine-readable module catalog](${SITE}/ai/catalog.json)`);
  L.push('- [Source repository](https://github.com/bloxbean/cardano-client-lib)');
  L.push('- [Examples repository](https://github.com/bloxbean/cardano-client-examples)');
  L.push('');

  return L.join('\n');
}

function buildLlmsFull(pages, catalog) {
  const L = [
    '# Cardano Client Lib — complete documentation',
    '',
    `Version ${catalog.version} · ${catalog.ledgerEra} era · generated ${catalog.generatedAt}`,
    `Source: ${SITE}`,
    '',
    '---',
    '',
  ];
  for (const p of pages) {
    L.push(`# ${p.title}`);
    L.push('');
    L.push(`Source: ${p.url}`);
    if (p.description) L.push(`Summary: ${p.description}`);
    L.push('');
    L.push(p.body);
    L.push('');
    L.push('---');
    L.push('');
  }
  return L.join('\n');
}

export async function generateLlmsFiles({ outDir, logger, catalog } = {}) {
  const log = logger ?? console;
  const cat = catalog ?? (await generateCatalog({ logger: log }));
  const pages = await loadPages();

  await fs.mkdir(outDir, { recursive: true });
  await fs.mkdir(path.join(outDir, 'ai'), { recursive: true });

  await fs.writeFile(path.join(outDir, 'llms.txt'), `${buildLlmsTxt(pages, cat)}\n`, 'utf8');
  await fs.writeFile(path.join(outDir, 'llms-full.txt'), `${buildLlmsFull(pages, cat)}\n`, 'utf8');

  // Raw markdown copies so an agent can fetch the two AI pages without HTML.
  for (const [slug, file] of [
    ['ai', 'index.md'],
    ['ai/starter-pack', 'starter-pack.md'],
  ]) {
    const page = pages.find((p) => p.slug === slug);
    if (!page) {
      log.error?.(`[llms-txt] missing expected page: ${slug}`);
      continue;
    }
    await fs.writeFile(
      path.join(outDir, 'ai', file),
      `# ${page.title}\n\nSource: ${page.url}\n\n${page.body}\n`,
      'utf8',
    );
  }

  log.info?.(`[llms-txt] wrote llms.txt, llms-full.txt and 2 ai/*.md from ${pages.length} pages`);
  return pages.length;
}
