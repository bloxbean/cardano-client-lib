#!/bin/bash
# Resolves the jars of one side (a CCL build plus a Yaci build) into sides/<label>/.
# usage: side.sh <label> <cclVersion> <yaciVersion>
set -euo pipefail
[ $# -eq 3 ] || { echo "usage: $0 <label> <cclVersion> <yaciVersion>" >&2; exit 2; }
LC=/Volumes/data2/yano-try/cbor-adr/lc
cd "$(dirname "$0")"
./gradlew --no-watch-fs --console=plain -q copySide -Dmaven.repo.local="$LC/m2" \
    -PsideCcl="$2" -PsideYaci="$3" -PsideDir="$LC/sides/$1"
ls "$LC/sides/$1" | grep -E '^(cardano-client-|yaci-core|jackson-databind)'
echo "$(ls "$LC/sides/$1" | wc -l | tr -d ' ') jars in $LC/sides/$1"
