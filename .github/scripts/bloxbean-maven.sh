#!/usr/bin/env bash
# Publishes the build's Maven publications to the BloxBean Maven repository (an S3-compatible bucket served at
# repo.bloxbean.org). Shared by bloxbean-snapshot.yml (snapshots), bloxbean-snapshot-cleanup.yml (discover) and
# release.yml (releases). Standard: bloxbean/release-ops docs/12-bloxbean-maven-repository.md and
# templates/scripts/; keep it identical across repositories and configure it by env.
#
#   discover [gradle args]  stage every publication once and record the artifactIds this build owns
#   seed                    seed $STAGING_DIR with each artifact's published maven-metadata.xml
#   upload                  upload the staged artifactIds: artifacts, then version metadata, then version lists
#   verify-public           check the public URL serves the uploaded metadata and POMs byte for byte
#   consume                 resolve CONSUMER_DEPENDENCIES from the public URL with a throwaway Gradle project
#
# Environment: GROUP_PATH, STAGING_DIR (absolute), BUCKET, REPOSITORY_PREFIX (maven/snapshots or maven/releases),
# PUBLIC_REPOSITORY_URL, VERSION, ARTIFACTS (from discover), RUNNER_TEMP, and the AWS CLI's endpoint and
# credentials. consume also reads CONSUMER_DEPENDENCIES ("group:artifact ..."), optional CONSUMER_PLATFORM (a
# BOM's "group:artifact") and CONSUMER_REQUIRED (artifactIds whose jars must resolve). Only keys under
# $REPOSITORY_PREFIX/$GROUP_PATH/<staged artifactId>/ are ever written; nothing is deleted. A group can be shared
# by several repositories (com/bloxbean/cardano), so everything is scoped to this build's own artifactIds.
set -euo pipefail

# Both are removed and recreated below; refuse to start without them.
: "${STAGING_DIR:?}" "${RUNNER_TEMP:?}"
SEED_DIR="$RUNNER_TEMP/maven-seed"

# Stages every publication to learn which artifactIds this build owns: those, and only those, are seeded and
# uploaded. The scope check ties that set to the Maven Central deployment scope.
discover() {
  ./gradlew verifyMavenReleasePublicationScope publishAllPublicationsToStagingRepository \
    -PstagingRepository="$STAGING_DIR" "$@" --stacktrace | tee "$RUNNER_TEMP/discover.log"
  local scope owned count
  scope=$(grep -oE 'Central deployment scope: [0-9]+ projects' "$RUNNER_TEMP/discover.log" | grep -oE '[0-9]+')
  owned=$(find "$STAGING_DIR/$GROUP_PATH" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort)
  count=$(wc -l <<< "$owned")
  if [[ "$count" != "$scope" ]]; then
    echo "::error::Staged $count artifacts, but the Central deployment scope has $scope projects." >&2
    exit 1
  fi
  echo "artifacts=$(tr '\n' ' ' <<< "$owned")" >> "$GITHUB_OUTPUT"
  echo "$count artifacts: $(tr '\n' ' ' <<< "$owned")"
}

# Gradle appends its version to an existing maven-metadata.xml in the target repository, so the staging directory
# is emptied and seeded with each artifact's published metadata. Read through the S3 API, not the public URL, so
# the copy is the stored object rather than an edge-cached one. A missing object is an artifact's first
# publication; any other error stops the run instead of silently replacing the published version list. A copy of
# every seed lets the verification prove that no published version was dropped. A release version that already has
# any object - even from a run that stopped before its metadata - is refused: a release is never replaced.
seed() {
  rm -rf "$STAGING_DIR" "$SEED_DIR"
  mkdir -p "$SEED_DIR"
  local artifact key existing
  for artifact in $ARTIFACTS; do
    if [[ "$VERSION" != *-SNAPSHOT ]]; then
      existing=$(aws s3api list-objects-v2 --bucket "$BUCKET" --max-keys 1 --query 'KeyCount' --output text \
        --prefix "$REPOSITORY_PREFIX/$GROUP_PATH/$artifact/$VERSION/")
      if [[ "$existing" != 0 ]]; then
        echo "::error::$artifact $VERSION already has objects in $REPOSITORY_PREFIX; a release is never replaced." >&2
        exit 1
      fi
    fi
    key="$REPOSITORY_PREFIX/$GROUP_PATH/$artifact/maven-metadata.xml"
    if aws s3api head-object --bucket "$BUCKET" --key "$key" > /dev/null 2> "$RUNNER_TEMP/head.err"; then
      aws s3 cp --only-show-errors "s3://$BUCKET/$key" "$SEED_DIR/$artifact.xml"
      install -D -m 644 "$SEED_DIR/$artifact.xml" "$STAGING_DIR/$GROUP_PATH/$artifact/maven-metadata.xml"
      echo "$artifact: appending to the published metadata"
    elif grep -q '(404)' "$RUNNER_TEMP/head.err"; then
      echo "$artifact: first publication"
    else
      cat "$RUNNER_TEMP/head.err" >&2
      exit 1
    fi
  done
}

# Artifacts, then each version's metadata (snapshots only), then each artifact's version list, so a reader never
# sees metadata naming something that is not there yet. A filter pattern's `*` also matches `/`, and the last
# matching filter wins: `*/*/maven-metadata.xml*` is the version level only. `cp` without --delete only adds or
# replaces the staged keys. Metadata is marked no-cache so an edge cache rule on the domain can never serve a stale
# version list. A run stopped part-way is repaired by running it again.
upload() {
  local source="$STAGING_DIR/$GROUP_PATH/" target="s3://$BUCKET/$REPOSITORY_PREFIX/$GROUP_PATH/"
  aws s3 cp "$source" "$target" --recursive --only-show-errors \
    --exclude '*maven-metadata.xml*'
  aws s3 cp "$source" "$target" --recursive --only-show-errors --cache-control no-cache \
    --exclude '*' --include '*/*/maven-metadata.xml*'
  aws s3 cp "$source" "$target" --recursive --only-show-errors --cache-control no-cache \
    --exclude '*' --include '*maven-metadata.xml*' --exclude '*/*/maven-metadata.xml*'
  echo "Uploaded $(find "$source" -type f | wc -l) files to $REPOSITORY_PREFIX/$GROUP_PATH/"
}

# Every artifact's version list, its version-level metadata (snapshots) and its POM must be served byte for byte.
verify_public() {
  local artifact path served paths
  for artifact in $ARTIFACTS; do
    paths=("$GROUP_PATH/$artifact/maven-metadata.xml")
    mapfile -t -O 1 paths < <(cd "$STAGING_DIR" \
      && find "$GROUP_PATH/$artifact/$VERSION" \( -name maven-metadata.xml -o -name '*.pom' \) | sort)
    if [[ "${paths[*]}" != *.pom* ]]; then
      echo "::error::No staged POM for $artifact $VERSION." >&2
      exit 1
    fi
    for path in "${paths[@]}"; do
      served=$(curl -fsSL --retry 5 --retry-delay 5 --retry-all-errors "$PUBLIC_REPOSITORY_URL/$path" \
        | sha256sum | cut -d' ' -f1)
      if [[ "$served" != "$(sha256sum < "$STAGING_DIR/$path" | cut -d' ' -f1)" ]]; then
        echo "::error::$PUBLIC_REPOSITORY_URL/$path does not serve the uploaded file." >&2
        exit 1
      fi
    done
  done
  echo "Public metadata and POMs match for every artifact."
}

# Resolves CONSUMER_DEPENDENCIES the way a consumer would: this build's artifactIds only from the public URL,
# everything else - including other projects in a shared group - from Maven Central. Every jar of this build that
# Gradle downloads must be byte-identical to the staged one.
consume() {
  local content=releasesOnly consumer="$RUNNER_TEMP/maven-consumer" group="${GROUP_PATH//\//.}"
  local modules="" dependencies="" artifact coordinate
  [[ "$VERSION" != *-SNAPSHOT ]] || content=snapshotsOnly
  for artifact in $ARTIFACTS; do
    modules+="            includeModule '$group', '$artifact'"$'\n'
  done
  if [[ -n "${CONSUMER_PLATFORM:-}" ]]; then
    dependencies+="    implementation platform(\"$CONSUMER_PLATFORM:\${testVersion}\")"$'\n'
  fi
  for coordinate in $CONSUMER_DEPENDENCIES; do
    dependencies+="    implementation \"$coordinate:\${testVersion}\""$'\n'
  done
  mkdir -p "$consumer"
  echo "rootProject.name = 'maven-consumer'" > "$consumer/settings.gradle"
  cat > "$consumer/build.gradle" <<EOF
plugins {
    id 'java-library'
}
repositories {
    exclusiveContent {
        forRepository {
            maven {
                url = uri(providers.gradleProperty('repositoryUrl').get())
                mavenContent {
                    $content()
                }
            }
        }
        filter {
$modules        }
    }
    mavenCentral()
}
dependencies {
$dependencies}
tasks.register('resolveArtifacts') {
    def artifacts = configurations.runtimeClasspath.incoming.artifacts
    doLast {
        artifacts.each { artifact ->
            def id = artifact.id.componentIdentifier
            if (id instanceof org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                println "resolved \${id.group} \${id.module} \${artifact.file}"
            }
        }
    }
}
EOF
  ./gradlew -p "$consumer" resolveArtifacts --refresh-dependencies -q \
    -PtestVersion="$VERSION" -PrepositoryUrl="$PUBLIC_REPOSITORY_URL" | tee "$RUNNER_TEMP/resolved.txt"
  # Only this build's jars are compared, by the module id Gradle resolved, never by file name: a dependency can
  # share this build's version string. Gradle caches a snapshot jar under its base version; the staged file carries the
  # timestamped value that the version-level metadata names. A release jar keeps its name.
  local matched=0 resolved_group resolved staged value
  while read -r resolved_group artifact resolved; do
    [[ " $ARTIFACTS " == *" $artifact "* && "$resolved_group" == "$group" ]] || continue
    staged="$STAGING_DIR/$GROUP_PATH/$artifact/$VERSION"
    value="$VERSION"
    if [[ "$VERSION" == *-SNAPSHOT ]]; then
      value=""
      if [[ -f "$staged/maven-metadata.xml" ]]; then
        value=$(sed -n '/<value>/{s:.*<value>\(.*\)</value>.*:\1:p;q}' "$staged/maven-metadata.xml")
      fi
    fi
    if [[ -z "$value" ]] || ! cmp -s "$resolved" "$staged/$artifact-$value.jar"; then
      echo "::error::Resolved $artifact does not match the staged artifact." >&2
      exit 1
    fi
    matched=$((matched + 1))
  done < <(sed -n 's/^resolved //p' "$RUNNER_TEMP/resolved.txt")
  local required
  for required in $CONSUMER_REQUIRED; do
    if ! grep -qF "resolved $group $required " "$RUNNER_TEMP/resolved.txt"; then
      echo "::error::The consumer did not resolve $required." >&2
      exit 1
    fi
  done
  echo "Resolved $matched jars of this build from $PUBLIC_REPOSITORY_URL, all identical to the staged ones."
}

command=${1:?usage: bloxbean-maven.sh discover|seed|upload|verify-public|consume}
shift
case "$command" in
  discover) discover "$@" ;;
  seed) seed ;;
  upload) upload ;;
  verify-public) verify_public ;;
  consume) consume ;;
  *) echo "unknown command: $command" >&2; exit 2 ;;
esac
