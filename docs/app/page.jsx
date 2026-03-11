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
    list: ['Blockfrost integration', 'Koios support', 'Ogmios/Kupo backends', 'Yac Store / Yaci DevKit support with Blockfrost Provider', 'Custom provider support']
  },
  {
    icon: '📋',
    title: 'CIP Implementations',
    description: 'Built-in support for Cardano Improvement Proposals to ensure compatibility and standards compliance.',
    list: ['CIP-8 Message signing', 'CIP-20 Transaction metadata', 'CIP-25 NFT metadata', 'CIP-30, CIP-67/68 and more']
  }
]

const keyApisFeatures = {
  backend: ['Multiple out-of-box backend providers', 'Write your own provider using supplier interfaces'],
  account: ['Create new mainnet/testnet accounts', 'Create accounts from mnemonic', 'Create accounts from account private key', 'Create accounts using derivation path']
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
        <li key={idx}><span className="ccl-checkmark">✓</span>{feature}</li>
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
            <button key={key} className={`ccl-tab-btn ${activeTab === key ? 'active' : ''}`} onClick={() => setActiveTab(key)}>{label}</button>
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

  useEffect(() => { setMounted(true) }, [])
  if (!mounted) return null

  return (
    <>
      <style jsx global>{`
        .ccl-landing {
          min-height: 100vh;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", sans-serif;
          background: #faf8f5;
          color: #1c1917;
          overflow-x: hidden;
        }

        .ccl-animated-bg {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background:
            radial-gradient(circle at 20% 30%, rgba(217, 119, 6, 0.06) 0%, transparent 50%),
            radial-gradient(circle at 80% 70%, rgba(180, 83, 9, 0.05) 0%, transparent 50%),
            radial-gradient(circle at 50% 50%, rgba(245, 158, 11, 0.03) 0%, transparent 50%);
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

        .ccl-nav {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          background: rgba(250, 248, 245, 0.92);
          backdrop-filter: blur(12px);
          border-bottom: 1px solid #e7e5e4;
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
          color: #1c1917;
          font-weight: 700;
          font-size: 1.25rem;
        }

        .ccl-nav-links {
          display: flex;
          align-items: center;
          gap: 1.5rem;
        }

        .ccl-nav-link {
          color: #78716c;
          text-decoration: none;
          font-weight: 500;
          transition: color 0.2s;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .ccl-nav-link:hover { color: #1c1917; }

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
          background: radial-gradient(circle, rgba(245, 158, 11, 0.2) 0%, transparent 70%);
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
          background: linear-gradient(135deg, #b45309 0%, #d97706 50%, #b45309 100%);
          background-clip: text;
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          line-height: 1.1;
        }

        .ccl-hero-subtitle {
          font-size: 1.5rem;
          color: #57534e;
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
          background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
          color: #ffffff;
        }

        .ccl-btn-primary:hover {
          transform: translateY(-3px);
          box-shadow: 0 10px 30px rgba(217, 119, 6, 0.3);
        }

        .ccl-btn-secondary {
          background: transparent;
          color: #b45309;
          border-color: #b45309;
        }

        .ccl-btn-secondary:hover {
          background: #b45309;
          color: #ffffff;
          transform: translateY(-3px);
        }

        .ccl-features-section { padding: 6rem 0; }

        .ccl-section-header {
          text-align: center;
          margin-bottom: 4rem;
        }

        .ccl-section-title {
          font-size: 3rem;
          font-weight: 700;
          margin-bottom: 1rem;
          background: linear-gradient(135deg, #b45309 0%, #d97706 100%);
          background-clip: text;
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
        }

        .ccl-section-subtitle {
          font-size: 1.2rem;
          color: #78716c;
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
          background: #fffbf5;
          border: 1px solid #e7e5e4;
          border-radius: 16px;
          padding: 2.5rem;
          transition: all 0.3s ease;
          position: relative;
          overflow: hidden;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
        }

        .ccl-feature-card::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 3px;
          background: linear-gradient(90deg, #d97706, #f59e0b, #ea580c);
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        .ccl-feature-card:hover {
          transform: translateY(-8px);
          border-color: #fcd34d;
          box-shadow: 0 20px 50px rgba(217, 119, 6, 0.08);
        }

        .ccl-feature-card:hover::before { opacity: 1; }

        .ccl-feature-header {
          display: flex;
          align-items: center;
          gap: 1rem;
          margin-bottom: 1.5rem;
        }

        .ccl-feature-icon { font-size: 2.5rem; }
        .ccl-feature-title { font-size: 1.4rem; font-weight: 600; color: #1c1917; margin: 0; }
        .ccl-feature-desc { color: #57534e; line-height: 1.6; margin-bottom: 1.5rem; }
        .ccl-feature-list { list-style: none; padding: 0; margin: 0; }

        .ccl-feature-list li {
          color: #78716c;
          padding: 0.4rem 0;
          padding-left: 1.5rem;
          position: relative;
          font-size: 0.95rem;
        }

        .ccl-feature-list li::before {
          content: '✓';
          color: #d97706;
          position: absolute;
          left: 0;
          font-weight: bold;
        }

        .ccl-api-section { padding: 6rem 0; }

        .ccl-section-tabs {
          display: flex;
          justify-content: center;
          gap: 0.75rem;
          margin-bottom: 3rem;
          flex-wrap: wrap;
        }

        .ccl-section-tab {
          padding: 0.875rem 2rem;
          border: 2px solid #e7e5e4;
          background: transparent;
          border-radius: 10px;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.3s ease;
          color: #78716c;
          font-size: 1rem;
        }

        .ccl-section-tab:hover { border-color: #d97706; color: #d97706; }

        .ccl-section-tab.active {
          background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
          border-color: transparent;
          color: white;
          box-shadow: 0 8px 25px rgba(217, 119, 6, 0.25);
        }

        .ccl-api-description {
          text-align: center;
          color: #78716c;
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
          .ccl-code-with-features { grid-template-columns: 1fr; }
        }

        .ccl-features-panel {
          padding: 1.5rem;
          background: #fef7ed;
          border-radius: 12px;
          border: 1px solid #e7e5e4;
        }

        .ccl-feature-checklist { list-style: none; padding: 0; margin: 0; }

        .ccl-feature-checklist li {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
          margin-bottom: 0.75rem;
          font-size: 0.9rem;
          line-height: 1.5;
          color: #57534e;
        }

        .ccl-checkmark {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 20px;
          height: 20px;
          min-width: 20px;
          background: linear-gradient(135deg, #d97706, #b45309);
          color: white;
          border-radius: 50%;
          font-size: 0.7rem;
          font-weight: bold;
          margin-top: 2px;
        }

        .ccl-code-tabs {
          background: #292524;
          border-radius: 12px;
          overflow: hidden;
          border: 1px solid #e7e5e4;
        }

        .ccl-tabs-header {
          display: flex;
          flex-wrap: wrap;
          background: #1c1917;
          border-bottom: 1px solid rgba(255, 255, 255, 0.1);
          padding: 0.75rem;
          gap: 0.5rem;
        }

        .ccl-tab-btn {
          padding: 0.625rem 1.25rem;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid rgba(255, 255, 255, 0.15);
          color: #a8a29e;
          font-size: 0.875rem;
          font-weight: 500;
          cursor: pointer;
          border-radius: 8px;
          transition: all 0.2s;
          white-space: nowrap;
        }

        .ccl-tab-btn:hover { background: rgba(255, 255, 255, 0.1); color: #ffffff; }

        .ccl-tab-btn.active {
          background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
          border-color: transparent;
          color: white;
          box-shadow: 0 4px 15px rgba(217, 119, 6, 0.3);
        }

        .ccl-code-content { padding: 1.5rem; overflow-x: auto; }
        .ccl-code-content pre { margin: 0; }

        .ccl-code-content code {
          color: #e7e5e4;
          font-family: 'Fira Code', 'Fira Mono', Menlo, Monaco, Consolas, monospace;
          font-size: 0.875rem;
          line-height: 1.7;
          white-space: pre;
        }

        .ccl-api-content { animation: ccl-fade-in 0.3s ease; }

        @keyframes ccl-fade-in {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }

        .ccl-community-section {
          padding: 5rem 0;
          background: #fef7ed;
          border: 1px solid #e7e5e4;
          border-radius: 24px;
          margin: 4rem 0;
          text-align: center;
        }

        .ccl-community-title { font-size: 2.5rem; font-weight: 700; margin-bottom: 1rem; color: #1c1917; }

        .ccl-community-desc {
          font-size: 1.2rem;
          color: #78716c;
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
          background: #fffbf5;
          border: 1px solid #e7e5e4;
          border-radius: 12px;
          color: #1c1917;
          text-decoration: none;
          transition: all 0.3s ease;
          font-weight: 500;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
        }

        .ccl-community-link:hover {
          transform: translateY(-3px);
          border-color: #fcd34d;
          box-shadow: 0 10px 30px rgba(217, 119, 6, 0.08);
        }

        .ccl-community-icon { font-size: 1.5rem; }

        .ccl-footer {
          padding: 3rem 0;
          border-top: 1px solid #e7e5e4;
          text-align: center;
        }

        .ccl-footer-text { color: #a8a29e; font-size: 0.9rem; }

        @media (max-width: 768px) {
          .ccl-hero-title { font-size: 2.5rem; }
          .ccl-hero-subtitle { font-size: 1.2rem; }
          .ccl-section-title { font-size: 2rem; }
          .ccl-features-grid { grid-template-columns: 1fr; }
          .ccl-nav-links { gap: 1rem; }
          .ccl-container { padding: 0 1rem; }
        }
      `}</style>

      <div className="ccl-landing">
        <div className="ccl-animated-bg"></div>
        <nav className="ccl-nav">
          <div className="ccl-nav-content">
            <Link href="/" className="ccl-logo">
              <img src="/img/logo_small.svg" alt="BloxBean" width={32} height={32} />
              <span>Cardano Client Lib</span>
            </Link>
            <div className="ccl-nav-links">
              <Link href="/docs" className="ccl-nav-link">Documentation</Link>
              <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-nav-link" title="GitHub">
                <svg height="20" width="20" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
              </a>
              <a href="https://discord.gg/JtQ54MSw6p" target="_blank" rel="noopener noreferrer" className="ccl-nav-link" title="Discord">
                <svg height="20" width="20" viewBox="0 0 24 24" fill="currentColor"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.095 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.095 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"/></svg>
              </a>
              <a href="https://x.com/BloxBean" target="_blank" rel="noopener noreferrer" className="ccl-nav-link" title="X">
                <svg height="18" width="18" viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>
              </a>
            </div>
          </div>
        </nav>

        <div className="ccl-container">
          <section className="ccl-hero">
            <div className="ccl-hero-content">
              <div className="ccl-hero-logo">
                <div className="ccl-logo-glow">
                  <img src="/img/logo_small.svg" alt="BloxBean" width={80} height={80} style={{ position: 'relative', zIndex: 2 }} />
                </div>
                <h1 className="ccl-hero-title">Cardano Client Lib</h1>
              </div>
              <p className="ccl-hero-subtitle">A Java Library for Simplifying Transactions, Token Minting, Address Derivation, and CIP Implementations for Applications on the Cardano Blockchain!</p>
              <div className="ccl-hero-buttons">
                <Link href="/docs" className="ccl-btn ccl-btn-primary">Get Started</Link>
                <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-btn ccl-btn-secondary">View on GitHub</a>
              </div>
            </div>
          </section>

          <section className="ccl-features-section">
            <div className="ccl-section-header">
              <h2 className="ccl-section-title">Features</h2>
              <p className="ccl-section-subtitle">Everything you need to build powerful Cardano applications in Java</p>
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
                    {feature.list.map((item, i) => (<li key={i}>{item}</li>))}
                  </ul>
                </div>
              ))}
            </div>
          </section>

          <section className="ccl-api-section">
            <div className="ccl-section-header">
              <h2 className="ccl-section-title">API Examples</h2>
              <p className="ccl-section-subtitle">Explore the different APIs available for building Cardano transactions</p>
            </div>
            <div className="ccl-section-tabs">
              <button className={`ccl-section-tab ${activeSection === 'keyapis' ? 'active' : ''}`} onClick={() => setActiveSection('keyapis')}>Key APIs</button>
              <button className={`ccl-section-tab ${activeSection === 'quicktx' ? 'active' : ''}`} onClick={() => setActiveSection('quicktx')}>QuickTx</button>
              <button className={`ccl-section-tab ${activeSection === 'composable' ? 'active' : ''}`} onClick={() => setActiveSection('composable')}>Composable Functions</button>
            </div>
            {activeSection === 'keyapis' && (<div className="ccl-api-content"><CodeTabs tabs={{ backend: 'Backend Providers', account: 'Account API' }} code={keyApisCode} features={keyApisFeatures} /></div>)}
            {activeSection === 'quicktx' && (<div className="ccl-api-content"><p className="ccl-api-description">Declarative API for transaction building</p><CodeTabs tabs={{ simple: 'Simple Payment', compose: 'Compose & Submit', multiple: 'Multiple Senders', mint: 'Token Minting', utxo: 'UTxO Selection', contract: 'Pay to Contract', unlock: 'Unlock Contract' }} code={quickTxCode} features={quickTxFeatures} /></div>)}
            {activeSection === 'composable' && (<div className="ccl-api-content"><p className="ccl-api-description">Flexible transaction building with composable functions</p><CodeTabs tabs={{ simple: 'Simple Payments', submit: 'Submit', mint: 'Mint Tokens' }} code={composableCode} features={composableFeatures} /></div>)}
          </section>

          <section className="ccl-community-section">
            <h2 className="ccl-community-title">Join the Community</h2>
            <p className="ccl-community-desc">Connect with developers, get support, and contribute to the project</p>
            <div className="ccl-community-links">
              <a href="https://github.com/bloxbean/cardano-client-lib" target="_blank" rel="noopener noreferrer" className="ccl-community-link"><span className="ccl-community-icon"><svg height="20" width="20" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg></span>GitHub</a>
              <a href="https://github.com/bloxbean/cardano-client-lib/discussions" target="_blank" rel="noopener noreferrer" className="ccl-community-link"><span className="ccl-community-icon">💬</span>Discussions</a>
              <a href="https://x.com/BloxBean" target="_blank" rel="noopener noreferrer" className="ccl-community-link"><span className="ccl-community-icon"><svg height="18" width="18" viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg></span>X</a>
              <Link href="/docs" className="ccl-community-link"><span className="ccl-community-icon">📖</span>Documentation</Link>
            </div>
          </section>

          <footer className="ccl-footer">
            <p className="ccl-footer-text">© {new Date().getFullYear()} BloxBean Project. Built with Nextra.</p>
          </footer>
        </div>
      </div>
    </>
  )
}
