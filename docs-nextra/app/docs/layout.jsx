import { Footer, Layout, Navbar } from 'nextra-theme-docs'
import { getPageMap } from 'nextra/page-map'

const logo = (
  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
    <img src="/img/logo_small.svg" alt="BloxBean Logo" width={24} height={24} />
    <span style={{ fontWeight: 'bold' }}>Cardano Client Lib</span>
  </div>
)

const navbar = (
  <Navbar
    logo={logo}
    logoLink="/"
    projectLink="https://github.com/bloxbean/cardano-client-lib"
  >
    <a href="https://discord.gg/fzfmWPEpsb" target="_blank" rel="noopener noreferrer" style={{ display: 'flex', alignItems: 'center', marginRight: '8px' }}>
      Discord
    </a>
    <a href="https://x.com/BloxBean" target="_blank" rel="noopener noreferrer" style={{ display: 'flex', alignItems: 'center', marginRight: '8px' }}>
      X
    </a>
  </Navbar>
)

const footer = (
  <Footer>
    <div style={{ textAlign: 'center', padding: '1rem' }}>
      &copy; {new Date().getFullYear()} BloxBean Project. Built with Nextra.
    </div>
  </Footer>
)

export default async function DocsLayout({ children }) {
  const pageMap = await getPageMap('/docs')

  return (
    <Layout
      navbar={navbar}
      pageMap={pageMap}
      docsRepositoryBase="https://github.com/bloxbean/cardano-client-lib/tree/main/docs-nextra"
      footer={footer}
      sidebar={{ defaultMenuCollapseLevel: 1 }}
      toc={{
        float: true,
        extraContent: (
          <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <a
              href="https://chatgpt.com"
              target="_blank"
              rel="noopener noreferrer"
              style={{ fontSize: '0.875rem', color: 'var(--shiki-token-comment)' }}
            >
              Review with ChatGPT ↗
            </a>
            <a
              href="https://claude.ai"
              target="_blank"
              rel="noopener noreferrer"
              style={{ fontSize: '0.875rem', color: 'var(--shiki-token-comment)' }}
            >
              Review with Claude ↗
            </a>
          </div>
        )
      }}
      feedback={{ content: <>Question? Give us feedback →</> }}
      editLink={<>Edit this page on GitHub →</>}
    >
      {children}
    </Layout>
  )
}
