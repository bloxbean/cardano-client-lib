---
title: "Contributing to docs"
description: "How this documentation site is built, and how to add or edit a page."
---

Documentation lives in the `www/` directory of the
[cardano-client-lib repository](https://github.com/bloxbean/cardano-client-lib) and is built with
[Astro](https://astro.build) and [Starlight](https://starlight.astro.build).

Every page has an **Edit page** link at the bottom that opens the right file on GitHub — for a
typo or a small correction, that is the whole workflow.

## Run it locally

Requires Node.js 20 or later.

```bash
cd www
npm install
npm run dev          # http://localhost:4321
```

The dev server hot-reloads on save. `npm run build` produces the static site in `dist/` and then
runs the link checker; `npm run preview` serves that build.

## Where things live

```text
www/
├── astro.config.mjs        site config and the sidebar
├── src/
│   ├── content/docs/       every documentation page (.md)
│   ├── pages/index.astro   the landing page
│   ├── components/         landing-page components
│   └── styles/             theme (starlight.css) and landing (landing.css)
├── scripts/
│   ├── generate-catalog.mjs    /ai/catalog.json, from settings.gradle
│   ├── generate-llms-txt.mjs   /llms.txt and /llms-full.txt
│   ├── llms-integration.mjs    serves those in dev, writes them at build
│   ├── check-links.mjs         internal-link gate, part of `npm run build`
│   └── migrate-nextra.mjs      one-shot import from the old site (historical)
└── public/                 logo, favicon
```

## Add a page

1. Create a `.md` file under `src/content/docs/<section>/`.
2. Give it frontmatter — `title` is required, `description` is strongly encouraged because it
   becomes the page summary in search results and in `llms.txt`:

   ```markdown
   ---
   title: "Your page title"
   description: "One sentence on what the reader gets from this page."
   ---
   ```

3. Add it to the sidebar in `astro.config.mjs`. Ordering is explicit — the array order is the
   sidebar order, so file names do not need numeric prefixes.

Do **not** start the body with an `# H1`. Starlight renders `title` as the page heading, so an H1
would appear twice.

## Formatting

Standard Markdown, plus Starlight's asides:

```markdown
:::note
Neutral context.
:::

:::tip[Custom title]
Something that makes the reader's life easier.
:::

:::caution
A footgun. Use sparingly, or it stops registering.
:::

:::danger
Loss of funds or data.
:::
```

Code blocks take a language and an optional title:

````markdown
```java title="SendAda.java"
Tx tx = new Tx()
        .payToAddress(receiver, Amount.ada(10))
        .from(sender.baseAddress());
```
````

Use `java`, `yaml`, `bash`, `json`, `xml` or `gradle`. Anything else falls back to plain text with
a build warning.

## Links

Use **absolute, site-relative** paths with a trailing slash:

```markdown
[QuickTx overview](/quicktx/overview/)
[a specific section](/txstream/durability/#dedup-scopes)
```

`npm run build` fails on a broken internal link or a missing anchor, so a bad link cannot ship.

## Things that generate themselves

Do not hand-edit these — regenerate them by changing their source:

| Output | Source |
|---|---|
| `/ai/catalog.json` | `settings.gradle` and `gradle.properties` |
| `/llms.txt`, `/llms-full.txt` | every page under `src/content/docs/` |
| Landing-page module and version counts | `/ai/catalog.json` |

This is deliberate: the version and module count on the landing page cannot drift from what the
repository actually builds. If you add a module to `settings.gradle`, describe it in
`MODULE_INFO` inside `scripts/generate-catalog.mjs` — the generator fails loudly on a module it
cannot describe.

## Writing guidance

- **Lead with the reader's problem**, not the API surface. "You need X because Y" beats "X is a
  class that…".
- **QuickTx is the recommended path.** Unless a page is specifically about TxPlan, TxFlow,
  TxStream or the composable functions, examples should use QuickTx.
- **Verify code before you publish it.** Snippets are copied verbatim by readers and ingested by
  agents through `llms-full.txt`. Check signatures against the source tree.
- **Mark preview APIs.** TxPlan, TxFlow, TxStream and the verified structures are preview; say so
  rather than leaving a reader to find out at upgrade time.
- **Say what something is for before how it works.** Several of the deeper pages open with
  architecture; that is right for a reference page and wrong for an entry point.

## Submitting

Fork, branch, commit, and open a pull request against `master`. Run `npm run build` first — it is
the same check CI runs, and it catches broken links and missing sidebar entries.

The previous documentation site (Next.js and Nextra) is still in `docs/` and is no longer the
place to make changes.
