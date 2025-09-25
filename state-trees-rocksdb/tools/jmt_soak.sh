#!/usr/bin/env bash
set -euo pipefail

# Simple helper to run repeated soak segments with fresh JVMs.
# Usage: ./jmt_soak.sh /var/jmt 6 3600 "--batch=1000 --value-size=128 --mix=60:30:10:0 --prune-interval=10000 --prune-to=window:100000 --stats-period=10"

if [ $# -lt 4 ]; then
  echo "Usage: $0 <rocksdb_dir> <segments> <segment_seconds> <extra_args>" >&2
  exit 1
fi

DB_DIR="$1"
SEGMENTS="$2"
SEG_SECS="$3"
EXTRA_ARGS="$4"

for i in $(seq 1 "$SEGMENTS"); do
  TS=$(date +%Y%m%d-%H%M%S)
  STATS="/tmp/jmt-stats-${TS}.csv"
  echo "[Segment $i/$SEGMENTS] Running for ${SEG_SECS}s; stats: ${STATS}"
  ./gradlew -q :state-trees-rocksdb:com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester.main \
    --args="--records=0 --duration=${SEG_SECS} --rocksdb=${DB_DIR} --stats-csv=${STATS} ${EXTRA_ARGS}"
  echo "[Segment $i] Done"
  sleep 5
done

echo "All segments complete. Stats CSVs in /tmp/jmt-stats-*.csv"

