#!/usr/bin/env python3
"""Removes old commit snapshots from the BloxBean Maven repository (bloxbean-snapshot-cleanup.yml).

Usage: clean-maven-snapshots.py plan
       clean-maven-snapshots.py delete <version>...

Environment: BUCKET, REPOSITORY_PREFIX (maven/snapshots), GROUP_PATH (org/yanoproject), ARTIFACTS (this build's
artifactIds, from `bloxbean-maven.sh discover`), KEEP_VERSIONS, KEEP_DAYS, and the AWS CLI's endpoint and
credentials. Standard: bloxbean/release-ops docs/12-bloxbean-maven-repository.md and templates/scripts/; keep it
identical across repositories.

Every publication uploads all artifacts under one commit version, so versions are chosen once and dropped from
every artifact. A version is kept if it is among the KEEP_VERSIONS most recently published, was published within
KEEP_DAYS, or is any artifact's <latest>. `plan` reports the rest. `delete` takes the versions an approver saw in
the plan, drops only those still eligible, first rewrites each artifact's maven-metadata.xml without them, and
then deletes their directories, so the metadata never lists a deleted version.

Only this build's artifactIds under GROUP_PATH are touched. A group can be shared by several repositories
(com/bloxbean/cardano), so the artifact set comes from the build, never from the bucket; other projects, other
groups and maven/releases are never written.
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

BUCKET = os.environ['BUCKET']
ROOT = f"{os.environ['REPOSITORY_PREFIX']}/{os.environ['GROUP_PATH']}/"
KEEP_VERSIONS = int(os.environ['KEEP_VERSIONS'])
KEEP_DAYS = int(os.environ['KEEP_DAYS'])
ARTIFACTS = set(os.environ['ARTIFACTS'].split())
METADATA = 'maven-metadata.xml'


def aws(*args):
    result = subprocess.run(['aws', *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f'::error::aws {args[0]} {args[1]} failed: {result.stderr.strip()}')
    return result.stdout


def scan():
    """Returns {artifact: {version: [(key, size, modified)]}} for the artifacts this group owns."""
    listing = json.loads(aws('s3api', 'list-objects-v2', '--bucket', BUCKET, '--prefix', ROOT, '--output', 'json',
                             '--query', 'Contents[].[Key, Size, LastModified]') or 'null') or []
    owned = {key[len(ROOT):].split('/')[0] for key, _, _ in listing if key[len(ROOT):].count('/') == 1
             and key.endswith('/' + METADATA)} & ARTIFACTS
    artifacts = defaultdict(lambda: defaultdict(list))
    for key, size, modified in listing:
        parts = key[len(ROOT):].split('/')
        if parts[0] in owned and len(parts) == 3:
            artifacts[parts[0]][parts[1]].append(
                (key, size, datetime.fromisoformat(modified.replace('Z', '+00:00'))))
    return {artifact: dict(versions) for artifact, versions in artifacts.items()} | {
        artifact: {} for artifact in owned - artifacts.keys()}


def read_metadata(artifact):
    with tempfile.TemporaryDirectory() as tmp:
        target = Path(tmp) / METADATA
        aws('s3', 'cp', '--only-show-errors', f's3://{BUCKET}/{ROOT}{artifact}/{METADATA}', str(target))
        return ET.parse(target)


def plan(artifacts, metadata):
    published = defaultdict(lambda: datetime.min.replace(tzinfo=timezone.utc))
    for versions in artifacts.values():
        for version, objects in versions.items():
            published[version] = max(published[version], *(modified for _, _, modified in objects))
    latest = {tree.findtext('versioning/latest') for tree in metadata.values()}
    newest = sorted(published, key=published.get, reverse=True)[:KEEP_VERSIONS]
    cutoff = datetime.now(timezone.utc) - timedelta(days=KEEP_DAYS)
    drop = sorted((v for v in published if v.endswith('-SNAPSHOT') and v not in newest and v not in latest
                   and published[v] < cutoff), key=published.get)
    return published, drop


def rewrite_metadata(tree, drop, out_dir):
    versions = tree.find('versioning/versions')
    for element in [e for e in versions if e.text in drop]:
        versions.remove(element)
    tree.find('versioning/lastUpdated').text = datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')
    ET.indent(tree, '  ')
    target = out_dir / METADATA
    out_dir.mkdir(parents=True)
    tree.write(target, encoding='UTF-8', xml_declaration=True)
    data = target.read_bytes()
    for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
        (out_dir / f'{METADATA}.{algorithm}').write_text(hashlib.new(algorithm, data).hexdigest())


def delete(artifacts, metadata, drop):
    with tempfile.TemporaryDirectory() as tmp:
        staged = Path(tmp) / 'metadata'
        for artifact, tree in metadata.items():
            if any(v.text in drop for v in tree.findall('versioning/versions/version')):
                rewrite_metadata(tree, drop, staged / artifact)
        if staged.is_dir():
            aws('s3', 'cp', '--recursive', '--only-show-errors', '--cache-control', 'no-cache', str(staged),
                f's3://{BUCKET}/{ROOT}')
    # `s3 rm` deletes object by object. The batch DeleteObjects call is avoided on purpose: AWS CLI 2.23+ always
    # attaches a CRC32 checksum to it, which R2 has rejected. Each filter is one exact <artifact>/<version>/ prefix.
    targets = [(artifact, v) for artifact, versions in artifacts.items() for v in drop if v in versions]
    filters = [arg for artifact, v in targets for arg in ('--include', f'{artifact}/{v}/*')]
    if filters:
        aws('s3', 'rm', f's3://{BUCKET}/{ROOT}', '--recursive', '--only-show-errors', '--exclude', '*', *filters)
    return sum(len(artifacts[artifact][v]) for artifact, v in targets)


def report(published, drop, artifacts, heading):
    sizes = defaultdict(int)
    for versions in artifacts.values():
        for version in drop:
            sizes[version] += sum(size for _, size, _ in versions.get(version, []))
    lines = [f'## {heading}', '',
             f'{len(published)} versions published; keeping the newest {KEEP_VERSIONS}, anything from the last '
             f'{KEEP_DAYS} days and every `<latest>`.', '']
    if drop:
        lines += ['| Version | Last published | Size |', '|---|---|---|']
        lines += [f'| `{v}` | {published[v]:%Y-%m-%d} | {sizes[v] / 1e6:.1f} MB |' for v in drop]
        lines += ['', f'{len(drop)} versions, {sum(sizes.values()) / 1e6:.1f} MB.']
    else:
        lines += ['Nothing to remove.']
    text = '\n'.join(lines) + '\n'
    print(text)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write(text)


def main(mode, requested):
    artifacts = scan()
    metadata = {artifact: read_metadata(artifact) for artifact in artifacts}
    published, drop = plan(artifacts, metadata)
    if mode == 'plan':
        report(published, drop, artifacts, 'Snapshot cleanup plan')
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
                output.write(f"versions={' '.join(drop)}\n")
        return 0
    # Only what the approver saw, and only if it is still eligible now.
    approved = [v for v in drop if v in requested]
    for skipped in sorted(set(requested) - set(approved)):
        print(f'::warning::{skipped} is no longer eligible and is kept.')
    count = delete(artifacts, metadata, set(approved)) if approved else 0
    report(published, approved, artifacts, f'Removed {len(approved)} snapshot versions ({count} objects)')
    return 0


if __name__ == '__main__':
    if len(sys.argv) < 2 or sys.argv[1] not in ('plan', 'delete'):
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2:]))
