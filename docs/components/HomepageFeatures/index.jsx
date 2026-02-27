'use client'

import styles from './styles.module.css'

const FeatureList = [
  {
    title: 'Easy to Use Java Library',
    image: '/img/software-svgrepo-com.svg',
    description: 'Address generation, transfer, token minting, Plutus contract call and more..',
  },
  {
    title: 'Out-of-box Backend Providers',
    image: '/img/cloud-computing-svgrepo-com.svg',
    description: 'Blockfrost, Koios, Ogmios',
  },
  {
    title: 'CIP Implementations',
    image: '/img/certificate-svgrepo-com.svg',
    description: (
      <>
        Java Api for <br />
        CIP-8, CIP-20, CIP-25, CIP-30, CIP-67/68 ...
      </>
    ),
  },
]

function Feature({ image, title, description }) {
  return (
    <div className={styles.featureCard}>
      <div className={styles.featureImage}>
        <img src={image} alt={title} width={200} height={200} />
      </div>
      <div className={styles.featureContent}>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  )
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className={styles.container}>
        <div className={styles.grid}>
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  )
}
