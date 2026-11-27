/*
 * Astro integration exposing the AI ingestion artifacts:
 *
 *   /llms.txt              curated index (llmstxt.org)
 *   /llms-full.txt         whole docsite concatenated
 *   /ai/starter-pack.md    raw markdown of the AI Starter Pack
 *   /ai/index.md           raw markdown of the /ai/ landing page
 *   /ai/catalog.json       modules + canonical entry points + version
 *
 * `astro build` writes them into dist/ so they ship with the static site.
 * `astro dev` serves them from a temp dir via middleware, regenerated per
 * request, so editing a doc page is reflected without a restart.
 */

import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs/promises';
import { generateLlmsFiles } from './generate-llms-txt.mjs';
import { writeCatalog, generateCatalog } from './generate-catalog.mjs';

const SERVED_PATHS = new Set([
  '/llms.txt',
  '/llms-full.txt',
  '/ai/starter-pack.md',
  '/ai/index.md',
  '/ai/catalog.json',
]);

export default function llmsIntegration() {
  return {
    name: 'ccl-llms-txt',
    hooks: {
      'astro:build:done': async ({ dir, logger }) => {
        const outDir = fileURLToPath(dir);
        try {
          // Generate the catalog once and hand it to the llms generator so both
          // artifacts describe the same module set.
          const catalog = await generateCatalog({ logger });
          await generateLlmsFiles({ outDir, logger, catalog });
          await writeCatalog({ outDir, logger, catalog });
        } catch (err) {
          logger.error(`[llms-txt] generation failed: ${err.stack || err.message}`);
          throw err;
        }
      },

      'astro:server:setup': async ({ server, logger }) => {
        const tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'ccl-llms-'));
        const quiet = { info: () => {}, error: (m) => logger.error(m) };

        async function ensureFresh() {
          const catalog = await generateCatalog({ logger: quiet });
          await generateLlmsFiles({ outDir: tmpDir, logger: quiet, catalog });
          await writeCatalog({ outDir: tmpDir, logger: quiet, catalog });
        }

        try {
          await ensureFresh();
          logger.info(`[llms-txt] dev middleware ready (${[...SERVED_PATHS].join(', ')})`);
        } catch (err) {
          logger.error(`[llms-txt] dev warmup failed: ${err.message}`);
        }

        server.middlewares.use(async (req, res, next) => {
          const reqUrl = (req.url || '').split('?')[0];
          if (!SERVED_PATHS.has(reqUrl)) return next();

          try {
            await ensureFresh();
            const body = await fs.readFile(path.join(tmpDir, reqUrl.replace(/^\//, '')), 'utf8');
            res.statusCode = 200;
            res.setHeader(
              'Content-Type',
              reqUrl.endsWith('.json')
                ? 'application/json; charset=utf-8'
                : 'text/markdown; charset=utf-8',
            );
            res.setHeader('Cache-Control', 'no-store');
            res.end(body);
          } catch (err) {
            logger.error(`[llms-txt] dev serve failed for ${reqUrl}: ${err.message}`);
            res.statusCode = 500;
            res.end(`llms-txt generation error: ${err.message}`);
          }
        });
      },
    },
  };
}
