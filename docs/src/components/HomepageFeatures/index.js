import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Easy to Use Java Library',
    Svg: require('@site/static/img/software-svgrepo-com.svg').default,
    description: (
      <>
        Address generation, transfer, token minting, Plutus contract call and more..
      </>
    ),
  },
  {
    title: 'Out-of-box Backend Providers',
    Svg: require('@site/static/img/cloud-computing-svgrepo-com.svg').default,
    description: (
      <>
        Blockfrost, Koios, Ogmios
      </>
    ),
  },
  {
    title: 'CIP Implementations',
    Svg: require('@site/static/img/certificate-svgrepo-com.svg').default,
    description: (
      <>
        Java Api for <br/>
        CIP-8, CIP-20, CIP-25, CIP-30, CIP-67/68 ...
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className={styles.featureCard}>
        <div className="text--center">
          <div className={styles.iconGlow}>
            <Svg className={styles.featureSvg} role="img" />
          </div>
        </div>
        <div className="text--center padding-horiz--md">
          <h3 className={styles.featureTitle}>{title}</h3>
          <p className={styles.featureDescription}>{description}</p>
        </div>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}