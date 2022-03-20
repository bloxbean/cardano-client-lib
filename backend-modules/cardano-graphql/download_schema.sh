cd ../..
./gradlew -p  backends/cardano-graphql/ downloadApolloSchema --endpoint="https://graphql-api.mainnet.dandelion.link/" --schema "src/main/graphql/com/bloxbean/cardano/gql/schema.json"
