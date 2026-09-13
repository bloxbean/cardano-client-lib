# Releasing

> **You do not push `v*` tags by hand.** A release starts with a reviewed PR that changes `version`
> in `gradle.properties`. CI tags the merge, stages Maven Central, and waits for a human to publish.

## How a release flows

```
PR: bump `version` (and set github_release) in gradle.properties
  └─ code-owner review ─► merge to master (or release/**)
        └─ tag-release.yml ── App token ──► tag v<version> on the merge commit
              └─ release.yml
                    ├─ publish        : tag == version check, build, sign, upload a USER_MANAGED
                    │                   Central Portal deployment → deployment id in the run summary
                    └─ github-release : only if github_release = true
                                        GitHub release "<version>" + load-tools jar
maintainer ─► publish-central.yml (deployment id) ─► `release` environment approval ─► Maven Central
         or ─► Central Portal UI → Publish
```

| Workflow | Trigger | What it does |
|---|---|---|
| [`tag-release.yml`](.github/workflows/tag-release.yml) | push to `master` / `release/**` touching `gradle.properties`; manual run by a release owner | Tags `v<version>` with the `bloxbean-release-tagger` App. Skips SNAPSHOT versions and tags that already exist. |
| [`release.yml`](.github/workflows/release.yml) | `v*` tag | Builds, signs, uploads a Central deployment that stops at VALIDATED. Optional GitHub release. |
| [`publish-central.yml`](.github/workflows/publish-central.yml) | manual run from `master`, input `deploymentId` | After `release` approval, publishes the staged deployment. **Irreversible.** |
| [`snapshot_manual.yml`](.github/workflows/snapshot_manual.yml) | manual run | Publishes a `-SNAPSHOT` version to the Central snapshot repository. No approval. |
| [`docs-deploy.yml`](.github/workflows/docs-deploy.yml) | push to `master` touching `docs/**`, `www/**`, `settings.gradle`, `gradle.properties`; manual run | Builds `docs/` (site root) and `www/` (`/next/`), pushes to `gh-pages`. The site updates after `github-pages` approval. |

The tag is pushed with a GitHub App token, not `GITHUB_TOKEN`: GitHub does not fire `on: push`
workflows for refs pushed by `GITHUB_TOKEN`, so such a tag would never start `release.yml`.

## Release checklist

1. [ ] Open a PR that sets `version` in `gradle.properties` to the release version (no `-SNAPSHOT`).
       Decide `github_release` (normally `true`). Get it approved by a release owner and merge it.
2. [ ] `tag-release.yml` pushes `v<version>`. `release.yml` starts on the tag.
3. [ ] When `release.yml` finishes, copy the **deployment id** from its run summary
       ("Maven Central deployment staged - NOT published").
4. [ ] Optionally inspect the deployment at https://central.sonatype.com/publishing/deployments.
5. [ ] Run **Publish staged deployment to Maven Central** (`publish-central.yml`) from `master` with
       that id, and get it approved in the `release` environment. Or press **Publish** in the portal.
6. [ ] If `github_release = true`, edit the notes of the GitHub release `<version>`. It is created
       with the load-tools jar and empty notes. Final versions (`X.Y.Z`) are normal releases; anything
       with a suffix is a pre-release.
7. [ ] Open a PR that moves `version` to the next `-SNAPSHOT` (tag-release skips SNAPSHOT versions).
       CI never commits this for you: the branch ruleset rejects direct pushes.

Artifacts appear on Maven Central within a few hours of publishing. A published version can never be
replaced or re-uploaded.

### Maintenance releases

Same flow on a `release/**` branch (e.g. `release/0.7.x`). The tag runs that commit's own
`release.yml` and build, so the branch must already contain this pipeline. The deployment is still
published from `master` through `publish-central.yml`: the deployment id is not tied to a branch.
The GitHub release uses `make_latest: legacy`, so a maintenance release doesn't take the "Latest"
badge from a newer line.

### Rehearsing the pipeline

With `github_release = false`, a tag does nothing public: it builds, signs and stages Central only.

1. PR a throwaway version such as `0.8.0-pre6-dev1` with `github_release = false`, and merge it.
2. Check that `tag-release.yml` tagged it and `release.yml` staged a VALIDATED deployment.
3. **Drop** the deployment in the Central Portal. Don't publish it.
4. PR the real version afterwards.

## Who can do what

| | satran004 (org admin) | fabianbormann, matiwinnetou |
|---|---|---|
| Merge a PR changing `gradle.properties` | Yes, with or without review (admin PR bypass) | Needs another release owner's approval |
| Approve `publish-central.yml` | Yes, including own runs (admin bypass) | Yes, but not for a run they started (prevent self-review) |
| Run `tag-release.yml` manually | Yes | Yes (tags only the version already on the branch) |
| Approve a docs deployment (`github-pages`) | Yes (admin bypass) | Yes, but not their own |

When satran004 is away, Fabian and Mati can still release, each approving the other's PR and publish.

## Recovery

| Problem | Fix |
|---|---|
| Version merged, but no tag | If the `tag-release.yml` run failed, **Re-run jobs**. If it never ran (e.g. `[skip ci]` in the merge commit), a release owner runs `tag-release.yml` manually on that branch. Both are idempotent. |
| `release.yml` failed before or during the upload | Fix the cause and re-run the workflow. If a deployment was created anyway, drop it in the portal. |
| Upload succeeded, `github-release` job failed | Re-run only the failed job. Central is not touched. |
| Tag does not match `version` | `release.yml` fails and uploads nothing. Tags can't be moved or deleted (tag ruleset), so release the next version. |
| Wrong content staged | Drop the deployment in the portal and release the next version. Never publish it. |
| `publish-central.yml` rejected the id | It must be the UUID from the release run summary. The check runs before the approval gate. |

Don't pre-create a draft GitHub release for a tag: the release job publishes an existing draft when it
attaches the jar.

## Documentation

`docs-deploy.yml` replaces the old `rel-docs*` tag trigger. Pull requests that touch the docs only
build them. A push to `master`, or a manual run on `master`, pushes the assembled site to the
`gh-pages` branch. GitHub's `pages-build-deployment` then waits for approval in the `github-pages`
environment before cardano-client.dev changes. Several docs merges in a row queue several
deployments: approve the newest and reject the older ones.

## One-time repository settings

Configured in GitHub settings, not code. Keep them in sync with `.github/CODEOWNERS` and the
allow-list in `tag-release.yml`.

- **`protected-branches` ruleset** (`master`, `release/**`): require a PR, 1 approval, **require
  review from Code Owners**, block deletion and force pushes. Bypass: Organization admin, pull
  requests only.
- **`V_tags` ruleset** (`refs/tags/v*`): restrict creation, update, deletion. Bypass:
  `bloxbean-release-tagger` App and, for now, the Maintain role. *Recommended, not yet decided:* once
  `tag-release.yml` has created a release tag, replace the Maintain role with Organization admin, so
  only the App (and an org admin, for recovery) can create `v*` tags.
- **`bloxbean-release-tagger` GitHub App** (Contents: read & write) installed on this repository.
  Org secrets `RELEASE_APP_ID` and `RELEASE_APP_PRIVATE_KEY`.
- **`release` environment:** required reviewers satran004, fabianbormann, matiwinnetou. Prevent
  self-review on. Administrators may bypass. Deployment branches: `master` only.
- **`github-pages` environment:** the same reviewers and rules. Deployment branches: `gh-pages`.
- **Org secrets** used by the release: `OSSRH_USERNAME` / `OSSRH_TOKEN` (Central Portal user token),
  `SIGNING_KEY` (base64 secret keyring), `SIGNING_KEY_ID`, `SIGNING_PASSWORD`.
