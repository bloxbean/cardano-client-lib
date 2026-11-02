#!/usr/bin/env bash
# Fails unless this commit's push CI succeeded: every workflow file named in CI_WORKFLOWS (e.g. "build.yml
# integration.yml") must have a successful push run for $GITHUB_SHA. The BloxBean repository workflows publish only
# such a commit instead of repeating its tests. Needs GH_TOKEN with `actions: read`. Standard: bloxbean/release-ops
# docs/12-bloxbean-maven-repository.md and templates/scripts/; keep it identical across repositories.
set -euo pipefail

for workflow in ${CI_WORKFLOWS:?}; do
  runs="repos/$GITHUB_REPOSITORY/actions/workflows/$workflow/runs"
  passed=$(gh api "$runs?head_sha=$GITHUB_SHA&event=push&status=success" --jq '.total_count')
  if [[ "$passed" == 0 ]]; then
    echo "::error::$workflow has no successful push run for $GITHUB_SHA. Run this again once the push CI is green." >&2
    exit 1
  fi
done
echo "$CI_WORKFLOWS passed for $GITHUB_SHA"
