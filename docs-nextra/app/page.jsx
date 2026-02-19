'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'

const features = [
  {
    icon: '☕',
    title: 'Easy to Use Java Library',
    description: 'Address generation, transfer, token minting, Plutus contract calls and more with a clean, intuitive API.',
    list: ['Simple declarative transaction building', 'Comprehensive address derivation', 'Full smart contract support', 'CIP implementations included']
  },
  {
    icon: '🔌',
    title: 'Multiple Backend Providers',
    description: 'Connect to Cardano through your preferred backend service with unified API interfaces.',
    list: ['Blockfrost integration', 'Koios support', 'Ogmios/Kupo backends', 'Custom provider support']
  },
  {
    icon: '📋',
    title: 'CIP Implementations',
    description: 'Built-in support for Cardano Improvement Proposals to ensure compatibility and standards compliance.',
    list: ['CIP-8 Message signing', 'CIP-20 Transaction metadata', 'CIP-25 NFT metadata', 'CIP-30, CIP-67/68 and more']
  }
]

const keyApisFeatures = {
  backend: [
    'Multiple out-of-box backend providers',
    'Write your own provider using supplier interfaces'
  ],
  account: [
    'Create new mainnet/testnet accounts',
    'Create accounts from mnemonic',
    'Create accounts from account private key',
    'Create accounts using derivation path'
  ]
}

const quickTxFeatures = {
  simple: ['Define outputs with Tx api'],
  compose: ['Compose transactions and provide signer', 'Submit transaction to blockchain'],
  multiple: ['Define multiple Txs with multiple senders'],
  mint: ['Create a policy', 'Define minting transaction', 'Provide signers for the account and policy'],
  utxo: ['Use an out-of-box UTxO selection strategy', 'Create custom UTxO selection strategy'],
  contract: ['Use Tx api to pay to a contract address'],
  unlock: ['Use ScriptTx api to define a script unlock tx', 'Attach validator script to the transaction']
}

const composableFeatures = {
  simple: ['Define outputs', 'Compose outputs using TxBuilder', 'Initialize TxBuilderContext', 'Build and Sign transaction'],
  submit: ['Submit transaction to blockchain'],
  mint: ['Create a policy', 'Define multi-asset', 'Define output with multi-asset', 'Create metadata', 'Balance transaction', 'Build and sign with policy key']
}

const keyApisCode = {
  backend: `//Blockfrost Backend Service
BackendService backendService =
    new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, "<Project_id>");

//Koios Backend Service
BackendService koiosBackendService =
    new KoiosBackendService(Constants.KOIOS_PREPROD_URL);

//Ogmios Backend Service
BackendService ogmiosBackendService = new OgmiosBackendService(OGMIOS_URL);

//Kupo Utxo Service
KupoUtxoService kupoUtxoService = new KupoUtxoService(KUPO_URL);
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(kupoUtxoService);`,
  account: `//Mainnet account from mnemonic
Account mainnetAccount = new Account(mnemonic);

//Testnet account from mnemonic
Account testnetAccount = new Account(Networks.testnet(), mnemonic);

//New mainnet account
Account mainnetAccount = new Account();

//New testnet account
Account testnetAccount = new Account(Networks.testnet());`
}

const quickTxCode = {
  simple: `Tx tx = new Tx()
    .payToAddress(receiver1, Amount.ada(1.5))
    .payToAddress(receiver2, Amount.ada(2.5))
    .from(sender1Addr);`,
  compose: `QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
Result<String> result = quickTxBuilder.compose(tx)
    .withSigner(SignerProviders.signerFrom(sender1))
    .completeAndWait(System.out::println);`,
  multiple: `Tx tx1 = new Tx()
    .payToAddress(receiver1, Amount.ada(1.5))
    .from(sender1Addr);

Tx tx2 = new Tx()
    .payToAddress(receiver1, Amount.ada(1.5))
    .from(sender2Addr);

Result<String> result = quickTxBuilder.compose(tx1, tx2)
    .feePayer(sender1Addr)
    .withSigner(SignerProviders.signerFrom(sender1))
    .withSigner(SignerProviders.signerFrom(sender2))
    .completeAndWait(System.out::println);`,
  mint: `//Create a policy
Policy policy = PolicyUtil.createMultiSigScriptAtLeastPolicy("test_policy", 1, 1);
String assetName = "MyAsset";
BigInteger qty = BigInteger.valueOf(1000);

//Define mint Tx
Tx tx = new Tx()
    .mintAssets(policy.getPolicyScript(),
        new Asset(assetName, qty), receiver)
    .attachMetadata(MessageMetadata.create().add("Sample Metadata"))
    .from(sender1Addr);

//Compose and sign Tx
Result<String> result = quickTxBuilder.compose(tx)
    .withSigner(SignerProviders.signerFrom(sender1))
    .withSigner(SignerProviders.signerFrom(policy))
    .complete();`,
  utxo: `UtxoSelectionStrategy randomSelectionStg =
    new RandomImproveUtxoSelectionStrategy(
        new DefaultUtxoSupplier(backendService.getUtxoService()));

Result<String> result = quickTxBuilder.compose(tx)
    .withSigner(SignerProviders.signerFrom(sender1))
    .withUtxoSelectionStrategy(randomSelectionStg)
    .complete();`,
  contract: `Tx tx = new Tx()
    .payToContract(scriptAddr, amount, plutusData)
    .from(sender2Addr);`,
  unlock: `ScriptTx scriptTx = new ScriptTx()
    .collectFrom(utxo, redeemer)
    .payToAddress(receiver1, amount)
    .attachSpendingValidator(plutusScript);

Result<String> result = quickTxBuilder.compose(scriptTx)
    .feePayer(sender1Addr)
    .withSigner(SignerProviders.signerFrom(sender1))
    .completeAndWait(System.out::println);`
}

const composableCode = {
  simple: `Output output = Output.builder()
    .address(receiverAddress)
    .assetName(LOVELACE)
    .qty(adaToLovelace(2.1))
    .build();

TxBuilder txBuilder = output.outputBuilder()
    .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
    .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
    .buildAndSign(txBuilder, SignerProviders.signerFrom(sender));`,
  submit: `Result<String> result = transactionService.submitTransaction(signedTransaction.serialize());`,
  mint: `Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("policy-1", 1);
Asset asset = new Asset("TestCoin", BigInteger.valueOf(50000));

MultiAsset multiAsset = MultiAsset.builder()
    .policyId(policy.getPolicyId())
    .assets(List.of(asset))
    .build();

TxBuilder txBuilder = output.mintOutputBuilder()
    .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
    .andThen(MintCreators.mintCreator(policy.getPolicyScript(), multiAsset))
    .andThen(BalanceTxBuilders.balanceTx(senderAddress, 2));

Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
    .buildAndSign(txBuilder, signerFrom(sender).andThen(signerFrom(policy)));`
}

function FeatureList({ features }) {
  return (
    <ul className="ccl-feature-checklist">
      {features.map((feature, idx) => (
        <li key={idx}>
          <span className="ccl-checkmark">✓</span>
          {feature}
        </li>
      ))}
    </ul>
  )
}

function CodeTabs({ tabs, code, features }) {
  const [activeTab, setActiveTab] = useState(Object.keys(tabs)[0])

  return (
    <div className="ccl-code-with-features">
      <div className="ccl-features-panel">
        <FeatureList features={features[activeTab]} />
      </div>
      <div className="ccl-code-tabs">
        <div className="ccl-tabs-header">
          {Object.entries(tabs).map(([key, label]) => (
            <button
              key={key}
              className={`ccl-tab-btn ${activeTab === key ? 'active' : ''}`}
              onClick={() => setActiveTab(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="ccl-code-content">
          <pre><code>{code[activeTab]}</code></pre>
        </div>
      </div>
    </div>
  )
}

export default function LandingPage() {
  const [mounted, setMounted] = useState(false)
  const [activeSection, setActiveSection] = useState('quicktx')

  useEffect(() => {
    setMounted(true)
  }, [])

  if (!mounted) return null

  return (
    <>
      <style jsx global>{`
        .ccl-landing {
          min-height: 100vh;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", sans-serif;
          background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #334155 100%);
          color: #ffffff;
          overflow-x: hidden;
        }

        .ccl-animated-bg {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background:
            radial-gradient(circle at 20% 30%, rgba(37, 99, 235, 0.15) 0%, transparent 50%),
            radial-gradient(circle at 80% 70%, rgba(139, 92, 246, 0.12) 0%, transparent 50%),
            radial-gradient(circle at 50% 50%, rgba(34, 197, 94, 0.08) 0%, transparent 50%);
          animation: ccl-bg-float 20s ease-in-out infinite;
          z-index: 0;
        }

        @keyframes ccl-bg-float {
          0%, 100% { transform: translateY(0px); opacity: 1; }
          50% { transform: translateY(-20px); opacity: 0.8; }
        }

        .ccl-container {
          max-width: 1400px;
          margin: 0 auto;
          padding: 0 2rem;
          position: relative;
          z-index: 1;
        }

        /* Navigation */
        .ccl-nav {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          background: rgba(15, 23, 42, 0.9);
          backdrop-filter: blur(12px);
          border-bottom: 1px solid rgba(255, 255, 255, 0.1);
          z-index: 100;
          padding: 1rem 0;
        }

        .ccl-nav-content {
          max-width: 1400px;
          margin: 0 auto;
          padding: 0 2rem;
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .ccl-logo {
          display: flex;
          align-items: center;
          gap: 12px;
          text-decoration: none;
          color: #ffffff;
          font-weight: 700;
          font-size: 1.25rem;
        }

        .ccl-nav-links {
          display: flex;
          align-items: center;
          gap: 1.5rem;
        }

        .ccl-nav-link {
          color: #94a3b8;
          text-decoration: none;
          font-weight: 500;
          transition: color 0.2s;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .ccl-nav-link:hover {
          color: #ffffff;
        }

        /* Hero Section */
        .ccl-hero {
          min-height: 100vh;
          display: flex;
          align-items: center;
          padding: 8rem 0 4rem;
        }

        .ccl-hero-content {
          max-width: 900px;
          margin: 0 auto;
          text-align: center;
        }

        .ccl-hero-logo {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 1.5rem;
          margin-bottom: 2rem;
        }

        .ccl-logo-glow {
          position: relative;
        }

        .ccl-logo-glow::before {
          content: '';
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          width: 120px;
          height: 120px;
          background: radial-gradient(circle, rgba(37, 99, 235, 0.4) 0%, transparent 70%);
          border-radius: 50%;
          animation: ccl-glow 3s ease-in-out infinite;
        }

        @keyframes ccl-glow {
          0%, 100% { opacity: 0.4; transform: translate(-50%, -50%) scale(1); }
          50% { opacity: 0.7; transform: translate(-50%, -50%) scale(1.15); }
        }

        .ccl-hero-title {
          font-size: 4rem;
          font-weight: 800;
          margin: 0 0 1.5rem;
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #3b82f6 100%);
          background-clip: text;
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          line-height: 1.1;
        }

        .ccl-hero-subtitle {
          font-size: 1.5rem;
          color: #cbd5e1;
          font-weight: 300;
          line-height: 1.6;
          margin-bottom: 2.5rem;
          max-width: 800px;
          margin-left: auto;
          margin-right: auto;
        }

        .ccl-hero-buttons {
          display: flex;
          gap: 1rem;
          justify-content: center;
          flex-wrap: wrap;
        }

        .ccl-btn {
          padding: 1rem 2rem;
          border-radius: 10px;
          text-decoration: none;
          font-weight: 600;
          font-size: 1rem;
          transition: all 0.3s ease;
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          border: 2px solid transparent;
          cursor: pointer;
        }

        .ccl-btn-primary {
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
          color: #ffffff;
        }

        .ccl-btn-primary:hover {
          transform: translateY(-3px);
          box-shadow: 0 10px 30px rgba(59, 130, 246, 0.4);
        }

        .ccl-btn-secondary {
          background: transparent;
          color: #3b82f6;
          border-color: #3b82f6;
        }

        .ccl-btn-secondary:hover {
          background: #3b82f6;
          color: #ffffff;
          transform: translateY(-3px);
        }

        /* Features Section */
        .ccl-features-section {
          padding: 6rem 0;
        }

        .ccl-section-header {
          text-align: center;
          margin-bottom: 4rem;
        }

        .ccl-section-title {
          font-size: 3rem;
          font-weight: 700;
          margin-bottom: 1rem;
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
          background-clip: text;
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
        }

        .ccl-section-subtitle {
          font-size: 1.2rem;
          color: #94a3b8;
          max-width: 600px;
          margin: 0 auto;
          line-height: 1.6;
        }

        .ccl-features-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
          gap: 2rem;
        }

        .ccl-feature-card {
          background: linear-gradient(135deg, rgba(15, 23, 42, 0.8) 0%, rgba(30, 41, 59, 0.8) 100%);
          border: 1px solid rgba(59, 130, 246, 0.2);
          border-radius: 16px;
          padding: 2.5rem;
          backdrop-filter: blur(10px);
          transition: all 0.3s ease;
          position: relative;
          overflow: hidden;
        }

        .ccl-feature-card::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 3px;
          background: linear-gradient(90deg, #3b82f6, #8b5cf6, #ec4899);
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        .ccl-feature-card:hover {
          transform: translateY(-8px);
          border-color: rgba(59, 130, 246, 0.5);
          box-shadow: 0 20px 50px rgba(59, 130, 246, 0.15);
        }

        .ccl-feature-card:hover::before {
          opacity: 1;
        }

        .ccl-feature-header {
          display: flex;
          align-items: center;
          gap: 1rem;
          margin-bottom: 1.5rem;
        }

        .ccl-feature-icon {
          font-size: 2.5rem;
        }

        .ccl-feature-title {
          font-size: 1.4rem;
          font-weight: 600;
          color: #ffffff;
          margin: 0;
        }

        .ccl-feature-desc {
          color: #cbd5e1;
          line-height: 1.6;
          margin-bottom: 1.5rem;
        }

        .ccl-feature-list {
          list-style: none;
          padding: 0;
          margin: 0;
        }

        .ccl-feature-list li {
          color: #94a3b8;
          padding: 0.4rem 0;
          padding-left: 1.5rem;
          position: relative;
          font-size: 0.95rem;
        }

        .ccl-feature-list li::before {
          content: '✓';
          color: #22c55e;
          position: absolute;
          left: 0;
          font-weight: bold;
        }

        /* API Section */
        .ccl-api-section {
          padding: 6rem 0;
        }

        .ccl-section-tabs {
          display: flex;
          justify-content: center;
          gap: 0.75rem;
          margin-bottom: 3rem;
          flex-wrap: wrap;
        }

        .ccl-section-tab {
          padding: 0.875rem 2rem;
          border: 2px solid rgba(59, 130, 246, 0.3);
          background: transparent;
          border-radius: 10px;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.3s ease;
          color: #94a3b8;
          font-size: 1rem;
        }

        .ccl-section-tab:hover {
          border-color: #3b82f6;
          color: #3b82f6;
        }

        .ccl-section-tab.active {
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
          border-color: transparent;
          color: white;
          box-shadow: 0 8px 25px rgba(59, 130, 246, 0.3);
        }

        .ccl-api-description {
          text-align: center;
          color: #94a3b8;
          margin-bottom: 2rem;
          font-size: 1.1rem;
        }

        .ccl-code-with-features {
          display: grid;
          grid-template-columns: 280px 1fr;
          gap: 1.5rem;
          align-items: start;
        }

        @media (max-width: 900px) {
          .ccl-code-with-features {
            grid-template-columns: 1fr;
          }
        }

        .ccl-features-panel {
          padding: 1.5rem;
          background: linear-gradient(135deg, rgba(15, 23, 42, 0.9) 0%, rgba(30, 41, 59, 0.9) 100%);
          border-radius: 12px;
          border: 1px solid rgba(59, 130, 246, 0.2);
        }

        .ccl-feature-checklist {
          list-style: none;
          padding: 0;
          margin: 0;
        }

        .ccl-feature-checklist li {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
          margin-bottom: 0.75rem;
          font-size: 0.9rem;
          line-height: 1.5;
          color: #cbd5e1;
        }

        .ccl-checkmark {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 20px;
          height: 20px;
          min-width: 20px;
          background: linear-gradient(135deg, #22c55e, #16a34a);
          color: white;
          border-radius: 50%;
          font-size: 0.7rem;
          font-weight: bold;
          margin-top: 2px;
        }

        .ccl-code-tabs {
          background: linear-gradient(135deg, rgba(15, 23, 42, 0.95) 0%, rgba(30, 41, 59, 0.95) 100%);
          border-radius: 12px;
          overflow: hidden;
          border: 1px solid rgba(59, 130, 246, 0.2);
        }

        .ccl-tabs-header {
          display: flex;
          flex-wrap: wrap;
          background: rgba(0, 0, 0, 0.3);
          border-bottom: 1px solid rgba(255, 255, 255, 0.1);
          padding: 0.75rem;
          gap: 0.5rem;
        }

        .ccl-tab-btn {
          padding: 0.625rem 1.25rem;
          background: rgba(59, 130, 246, 0.1);
          border: 1px solid rgba(59, 130, 246, 0.3);
          color: #94a3b8;
          font-size: 0.875rem;
          font-weight: 500;
          cursor: pointer;
          border-radius: 8px;
          transition: all 0.2s;
          white-space: nowrap;
        }

        .ccl-tab-btn:hover {
          background: rgba(59, 130, 246, 0.2);
          border-color: rgba(59, 130, 246, 0.5);
          color: #ffffff;
        }

        .ccl-tab-btn.active {
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
          border-color: transparent;
          color: white;
          box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3);
        }

        .ccl-code-content {
          padding: 1.5rem;
          overflow-x: auto;
        }

        .ccl-code-content pre {
          margin: 0;
        }

        .ccl-code-content code {
          color: #e2e8f0;
          font-family: 'Fira Code', 'Fira Mono', Menlo, Monaco, Consolas, monospace;
          font-size: 0.875rem;
          line-height: 1.7;
          white-space: pre;
        }

        .ccl-api-content {
          animation: ccl-fade-in 0.3s ease;
        }

        @keyframes ccl-fade-in {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }

        /* Community Section */
        .ccl-community-section {
          padding: 5rem 0;
          background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
          border-radius: 24px;
          margin: 4rem 0;
          text-align: center;
        }

        .ccl-community-title {
          font-size: 2.5rem;
          font-weight: 700;
          margin-bottom: 1rem;
          color: #ffffff;
        }

        .ccl-community-desc {
          font-size: 1.2rem;
          color: #94a3b8;
          margin-bottom: 2.5rem;
          max-width: 500px;
          margin-left: auto;
          margin-right: auto;
        }

        .ccl-community-links {
          display: flex;
          justify-content: center;
          gap: 1.5rem;
          flex-wrap: wrap;
        }

        .ccl-community-link {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 1rem 2rem;
          background: linear-gradient(135deg, rgba(15, 23, 42, 0.8) 0%, rgba(30, 41, 59, 0.8) 100%);
          border: 1px solid rgba(59, 130, 246, 0.3);
          border-radius: 12px;
          color: #ffffff;
          text-decoration: none;
          transition: all 0.3s ease;
          font-weight: 500;
        }

        .ccl-community-link:hover {
          transform: translateY(-3px);
          border-color: rgba(59, 130, 246, 0.6);
          box-shadow: 0 10px 30px rgba(59, 130, 246, 0.2);
        }

        .ccl-community-icon {
          font-size: 1.5rem;
        }

        /* Footer */
        .ccl-footer {
          padding: 3rem 0;
          border-top: 1px solid rgba(255, 255, 255, 0.1);
          text-align: center;
        }

        .ccl-footer-text {
          color: #64748b;
          font-size: 0.9rem;
        }

        /* Responsive */
        @media (max-width: 768px) {
          .ccl-hero-title {
            font-size: 2.5rem;
          }

          .ccl-hero-subtitle {
            font-size: 1.2rem;
          }

          .ccl-section-title {
            font-size: 2rem;
          }

          .ccl-features-grid {
            grid-template-columns: 1fr;
          }

          .ccl-nav-links {
            gap: 1rem;
          }

          .ccl-container {
            padding: 0 1rem;
          }
        }
      `}</style>

      <div className="ccl-landing">
        <div className="ccl-animated-bg"></div>

        {/* Navigation */}
        <nav className="ccl-nav">
          <div className="ccl-nav-content">
            <Link href="/" className="ccl-logo">
              <img src="/img/logo_small.svg" alt="BloxBean" width={32} height={32} />
              <span>Cardano Client Lib</span>
            </Link>
            <div className="ccl-nav-links">
              <Link href="/docs" className="ccl-nav-link">Documentation</Link>
              <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-nav-link">
                <svg height="18" width="18" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
                </svg>
                GitHub
              </a>
            </div>
          </div>
        </nav>

        <div className="ccl-container">
          {/* Hero Section */}
          <section className="ccl-hero">
            <div className="ccl-hero-content">
              <div className="ccl-hero-logo">
                <div className="ccl-logo-glow">
                  <img src="/img/logo_small.svg" alt="BloxBean" width={80} height={80} style={{ position: 'relative', zIndex: 2 }} />
                </div>
                <h1 className="ccl-hero-title">Cardano Client Lib</h1>
              </div>

              <p className="ccl-hero-subtitle">
                A Java Library for Simplifying Transactions, Token Minting, Address Derivation,
                and CIP Implementations for Applications on the Cardano Blockchain!
              </p>

              <div className="ccl-hero-buttons">
                <Link href="/docs" className="ccl-btn ccl-btn-primary">
                  Get Started
                </Link>
                <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-btn ccl-btn-secondary">
                  View on GitHub
                </a>
              </div>
            </div>
          </section>

          {/* Features Section */}
          <section className="ccl-features-section">
            <div className="ccl-section-header">
              <h2 className="ccl-section-title">Features</h2>
              <p className="ccl-section-subtitle">
                Everything you need to build powerful Cardano applications in Java
              </p>
            </div>

            <div className="ccl-features-grid">
              {features.map((feature, idx) => (
                <div key={idx} className="ccl-feature-card">
                  <div className="ccl-feature-header">
                    <div className="ccl-feature-icon">{feature.icon}</div>
                    <h3 className="ccl-feature-title">{feature.title}</h3>
                  </div>
                  <p className="ccl-feature-desc">{feature.description}</p>
                  <ul className="ccl-feature-list">
                    {feature.list.map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </section>

          {/* API Section */}
          <section className="ccl-api-section">
            <div className="ccl-section-header">
              <h2 className="ccl-section-title">API Examples</h2>
              <p className="ccl-section-subtitle">
                Explore the different APIs available for building Cardano transactions
              </p>
            </div>

            <div className="ccl-section-tabs">
              <button
                className={`ccl-section-tab ${activeSection === 'keyapis' ? 'active' : ''}`}
                onClick={() => setActiveSection('keyapis')}
              >
                Key APIs
              </button>
              <button
                className={`ccl-section-tab ${activeSection === 'quicktx' ? 'active' : ''}`}
                onClick={() => setActiveSection('quicktx')}
              >
                QuickTx
              </button>
              <button
                className={`ccl-section-tab ${activeSection === 'composable' ? 'active' : ''}`}
                onClick={() => setActiveSection('composable')}
              >
                Composable Functions
              </button>
            </div>

            {activeSection === 'keyapis' && (
              <div className="ccl-api-content">
                <CodeTabs
                  tabs={{ backend: 'Backend Providers', account: 'Account API' }}
                  code={keyApisCode}
                  features={keyApisFeatures}
                />
              </div>
            )}

            {activeSection === 'quicktx' && (
              <div className="ccl-api-content">
                <p className="ccl-api-description">Declarative API for transaction building</p>
                <CodeTabs
                  tabs={{
                    simple: 'Simple Payment',
                    compose: 'Compose & Submit',
                    multiple: 'Multiple Senders',
                    mint: 'Token Minting',
                    utxo: 'UTxO Selection',
                    contract: 'Pay to Contract',
                    unlock: 'Unlock Contract'
                  }}
                  code={quickTxCode}
                  features={quickTxFeatures}
                />
              </div>
            )}

            {activeSection === 'composable' && (
              <div className="ccl-api-content">
                <p className="ccl-api-description">Flexible transaction building with composable functions</p>
                <CodeTabs
                  tabs={{ simple: 'Simple Payments', submit: 'Submit', mint: 'Mint Tokens' }}
                  code={composableCode}
                  features={composableFeatures}
                />
              </div>
            )}

          </section>

          {/* Community Section */}
          <section className="ccl-community-section">
            <h2 className="ccl-community-title">Join the Community</h2>
            <p className="ccl-community-desc">
              Connect with developers, get support, and contribute to the project
            </p>

            <div className="ccl-community-links">
              <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-community-link">
                <span className="ccl-community-icon">🐙</span>
                GitHub
              </a>
              <a href="https://github.com/bloxbean/cardano-client-lib/discussions" target="_blank" rel="noopener noreferrer" className="ccl-community-link">
                <span className="ccl-community-icon">💬</span>
                Discussions
              </a>
              <a href="https://x.com/BloxBean" target="_blank" rel="noopener noreferrer" className="ccl-community-link">
                <span className="ccl-community-icon">𝕏</span>
                Twitter
              </a>
              <Link href="/docs" className="ccl-community-link">
                <span className="ccl-community-icon">📖</span>
                Documentation
              </Link>
            </div>
          </section>

          {/* Footer */}
          <footer className="ccl-footer">
            <p className="ccl-footer-text">
              © {new Date().getFullYear()} BloxBean Project. Built with Nextra.
            </p>
          </footer>
        </div>
      </div>
    </>
  )
}
