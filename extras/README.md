This module is used to create cardano-client-lib-extras jar.

To download native binaries from GitHub release page and create cardano-client-lib-extra jar

```
//Go to top level project folder
$> cd cardano-client-lib
$> sh extras/scripts/download_extra_libs.sh <github_release_tag>
$> ./gradlew clean build
```
