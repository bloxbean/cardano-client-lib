cd ../..
./gradlew -p  ./backend-modules/cardano-graphql/ downloadApolloSchema --endpoint="https://graphql-api.mainnet.dandelion.link/" --schema "src/main/graphql/com/bloxbean/cardano/gql/schema.json"
