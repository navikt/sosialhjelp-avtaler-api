package no.nav.sosialhjelp.avtaler.graphql

data class GraphQLRequest<T>(
    val query: String,
    val variables: T,
)
