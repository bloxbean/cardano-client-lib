import React from 'react';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import CodeBlock from '@theme/CodeBlock';
import BackendService from '!!raw-loader!./code/backend_service.java';
import AccountService from '!!raw-loader!./code/account.java';
import SimplePayment from '!!raw-loader!./code/simple_payment.java';
import SimpleCompose from '!!raw-loader!./code/simple_compose.java';
import MultipleSenders from '!!raw-loader!./code/multiple_senders.java';
import SimpleTokenMinting from '!!raw-loader!./code/simple_token_mint.java';
import UtxoSelection from '!!raw-loader!./code/utxo_selection_strategy.java';
import PayToContract from '!!raw-loader!./code/payto_script.java';
import SimpleScript from '!!raw-loader!./code/simple_script_unlock.java';

import CFSimplePayments from '!!raw-loader!./code/cf_simple_payments.java';
import CFSubmitTx from '!!raw-loader!./code/cf_submit_tx.java';
import CFMintToken from '!!raw-loader!./code/cf_mint_token.java';

const common_examples = [
    {
        title: "Backend Providers",
        src: BackendService,
        features: [
            "Multiple out-of-box backend providers",
            "Write your own provider using supplier interfaces",
        ]
    },
    {
        title: "Account API",
        src: AccountService,
        features: [
            "Create new mainnet / testnet accounts",
            "Create accounts from mnemonic",
            "Create accounts from account private key",
            "Create accounts using derivation path"
        ]
    }
]

const quickTxExamples = [
    {
        title: "Simple Payment",
        src: SimplePayment,
        features: [
            "Define outputs with Tx api"
        ]
    },
    {
        title: "Compose and Submit",
        src: SimpleCompose,
        features: [
            "Compose transactions and provide signer",
            "Submit transaction to blockchain"
        ]
    },
    {
        title: "Multiple Txs with Multiple Senders",
        src: MultipleSenders,
        features: [
            "Define Txs",
            "Compose and build transaction and provide signers",
            "Submit transaction to blockchain"
        ]
    },
    {
        title: "Simple Token Minting with Metadata",
        src: SimpleTokenMinting,
        features: [
            "Create a policy",
            "Define minting transaction",
            "Provide signers for the account and policy",
            "Compose and submit transaction",
        ]
    },
    {
        title: "Select a different UTxO Selection Strategy",
        src: UtxoSelection,
        features: [
            "Use an out-of-box UTxO selection strategy",
            "Create your own custom UTxO selection strategy by implementing the UtxoSelectionStrategy interface"
        ]
    },
    {
        title: "Pay to a Contract Address",
        src: PayToContract,
        features: [
            "Use Tx api to pay to a contract address"
        ]
    },
    {
        title: "Unlock Amount at a Contract Address",
        src: SimpleScript,
        features: [
            "Use ScriptTx api to define a script unlock tx",
            "Attach validator script to the transaction",
            "Compose and submit transaction"
        ]
    }
]

const cfExamples = [
    {
        title: "Simple Payments",
        src: CFSimplePayments,

        features: [
            "Define outputs",
            "Compose outputs using TxBuilder and out-of-box Composable Functions",
            "Initialize TxBuilderContext with UtxoSupplier and ProtocolParamsSupplier",
            "Build and Sign transaction"
        ]
    },
    {
        title: "Submit Transaction",
        src: CFSubmitTx,

        features: [
            "Get TransactionService from BackendService or create your own TransactionProcessor",
            "Submit transaction to blockchain"
        ]
    },
    {
        title: "Mint Tokens",
        src: CFMintToken,

        features: [
            "Create a policy",
            "Define multi-asset",
            "Define output with multi-asset",
            "Create metadata",
            "Compose transaction using TxBuilder and out-of-box Composable Functions",
            "Balance transaction with balanceTx composable function",
            "Build and sign transaction with sender account and policy key"
        ]
    }
]

export default function HomepageExamples() {
    return (
        <div className="container margin-top--lg">
            <Tabs>
                <TabItem value="Key APIs" title="Provider / Account">
                    {/*<div className="margin-top--lg text--center"><h1>Provider / Account APIs</h1></div>*/}

                    <HomepageExample examples={common_examples}/>
                </TabItem>

                <TabItem value="QuickTx" title="QuickTx">
                    {/*<div className="margin-top--lg text--center"><h1>QuickTx</h1></div>*/}
                    <div className="alert alert--success" role="alert">
                        Simple <strong>declarative</strong> api to build and submit transactions.
                        <br/>
                        Supported in <strong>v0.5.0-alpha.3 or later</strong>
                    </div>
                    <HomepageExample examples={quickTxExamples}/>
                </TabItem>
                <TabItem value="Composable Functions" title="Composable Functions">
                    {/*<div className="margin-top--lg text--center"><h1>Composable Functions</h1></div>*/}
                    <div className="alert alert--success" role="alert">
                        A flexible way to build transactions using out-of-box <strong>composable functions</strong> and
                        with
                        your own <strong>custom functions</strong>.
                    </div>
                    <HomepageExample examples={cfExamples}/>
                </TabItem>
            </Tabs>
            {/*<HomepageExample examples={examples}/>*/}
        </div>
    )
}

function HomepageExample(props) {
    return (
        <div className="container margin-top--lg">
            {props.examples.map((example, index) => (
                <div className="container margin-top--lg">
                    <div className="row">
                        <div className="col">
                            <h3>{example.title}</h3>
                        </div>
                    </div>
                    <div className="row">
                        <div className="col">
                            <div className="col-demo">
                                {renderItem(example)}
                            </div>
                        </div>
                        <div className="col">
                            <div className="col-demo">
                                {example.features.map((feature) => (
                                    <ul className="features">
                                        {/*<IonIcon icon="arrow-forward-outline" />*/}
                                        <li>{feature}</li>
                                    </ul>))
                                }
                            </div>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
}

function renderItem(example) {
    return (
        <CodeBlock language="java">{example.src}</CodeBlock>
    );
}
