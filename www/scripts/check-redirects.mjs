#!/usr/bin/env node
// Fails the build if a redirect from an old documentation URL points to a page that doesn't exist.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { legacyRedirects } from './legacy-redirects.mjs';

const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');
const entries = Object.entries(legacyRedirects);
const missing = entries.filter(
  ([from, to]) =>
    !fs.existsSync(path.join(dist, to, 'index.html')) ||
    !fs.existsSync(path.join(dist, from, 'index.html')),
);

if (missing.length) {
  for (const [from, to] of missing) console.error(`[check-redirects] ${from} -> ${to} is broken`);
  process.exit(1);
}
console.log(`[check-redirects] ${entries.length} redirects from old URLs all resolve.`);
