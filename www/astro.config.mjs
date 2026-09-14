// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import llmsIntegration from './scripts/llms-integration.mjs';

/*
 * BASE_PATH lets the same build serve from a subdirectory during preview
 * (`/next/` on GitHub Pages) and from the domain root once it goes live —
 * mirroring the trick the previous Next.js site used in next.config.mjs.
 */
const base = process.env.BASE_PATH || undefined;

const preview = /** @type {const} */ ({ text: 'Preview', variant: 'tip' });

export default defineConfig({
  site: 'https://cardano-client.dev',
  base,
  integrations: [
    starlight({
      title: 'Cardano Client Lib',
      description:
        'A Java library for building, signing and submitting Cardano transactions. ' +
        'QuickTx for everyday transactions, TxPlan / TxFlow / TxStream for declarative ' +
        'and multi-step workflows, with full Conway-era support.',
      logo: {
        src: './public/logo.svg',
        alt: 'Cardano Client Lib',
        replacesTitle: false,
      },
      favicon: '/favicon.svg',
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/bloxbean/cardano-client-lib',
        },
      ],
      editLink: {
        baseUrl: 'https://github.com/bloxbean/cardano-client-lib/edit/master/www/',
      },
      customCss: ['./src/styles/starlight.css'],
      expressiveCode: {
        // Docs write dependency snippets as ```gradle; Shiki has no such
        // grammar, so alias it to the language Gradle build files actually are.
        shiki: { langAlias: { gradle: 'groovy' } },
      },
      lastUpdated: true,
      components: {
        Head: './src/components/overrides/Head.astro',
      },
      sidebar: [
        {
          label: 'Start here',
          items: [
            { label: 'Why Cardano Client Lib', slug: 'start-here/why-ccl' },
            { label: 'Installation', slug: 'start-here/installation' },
            { label: 'Core concepts', slug: 'start-here/core-concepts' },
            { label: 'Accounts & addresses', slug: 'start-here/accounts-and-addresses' },
            { label: 'Key APIs at a glance', slug: 'start-here/key-apis' },
            { label: 'Build from source', slug: 'start-here/build-from-source' },
          ],
        },
        {
          label: 'Learn: a guided path',
          items: [
            { label: '1. Your first transaction', slug: 'learn/simple-transfer' },
            { label: '2. Native tokens', slug: 'learn/native-tokens' },
            { label: '3. Minting an NFT', slug: 'learn/minting-an-nft' },
            { label: '4. Multi-sig transfers', slug: 'learn/multisig' },
            { label: '5. Calling a Plutus script', slug: 'learn/plutus-scripts' },
            { label: '6. Staking & delegation', slug: 'learn/staking-and-delegation' },
            { label: "7. What's next", slug: 'learn/whats-next' },
          ],
        },
        {
          label: 'QuickTx',
          badge: /** @type {const} */ ({ text: 'Recommended', variant: 'success' }),
          items: [
            { label: 'Overview', slug: 'quicktx/overview' },
            { label: 'Unified Tx API', slug: 'quicktx/unified-tx' },
          ],
        },
        {
          label: 'TxPlan',
          badge: preview,
          items: [
            { label: 'Getting started', slug: 'txplan/getting-started' },
            { label: 'Overview', slug: 'txplan/overview' },
            { label: 'Advanced usage', slug: 'txplan/advanced-usage' },
          ],
        },
        {
          label: 'TxFlow',
          badge: preview,
          items: [
            { label: 'Overview', slug: 'txflow/overview' },
            { label: 'Your first flow', slug: 'txflow/your-first-flow' },
            { label: 'Getting started', slug: 'txflow/getting-started' },
            { label: 'Portable authoring', slug: 'txflow/portable-authoring' },
            { label: 'Execution engine', slug: 'txflow/execution-engine' },
            {
              label: 'Policies, results & observability',
              slug: 'txflow/policies-results-observability',
            },
            { label: 'Durable runtime', slug: 'txflow/durable-runtime' },
            { label: 'Operations & testing', slug: 'txflow/operations-testing' },
          ],
        },
        {
          label: 'TxStream',
          badge: preview,
          items: [
            { label: 'Getting started', slug: 'txstream/getting-started' },
            { label: 'Durability & exactly-once', slug: 'txstream/durability' },
            { label: 'Lanes & throughput', slug: 'txstream/throughput' },
            { label: 'Smart contracts', slug: 'txstream/smart-contracts' },
          ],
        },
        {
          label: 'Governance',
          items: [{ label: 'Governance API', slug: 'governance/overview' }],
        },
        {
          label: 'Smart contracts',
          items: [
            { label: 'Plutus API', slug: 'smart-contracts/plutus-api' },
            { label: 'Blueprint code generation', slug: 'smart-contracts/blueprint-codegen' },
            { label: 'PlutusData annotations', slug: 'smart-contracts/plutusdata-annotations' },
            {
              label: 'Annotation processor setup',
              slug: 'smart-contracts/annotation-processor-setup',
            },
            { label: 'Aiken integration', slug: 'smart-contracts/aiken' },
            { label: 'Scalus integration', slug: 'smart-contracts/scalus' },
          ],
        },
        {
          label: 'Verified structures',
          badge: preview,
          collapsed: true,
          items: [
            { label: 'MpfTrie overview', slug: 'verified-structures/overview' },
            { label: 'Production guide', slug: 'verified-structures/production-guide' },
            { label: 'Multi-version GC', slug: 'verified-structures/multi-version-gc' },
          ],
        },
        {
          label: 'Standards (CIPs)',
          collapsed: true,
          items: [
            { label: 'CIP-8 — Message signing', slug: 'standards/cip8' },
            { label: 'CIP-20 — Transaction messages', slug: 'standards/cip20' },
            { label: 'CIP-25 — NFT metadata', slug: 'standards/cip25' },
            { label: 'CIP-27 — Royalties', slug: 'standards/cip27' },
            { label: 'CIP-30 — Wallet bridge', slug: 'standards/cip30' },
            { label: 'CIP-67 — Asset name labels', slug: 'standards/cip67' },
            { label: 'CIP-68 — Datum metadata', slug: 'standards/cip68' },
          ],
        },
        {
          label: 'Reference',
          collapsed: true,
          items: [
            { label: 'Modules & artifacts', slug: 'reference/modules' },
            { label: 'Backend services', slug: 'reference/backend-services' },
            { label: 'Supplier interfaces', slug: 'reference/supplier-interfaces' },
            { label: 'Account API', slug: 'reference/account-api' },
            { label: 'Address API', slug: 'reference/address-api' },
            { label: 'HD wallet API', slug: 'reference/hd-wallet-api' },
            { label: 'Metadata API', slug: 'reference/metadata-api' },
            { label: 'Coin selection', slug: 'reference/coin-selection' },
            { label: 'Crypto API', slug: 'reference/crypto-api' },
            {
              label: 'Composable functions',
              slug: 'reference/composable-functions',
              badge: /** @type {const} */ ({ text: 'Advanced', variant: 'caution' }),
            },
          ],
        },
        {
          label: 'AI agents',
          items: [
            { label: 'Using CCL with AI', slug: 'ai' },
            { label: 'AI Starter Pack', slug: 'ai/starter-pack' },
          ],
        },
        {
          label: 'Project',
          collapsed: true,
          items: [
            { label: "What's new in 0.8.0", slug: 'project/whats-new' },
            { label: 'Showcase', slug: 'project/showcase' },
            { label: 'Contributing to docs', slug: 'project/contributing-to-docs' },
            { label: 'Support this project', slug: 'project/support' },
          ],
        },
      ],
    }),
    llmsIntegration(),
  ],
});
