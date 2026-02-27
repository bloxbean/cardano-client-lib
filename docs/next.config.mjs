import nextra from 'nextra'

const withNextra = nextra({
  contentDirBasePath: '/docs',
  defaultShowCopyCode: true
})

export default withNextra({
  output: 'export',
  images: {
    unoptimized: true
  },
  basePath: process.env.BASE_PATH || ''
})
