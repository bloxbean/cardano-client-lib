import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';
import HomepageExamples from "../components/HomepageExamples";

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  
  React.useEffect(() => {
    // Create floating particles
    const particlesContainer = document.querySelector(`.${styles.particles}`);
    if (particlesContainer) {
      for (let i = 0; i < 20; i++) {
        const particle = document.createElement('div');
        particle.className = styles.particle;
        particle.style.left = `${Math.random() * 100}%`;
        particle.style.animationDelay = `${Math.random() * 15}s`;
        particle.style.animationDuration = `${15 + Math.random() * 10}s`;
        particlesContainer.appendChild(particle);
      }
    }
  }, []);
  
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <div className={styles.particles}></div>
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className={clsx('button', styles.heroButton, styles.primaryButton)}
            to="/docs/quickstart/installation">
            🚀 Getting Started
          </Link>
          <Link
            className={clsx('button', styles.heroButton, styles.secondaryButton)}
            to="/docs/intro">
            📚 Documentation
          </Link>
          <a
            className={clsx('button', styles.heroButton, styles.secondaryButton)}
            href="https://github.com/bloxbean/cardano-client-examples"
            target="_blank"
            rel="noopener noreferrer">
            💻 Examples
          </a>
        </div>
      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title} - Modern Java Library for Cardano`}
      description="Build powerful Cardano blockchain applications with Java. Features transactions, smart contracts, NFTs, staking, and governance support.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <HomepageExamples/>
      </main>
    </Layout>
  );
}