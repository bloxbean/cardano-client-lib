#!/usr/bin/env python3
"""Checks a staged Maven repository before it is uploaded to the BloxBean Maven repository. Standard:
bloxbean/release-ops docs/12-bloxbean-maven-repository.md and templates/scripts/; keep it identical across
repositories.

Usage: verify-maven-staging.py [--central-bundle <zip>] <repository-dir> <seed-dir> <group-path> <version>
           <artifact path>...

An artifact path is relative to <group-path>: usually the artifactId, nested for a Gradle plugin marker (e.g.
julc/org.julclang.julc.gradle.plugin, group org.julclang.julc). The repository must hold exactly the given artifacts, each with <version> as its only
version, artifact-level maven-metadata.xml that names that version, a checksum for every file, and binary, sources
and javadoc jars plus Gradle module metadata for every jar-packaged module. A snapshot also needs version-level
metadata naming each of its timestamped files; a release needs a signature (.asc) for each of its files. A file of
any other kind or outside <group-path> - a distribution zip, a native binary, another project's metadata - fails
the check, because whatever is staged is uploaded to the shared bucket.

<seed-dir> holds <artifact path>.xml, the artifact-level metadata the repository was seeded with. Every version it
lists must still be listed after staging: uploading a list Gradle failed to merge would erase published versions.
A release version the seed already lists is refused, because releases are never replaced.

--central-bundle is the Maven Central deployment zip of the same build. Every file in it must be byte-identical to
the staged one, and every staged artifact and signature must be in it, so Central and the BloxBean repository
receive the same release.
"""
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

CHECKSUMS = ('.md5', '.sha1', '.sha256', '.sha512')
MAVEN_FILES = ('.jar', '.pom', '.module', '.asc', 'maven-metadata.xml') + CHECKSUMS
JAR_MODULE_FILES = {(None, 'jar'), ('sources', 'jar'), ('javadoc', 'jar'), (None, 'module'), (None, 'pom')}


def text(element, path):
    found = element.find(path)
    return found.text.strip() if found is not None and found.text else None


def versions(metadata):
    return [v.text for v in metadata.findall('versioning/versions/version')]


def packaging(pom):
    # POMs are namespaced; match the element by local name. Maven's default packaging is jar.
    for child in ET.parse(pom).getroot():
        if child.tag.rsplit('}', 1)[-1] == 'packaging':
            return child.text.strip()
    return 'jar'


def base_name(name):
    """The artifact file a checksum or signature belongs to: x.jar.asc.sha1 -> x.jar."""
    for checksum in CHECKSUMS:
        name = name.removesuffix(checksum)
    return name.removesuffix('.asc')


def snapshot_files(version_dir, group_id, artifact, version, errors):
    """Files the version-level metadata names, as {(classifier, extension): file name}."""
    if not (version_dir / 'maven-metadata.xml').is_file():
        errors.append(f'{artifact}: no version-level maven-metadata.xml')
        return {}
    metadata = ET.parse(version_dir / 'maven-metadata.xml').getroot()
    if (text(metadata, 'groupId'), text(metadata, 'artifactId'), text(metadata, 'version')) != (
            group_id, artifact, version):
        errors.append(f'{artifact}: version metadata names another artifact or version')
    named = {}
    for entry in metadata.findall('versioning/snapshotVersions/snapshotVersion'):
        classifier, extension = text(entry, 'classifier'), text(entry, 'extension')
        suffix = f'-{classifier}' if classifier else ''
        named[(classifier, extension)] = f'{artifact}-{text(entry, "value")}{suffix}.{extension}'
    for name in named.values():
        if not (version_dir / name).is_file():
            errors.append(f'{artifact}: metadata names {name}, which is not staged')
    return named


def release_files(version_dir, artifact, version, errors):
    """Release files present, as {(classifier, extension): file name}; each must be signed."""
    if (version_dir / 'maven-metadata.xml').exists():
        errors.append(f'{artifact}: a release has no version-level maven-metadata.xml')
    # Any classifier: modules also publish e.g. test-fixtures jars. --central-bundle ties them to Central's set.
    pattern = re.compile(re.escape(f'{artifact}-{version}') + r'(?:-([A-Za-z][A-Za-z0-9-]*))?\.(jar|pom|module)')
    named = {}
    for f in version_dir.iterdir():
        match = pattern.fullmatch(f.name)
        if match:
            named[(match.group(1), match.group(2))] = f.name
            if not f.with_name(f.name + '.asc').is_file():
                errors.append(f'{artifact}: {f.name} is not signed')
    return named


def check_artifact(artifact_dir, seed, group_id, artifact, version):
    errors = []
    # Version directories hold a POM; any other directory belongs to a nested artifact path, checked on its own.
    staged_versions = sorted(p.name for p in artifact_dir.iterdir() if p.is_dir() and any(p.glob('*.pom')))
    if staged_versions != [version]:
        return [f'{artifact}: staged versions {staged_versions}, expected [{version}]']
    if not (artifact_dir / 'maven-metadata.xml').is_file():
        return [f'{artifact}: no artifact-level maven-metadata.xml']
    release = not version.endswith('-SNAPSHOT')

    metadata = ET.parse(artifact_dir / 'maven-metadata.xml').getroot()
    listed = versions(metadata)
    if (text(metadata, 'groupId'), text(metadata, 'artifactId')) != (group_id, artifact):
        errors.append(f'{artifact}: artifact metadata names another artifact')
    if version not in listed or text(metadata, 'versioning/latest') != version:
        errors.append(f'{artifact}: artifact metadata does not list {version} as latest')
    if release and text(metadata, 'versioning/release') != version:
        errors.append(f'{artifact}: artifact metadata does not name {version} as the release')
    if seed.is_file():
        published = versions(ET.parse(seed).getroot())
        dropped = [v for v in published if v not in listed]
        if dropped:
            errors.append(f'{artifact}: staging dropped published versions {dropped}')
        if release and version in published:
            errors.append(f'{artifact}: {version} is already published; a release is never replaced')

    version_dir = artifact_dir / version
    named = (release_files(version_dir, artifact, version, errors) if release
             else snapshot_files(version_dir, group_id, artifact, version, errors))
    if (None, 'pom') not in named:
        return errors + [f'{artifact}: no POM']
    required = {(None, 'pom')}
    if packaging(version_dir / named[(None, 'pom')]) != 'pom':
        required = JAR_MODULE_FILES
    for classifier, extension in sorted(required - named.keys(), key=str):
        errors.append(f'{artifact}: missing {classifier or "main"} {extension}')

    # Anything else in the version directory would be uploaded but never resolved.
    expected = set(named.values()) | (set() if release else {'maven-metadata.xml'})
    for f in version_dir.iterdir():
        if base_name(f.name) not in expected:
            errors.append(f'{artifact}: unexpected file {f.name}')
    return errors


def check_bundle(bundle, root):
    errors = []
    with zipfile.ZipFile(bundle) as z:
        entries = {n for n in z.namelist() if not n.endswith('/')}
        for name in sorted(entries):
            staged = root / name
            if not staged.is_file():
                errors.append(f'Central bundle has {name}, which is not staged')
            elif z.read(name) != staged.read_bytes():
                errors.append(f'Central bundle and staging differ: {name}')
    for f in sorted(root.rglob('*')):
        if f.is_file() and f.name.endswith(('.jar', '.pom', '.module', '.asc')):
            if f.relative_to(root).as_posix() not in entries:
                errors.append(f'staged {f.relative_to(root)} is not in the Central bundle')
    return errors, len(entries)


def main(bundle, root, seed_dir, group_path, version, artifacts):
    root = Path(root)
    group_dir = root / group_path
    errors = []
    for f in sorted(p for p in root.rglob('*') if p.is_file()):
        rel = f.relative_to(root)
        if not f.is_relative_to(group_dir):
            errors.append(f'outside {group_path}: {rel}')
        elif not f.name.endswith(MAVEN_FILES):
            errors.append(f'not a Maven repository file: {rel}')
        elif not f.name.endswith(CHECKSUMS) and not f.with_name(f.name + '.sha1').is_file():
            errors.append(f'no checksum: {rel}')

    staged = sorted({pom.parent.parent.relative_to(group_dir).as_posix() for pom in group_dir.rglob('*.pom')}
                    ) if group_dir.is_dir() else []
    if staged != sorted(artifacts):
        errors.append(f'staged artifacts {staged} do not match the build publications {sorted(artifacts)}')
    for artifact in staged:
        path = Path(group_path) / artifact
        errors += check_artifact(group_dir / artifact, Path(seed_dir) / f'{artifact}.xml',
                                 path.parent.as_posix().replace('/', '.'), path.name, version)
    bundled = None
    if bundle:
        bundle_errors, bundled = check_bundle(bundle, root)
        errors += bundle_errors

    for error in errors:
        print(f'::error::{error}', file=sys.stderr)
    if errors:
        return 1
    files = sum(1 for p in root.rglob('*') if p.is_file())
    print(f'{len(staged)} artifacts, {files} files, all at {version}'
          + (f'; all {bundled} Central bundle files identical' if bundle else ''))
    return 0


if __name__ == '__main__':
    args = sys.argv[1:]
    bundle = None
    if args[:1] == ['--central-bundle']:
        bundle, args = args[1], args[2:]
    if len(args) < 5:
        sys.exit(__doc__)
    sys.exit(main(bundle, args[0], args[1], args[2], args[3], args[4:]))
