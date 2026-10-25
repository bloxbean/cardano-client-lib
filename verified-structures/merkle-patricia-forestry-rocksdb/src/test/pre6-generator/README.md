# pre6 store generator

Standalone Gradle project (not part of the CCL build) that writes real databases with the **published**
`cardano-client-*:0.8.0-pre6` artifacts from Maven Central. Its output is committed as
`../resources/mpf-compat/pre6-rocksdb.zip` (this module) and
`merkle-patricia-forestry-rdbms/src/test/resources/mpf-compat/pre6-h2.zip`, which
`Pre6RocksDbStoreCompatibilityTest` and `Pre6H2StoreCompatibilityTest` open with the current code.

```sh
# from the repository root
./gradlew -p verified-structures/merkle-patricia-forestry-rocksdb/src/test/pre6-generator run --args="/tmp/pre6-stores"
cd /tmp/pre6-stores
zip -r -X pre6-rocksdb.zip manifest.json rocksdb -x 'rocksdb/LOCK' 'rocksdb/LOG*'
zip -r -X pre6-h2.zip manifest.json h2
```
