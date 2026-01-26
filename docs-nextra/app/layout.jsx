import { Head } from 'nextra/components'
import 'nextra-theme-docs/style.css'
import './globals.css'

export const metadata = {
  title: {
    default: 'Cardano Client Lib',
    template: '%s | Cardano Client Lib'
  },
  description: 'A Java Library for Simplifying Transactions, Token Minting, Address Derivation, and CIP Implementations for Applications on the Cardano Blockchain!',
  icons: {
    icon: '/img/favicon.svg'
  }
}

export default function RootLayout({ children }) {
  return (
    <html lang="en" dir="ltr" suppressHydrationWarning>
      <Head faviconGlyph="C" />
      <body>
        {children}
      </body>
    </html>
  )
}
