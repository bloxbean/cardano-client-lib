'use client'

import { useState } from 'react'
import styles from './styles.module.css'

// Code examples as strings (in Nextra 4, we'll include these inline for simplicity)
const backendService = `// Blockfrost Backend
BackendService backendService =
    new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);

// Koios Backend
BackendService backendService =
    new KoiosBackendService(Constants.KOIOS_PREPROD_URL);`

const account = `// Create new account
Account account = new Account(Networks.testnet());
String baseAddress = account.baseAddress();
String mnemonic = account.mnemonic();

// From existing mnemonic
Account account = new Account(Networks.testnet(), mnemonic);`

const simplePayment = `Output output1 = Output.builder()
    .address(receiverAddress1)
    .assetName(LOVELACE)
    .qty(adaToLovelace(10))
    .build();

Output output2 = Output.builder()
    .address(receiverAddress2)
    .assetName(LOVELACE)
    .qty(adaToLovelace(20))
    .build();`

const simpleCompose = `TxBuilder txBuilder = output1.outputBuilder()
    .and(output2.outputBuilder())
    .buildInputs(createFromSender(senderAddress, senderAddress))
    .andThen(balanceTx(senderAddress, 1));

Transaction signedTx = TxBuilderContext
    .init(utxoSupplier, protocolParamsSupplier)
    .buildAndSign(txBuilder, signerFrom(senderAccount));`

const simpleTokenMint = `// Create policy
Policy policy = PolicyScripts.createMultiSigScriptAtLeastPolicy(
    "my-policy", 1, 1);
String policyId = policy.getPolicyId();

// Mint token
Tx tx = new Tx()
    .mintAssets(policy.getPolicyScript(),
        new Asset("MyToken", BigInteger.valueOf(1000)))
    .payToAddress(receiverAddress, Amount.asset(policyId, "MyToken", 1000))
    .from(senderAddress);`

const cfSimplePayments = `Output output = Output.builder()
    .address(receiverAddress)
    .assetName(LOVELACE)
    .qty(adaToLovelace(10))
    .build();

TxBuilder txBuilder = output.outputBuilder()
    .buildInputs(createFromSender(senderAddress, senderAddress))
    .andThen(balanceTx(senderAddress, 1));`

const quickTxExample = `Tx tx = new Tx()
    .payToAddress(receiver1, Amount.ada(1.5))
    .payToAddress(receiver2, Amount.ada(2.5))
    .attachMetadata(MessageMetadata.create().add("Hello!"))
    .from(senderAddress);

Result<String> result = quickTxBuilder.compose(tx)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait(System.out::println);`

const commonExamples = [
  {
    title: 'Backend Providers',
    src: backendService,
    features: [
      'Multiple out-of-box backend providers',
      'Write your own provider using supplier interfaces',
    ],
  },
  {
    title: 'Account API',
    src: account,
    features: [
      'Create new mainnet / testnet accounts',
      'Create accounts from mnemonic',
      'Create accounts from account private key',
      'Create accounts using derivation path',
    ],
  },
]

const quickTxExamples = [
  {
    title: 'Simple Payment',
    src: quickTxExample,
    features: ['Simple declarative API', 'Automatic fee calculation', 'Built-in signing'],
  },
  {
    title: 'Simple Token Minting',
    src: simpleTokenMint,
    features: [
      'Create a policy',
      'Define minting transaction',
      'Provide signers for the account and policy',
    ],
  },
]

const cfExamples = [
  {
    title: 'Define Outputs',
    src: simplePayment,
    features: ['Define outputs with Output builder', 'Support for ADA and native tokens'],
  },
  {
    title: 'Compose and Submit',
    src: simpleCompose,
    features: [
      'Compose transactions using TxBuilder',
      'Balance transaction automatically',
      'Sign and submit to blockchain',
    ],
  },
]

function CodeExample({ example }) {
  return (
    <div className={styles.example}>
      <h3>{example.title}</h3>
      <div className={styles.exampleContent}>
        <pre className={styles.codeBlock}>
          <code>{example.src}</code>
        </pre>
        <ul className={styles.featuresList}>
          {example.features.map((feature, idx) => (
            <li key={idx}>{feature}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

export default function HomepageExamples() {
  const [activeTab, setActiveTab] = useState(0)

  const tabs = [
    { label: 'Provider / Account', examples: commonExamples },
    { label: 'QuickTx', examples: quickTxExamples, alert: 'Simple declarative api to build and submit transactions.' },
    { label: 'Composable Functions', examples: cfExamples, alert: 'A flexible way to build transactions using out-of-box composable functions and with your own custom functions.' },
  ]

  return (
    <div className={styles.container}>
      <div className={styles.tabs}>
        {tabs.map((tab, idx) => (
          <button
            key={idx}
            className={`${styles.tab} ${activeTab === idx ? styles.activeTab : ''}`}
            onClick={() => setActiveTab(idx)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className={styles.tabContent}>
        {tabs[activeTab].alert && (
          <div className={styles.alert}>{tabs[activeTab].alert}</div>
        )}
        {tabs[activeTab].examples.map((example, idx) => (
          <CodeExample key={idx} example={example} />
        ))}
      </div>
    </div>
  )
}
