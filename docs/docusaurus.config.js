// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const {themes} = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Cardano Client Lib',
  tagline: 'A Java Library for Simplifying Transactions, Token Minting, Address Derivation, and CIP Implementations for Applications on the Cardano Blockchain!',
  url: 'https://cardano-client.bloxbean.com',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.svg',

  // GitHub pages deployment config.
  organizationName: 'bloxbean',
  projectName: 'cardano-client',

  // Internationalization config
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  // Enhanced metadata for SEO
  customFields: {
    metadata: [
      {name: 'keywords', content: 'cardano, java, blockchain, cryptocurrency, ada, smart contracts, defi, nft'},
      {name: 'description', content: 'Java library for building Cardano blockchain applications. Supports transactions, smart contracts, NFTs, staking, and governance.'},
      {property: 'og:image', content: 'https://cardano-client.bloxbean.com/img/logo_hero.svg'},
      {property: 'twitter:card', content: 'summary_large_image'},
    ],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/bloxbean/cardano-client-lib/tree/master/docs/',
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
          // Enhanced docs features
          breadcrumbs: true,
          sidebarCollapsed: false,
          // Add feedback widget
          remarkPlugins: [],
          rehypePlugins: [],
        },
        // Blog configuration (currently disabled)
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        // Enhanced sitemap
        sitemap: {
          changefreq: 'weekly',
          priority: 0.5,
          ignorePatterns: ['/tags/**'],
          filename: 'sitemap.xml',
        },
        gtag: {
          trackingID: 'G-XXXXXXXXXX', // Replace with your Google Analytics tracking ID
          anonymizeIP: true,
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Enhanced image configuration
      image: 'img/logo_hero.svg',
      
      // Enhanced navbar with search and version info
      navbar: {
        title: 'Cardano Client Lib',
        logo: {
          alt: 'BloxBean Logo',
          src: 'img/logo_small.svg',
          srcDark: 'img/logo_small.svg',
          href: '/',
          target: '_self',
          width: 32,
          height: 32,
        },
        hideOnScroll: false,
        items: [
          {
            type: 'doc',
            docId: 'intro',
            position: 'left',
            label: 'Documentation',
          },
          {
            type: 'dropdown',
            label: 'Quick Links',
            position: 'left',
            items: [
              {
                label: 'Installation',
                to: '/docs/quickstart/installation',
              },
              {
                label: 'First Transaction',
                to: '/docs/quickstart/first-transaction',
              },
              {
                label: 'Choosing Your Path',
                to: '/docs/quickstart/choosing-your-path',
              },
              {
                label: 'Examples Repository',
                href: 'https://github.com/bloxbean/cardano-client-examples',
              },
            ],
          },
          {
            href: 'https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html',
            label: 'JavaDoc',
            position: 'right',
          },
          {
            href: 'https://github.com/bloxbean/cardano-client-lib',
            position: 'right',
            className: 'header-github-link',
            'aria-label': 'GitHub repository',
          },
        ],
      },
      
      // Enhanced search configuration
      algolia: undefined, // Using local search instead
      // Enhanced footer
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {
                label: 'Getting Started',
                to: '/docs/quickstart/installation',
              },
              {
                label: 'QuickTx API',
                to: '/docs/quickstart/first-transaction',
              },
              {
                label: 'Examples',
                href: 'https://github.com/bloxbean/cardano-client-examples',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Discord',
                href: 'https://discord.gg/JtQ54MSw6p',
              },
              {
                label: 'GitHub Discussions',
                href: 'https://github.com/bloxbean/cardano-client-lib/discussions',
              },
              {
                label: 'Twitter',
                href: 'https://twitter.com/bloxbean',
              },
              {
                label: 'Stack Overflow',
                href: 'https://stackoverflow.com/questions/tagged/cardano-client-lib',
              },
            ],
          },
          {
            title: 'Resources',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/bloxbean/cardano-client-lib',
              },
              {
                label: 'JavaDoc',
                href: 'https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html',
              },
              {
                label: 'Cardano.org',
                href: 'https://cardano.org/',
              },
              {
                label: 'Release Notes',
                href: 'https://github.com/bloxbean/cardano-client-lib/releases',
              },
            ],
          },
        ],
        copyright: `© ${new Date().getFullYear()} BloxBean project. Built with Docusaurus.`,
      },
      // Enhanced code highlighting with Java optimizations
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: [
          'java',
          'kotlin',
          'scala',
          'gradle',
          'groovy',
          'json',
          'yaml',
          'bash',
          'properties',
          'toml',
        ],
        defaultLanguage: 'java',
        magicComments: [
          {
            className: 'theme-code-block-highlighted-line',
            line: 'highlight-next-line',
            block: {start: 'highlight-start', end: 'highlight-end'},
          },
          {
            className: 'code-block-error-line',
            line: 'error-next-line',
            block: {start: 'error-start', end: 'error-end'},
          },
          {
            className: 'code-block-success-line',
            line: 'success-next-line',
            block: {start: 'success-start', end: 'success-end'},
          },
        ],
      },
      
      // Enhanced color mode
      colorMode: {
        defaultMode: 'light',
        disableSwitch: false,
        respectPrefersColorScheme: true,
      },
      
      // Table of contents configuration
      tableOfContents: {
        minHeadingLevel: 2,
        maxHeadingLevel: 4,
      },
      
      // Live code blocks (for future use)
      liveCodeBlock: {
        playgroundPosition: 'bottom',
      },
      
      // Announcement bar (can be used for important notices)
      // announcementBar: {
      //   id: 'announcement',
      //   content: 'New version 0.7.0 released! <a href="/docs/guides/migration">Migration guide</a>',
      //   backgroundColor: '#fafbfc',
      //   textColor: '#091E42',
      //   isCloseable: true,
      // },
    }),
    
  // Enhanced plugins configuration
  plugins: [
    // Enhanced search with better configuration
    [
      require.resolve("@easyops-cn/docusaurus-search-local"),
      {
        hashed: true,
        language: ["en"],
        highlightSearchTermsOnTargetPage: true,
        explicitSearchResultPath: true,
        indexBlog: false,
        indexDocs: true,
        indexPages: true,
        docsRouteBasePath: "/docs",
        searchResultLimits: 10,
        searchResultContextMaxLength: 100,
      },
    ],
    // Copy button for code blocks
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {
            from: '/docs/getting-started',
            to: '/docs/quickstart/installation',
          },
        ],
      },
    ],
    // PWA support for offline documentation
    [
      '@docusaurus/plugin-pwa',
      {
        debug: false,
        offlineModeActivationStrategies: [
          'appInstalled',
          'standalone',
          'queryString',
        ],
        pwaHead: [
          {
            tagName: 'link',
            rel: 'icon',
            href: '/img/logo_small.svg',
          },
          {
            tagName: 'link',
            rel: 'manifest',
            href: '/manifest.json',
          },
          {
            tagName: 'meta',
            name: 'theme-color',
            content: '#0033ad',
          },
          {
            tagName: 'meta',
            name: 'apple-mobile-web-app-capable',
            content: 'yes',
          },
          {
            tagName: 'meta',
            name: 'apple-mobile-web-app-status-bar-style',
            content: '#0033ad',
          },
        ],
      },
    ],
  ],
  
  // Mermaid support
  themes: ['@docusaurus/theme-mermaid'],
  
  // Enhanced markdown configuration
  markdown: {
    mermaid: true,
  },
};

module.exports = config;
