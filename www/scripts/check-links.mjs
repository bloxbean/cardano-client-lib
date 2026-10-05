// Verify that every internal link in the built site resolves.
//
// Run after `astro build`. Scans dist/**/*.html for site-relative hrefs and
// checks that each one corresponds to a real file or directory in dist/.
// External links are not fetched — this checks what we control.
//
//   node scripts/check-links.mjs [distDir]

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DOCSITE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const DIST = path.resolve(process.argv[2] ?? path.join(DOCSITE_ROOT, 'dist'));

/*
 * With BASE_PATH set, Astro prefixes every emitted link (`/next/quicktx/...`)
 * but still writes the files to the root of dist/ — the prefix only exists once
 * the build is deployed into that subdirectory. Strip it before resolving, or
 * every internal link looks broken in a subdirectory build.
 */
const BASE = (process.env.BASE_PATH ?? '').replace(/\/+$/, '');

function stripBase(href) {
  if (!BASE) return href;
  if (href === BASE) return '/';
  return href.startsWith(`${BASE}/`) ? href.slice(BASE.length) : href;
}

// Anchors emitted by the framework or by page chrome that have no file target.
const IGNORED = new Set(['/', '#']);

async function walk(dir, out = []) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full, out);
    else if (entry.name.endsWith('.html')) out.push(full);
  }
  return out;
}

async function exists(p) {
  try {
    await fs.stat(p);
    return true;
  } catch {
    return false;
  }
}

/** Resolve a site-absolute href to the file dist/ would serve for it. */
async function resolves(href) {
  const clean = stripBase(href).split('#')[0].split('?')[0];
  if (!clean || clean === '/') return true;

  const rel = decodeURIComponent(clean.replace(/^\//, ''));
  const target = path.join(DIST, rel);

  // A file (llms.txt, catalog.json, studio/app.mjs, an image).
  if (await exists(target)) return true;
  // A page route (/concepts/effects/ -> concepts/effects/index.html).
  if (await exists(path.join(target, 'index.html'))) return true;
  // A route written without its trailing slash.
  if (await exists(`${target}.html`)) return true;

  return false;
}

const hrefPattern = /(?:href|src)="([^"]+)"/g;
const idPattern = /\bid="([^"]+)"/g;

/** Ids present on a built page, cached per file. */
const idCache = new Map();

async function idsFor(htmlPath) {
  if (idCache.has(htmlPath)) return idCache.get(htmlPath);
  let ids = null;
  try {
    const html = await fs.readFile(htmlPath, 'utf8');
    ids = new Set([...html.matchAll(idPattern)].map((m) => m[1]));
  } catch {
    ids = null;
  }
  idCache.set(htmlPath, ids);
  return ids;
}

/**
 * Check a `#fragment` against the target page's ids. Returns null when the
 * fragment cannot be checked (the target is not an HTML page we built).
 */
async function fragmentResolves(clean, fragment) {
  const rel = decodeURIComponent(clean.replace(/^\//, ''));
  const candidates = [
    path.join(DIST, rel, 'index.html'),
    path.join(DIST, `${rel}.html`),
    path.join(DIST, rel),
  ];
  for (const candidate of candidates) {
    if (!candidate.endsWith('.html')) continue;
    const ids = await idsFor(candidate);
    if (ids) return ids.has(decodeURIComponent(fragment));
  }
  return null;
}

async function main() {
  if (!(await exists(DIST))) {
    console.error(`[check-links] no build output at ${DIST}. Run \`npm run build\` first.`);
    process.exit(1);
  }

  const files = await walk(DIST);
  const broken = new Map();       // href -> Set(pages)
  const badFragments = new Map(); // href -> Set(pages)
  const distinct = new Set();
  let occurrences = 0;
  let fragmentsChecked = 0;

  for (const file of files) {
    const html = await fs.readFile(file, 'utf8');
    const page = `/${path.relative(DIST, file).replace(/index\.html$/, '')}`;

    for (const [, href] of html.matchAll(hrefPattern)) {
      if (!href.startsWith('/') || href.startsWith('//')) continue;
      if (IGNORED.has(href)) continue;
      occurrences += 1;
      distinct.add(href);

      if (!(await resolves(href))) {
        if (!broken.has(href)) broken.set(href, new Set());
        broken.get(href).add(page);
        continue;
      }

      // The target exists; if the link names an anchor, check that too.
      const hash = href.indexOf('#');
      if (hash === -1) continue;
      const clean = stripBase(href.slice(0, hash)).split('?')[0];
      const fragment = href.slice(hash + 1);
      if (!clean || !fragment) continue;
      // Some hashes carry application state rather than naming an element.
      if (/[=&]/.test(fragment)) continue;

      const ok = await fragmentResolves(clean, fragment);
      if (ok === null) continue; // not an HTML page we can inspect
      fragmentsChecked += 1;
      if (!ok) {
        if (!badFragments.has(href)) badFragments.set(href, new Set());
        badFragments.get(href).add(page);
      }
    }
  }

  const report = (label, map) => {
    console.error(`\n[check-links] ${map.size} ${label}:\n`);
    for (const [href, pages] of [...map].sort()) {
      console.error(`  ${href}`);
      for (const page of [...pages].sort().slice(0, 6)) console.error(`      from ${page}`);
      if (pages.size > 6) console.error(`      … and ${pages.size - 6} more`);
    }
  };

  if (broken.size > 0) report('broken internal link(s)', broken);
  if (badFragments.size > 0) report('link(s) to a missing anchor', badFragments);
  if (broken.size > 0 || badFragments.size > 0) {
    console.error('');
    process.exit(1);
  }

  console.log(
    `[check-links] ${distinct.size} distinct internal target(s) ` +
    `(${occurrences} occurrences, ${fragmentsChecked} anchor(s)) ` +
    `across ${files.length} page(s) all resolve.`,
  );
}

await main();
