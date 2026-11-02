/*
 * Old documentation URLs (the previous site served its pages under /docs/) and where they live now.
 * Astro turns each entry into a redirect page. scripts/check-redirects.mjs fails the build if a
 * target page doesn't exist.
 */
const PAGES = {
  // Start here
  '': 'start-here/why-ccl',
  'gettingstarted/dependencies': 'start-here/installation',
  'gettingstarted/build': 'start-here/build-from-source',
  'gettingstarted/account-setup': 'start-here/accounts-and-addresses',
  'gettingstarted/concepts': 'start-here/core-concepts',
  'gettingstarted/key-apis': 'start-here/key-apis',

  // Learn
  'gettingstarted/simple-transfer': 'learn/simple-transfer',
  'gettingstarted/tokens-distribution': 'learn/native-tokens',
  'gettingstarted/multisig-quickstart': 'learn/multisig',
  'tutorials/nft-minting': 'learn/minting-an-nft',
  'tutorials/smart-contract-calls': 'learn/plutus-scripts',
  'tutorials/legacy-tutorials/simple-transfer': 'learn/simple-transfer',
  'tutorials/legacy-tutorials/tokens-distribution': 'learn/native-tokens',
  'tutorials/legacy-tutorials/multisig-quickstart': 'learn/multisig',

  // QuickTx and TxPlan
  'apis/transaction/quicktx-api': 'quicktx/overview',
  'preview/unified-tx': 'quicktx/unified-tx',
  'preview/txplan/overview': 'txplan/overview',
  'preview/txplan/advanced-usage': 'txplan/advanced-usage',

  // TxFlow
  'preview/txflow/overview': 'txflow/overview',
  'preview/txflow/getting-started': 'txflow/getting-started',
  'preview/txflow/portable-authoring': 'txflow/portable-authoring',
  'preview/txflow/execution-engine': 'txflow/execution-engine',
  'preview/txflow/policies-results-observability': 'txflow/policies-results-observability',
  'preview/txflow/durable-runtime': 'txflow/durable-runtime',
  'preview/txflow/operations-testing': 'txflow/operations-testing',
  // Pages for the FlowExecutor compatibility API, which this site doesn't document.
  'preview/txflow/legacy-flowexecutor': 'txflow/overview',
  'preview/txflow/building-flows': 'txflow/getting-started',
  'preview/txflow/chaining-modes': 'txflow/execution-engine',
  'preview/txflow/confirmation-rollback': 'txflow/policies-results-observability',
  'preview/txflow/retry-execution-results': 'txflow/policies-results-observability',
  'preview/txflow/advanced': 'txflow/execution-engine',

  // TxStream
  'preview/txflow/txstream-getting-started': 'txstream/getting-started',
  'preview/txflow/txstream-durability': 'txstream/durability',
  'preview/txflow/txstream-throughput': 'txstream/throughput',
  'preview/txflow/txstream-contracts': 'txstream/smart-contracts',

  // Governance
  'apis/governance/governance-api': 'governance/overview',

  // Smart contracts
  'apis/core/plutus-api': 'smart-contracts/plutus-api',
  'annotations/plutus-blueprint-code-generation': 'smart-contracts/blueprint-codegen',
  'annotations/plutus-data-annotations': 'smart-contracts/plutusdata-annotations',
  'annotations/dependencies': 'smart-contracts/annotation-processor-setup',
  'integrations/aiken-integration-api': 'smart-contracts/aiken',
  'integrations/scalus-integration-api': 'smart-contracts/scalus',

  // Verified structures
  'preview/mpftrie/overview': 'verified-structures/overview',
  'preview/mpftrie/production-guide': 'verified-structures/production-guide',
  'preview/mpftrie/multi-version-gc': 'verified-structures/multi-version-gc',

  // Standards
  'apis/standards/cip8-api': 'standards/cip8',
  'apis/standards/cip20-api': 'standards/cip20',
  'apis/standards/cip25-api': 'standards/cip25',
  'apis/standards/cip27-api': 'standards/cip27',
  'apis/standards/cip30-api': 'standards/cip30',
  'apis/standards/cip67-api': 'standards/cip67',
  'apis/standards/cip68-api': 'standards/cip68',

  // Reference
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

  // Project
  preview: 'project/whats-new',
  showcase: 'project/showcase',
  'support-this-project': 'project/support',
  'contributing-to-docs': 'project/contributing-to-docs',
};

export const legacyRedirects = Object.fromEntries(
  Object.entries(PAGES).map(([from, to]) => [from ? `/docs/${from}` : '/docs', `/${to}/`]),
);
