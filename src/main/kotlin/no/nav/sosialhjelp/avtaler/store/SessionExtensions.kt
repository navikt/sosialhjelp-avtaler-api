package no.nav.sosialhjelp.avtaler.store

import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.action.QueryAction
import kotliquery.action.ResultQueryActionBuilder
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

fun <T> Session.query(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    mapper: ResultMapper<T>,
): T? = run(queryOf(sql, queryParameters).map(mapper).asSingle)

fun <T> Session.queryList(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    mapper: ResultMapper<T>,
): List<T> = run(queryOf(sql, queryParameters).map(mapper).asList)

fun <T> Session.queryList(
    @Language("PostgreSQL") sql: String,
    queryParameters: List<String> = emptyList(),
    mapper: ResultMapper<T>,
): List<T> = run(queryOf(sql, params = queryParameters.toTypedArray()).map(mapper).asList)

fun <T> Session.queryPagedList(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    limit: Int,
    offset: Int,
    mapper: ResultMapper<T>,
): Page<T> = run(queryOf(sql, queryParameters).map(mapper).asPage(limit, offset))

fun Session.update(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
): UpdateResult = UpdateResult(rowCount = run(queryOf(sql, queryParameters).asUpdate))

const val COLUMN_LABEL_TOTAL = "total"

typealias QueryParameters = Map<String, Any?>
typealias ResultMapper<T> = (Row) -> T?

data class UpdateResult(
    val rowCount: Int? = null,
    val generatedId: Long? = null,
) {
    fun validate() {
        if (rowCount == 0) {
            throw StoreException("rowsUpdated var 0")
        }
    }
}

data class Page<T>(
    val items: List<T>,
    val total: Int,
) : List<T> by items

data class PageResultQueryAction<A>(
    val query: Query,
    val extractor: (Row) -> A?,
    val limit: Int,
    val offset: Int,
) : QueryAction<Page<A>> {
    override fun runWithSession(session: Session): Page<A> {
        var totalNumberOfItems = -1
        val items = session.list(
            query.let {
                Query(
                    "${it.statement} limit :limit offset :offset",
                    it.params,
                    it.paramMap.plus(
                        mapOf(
                            "limit" to limit + 1, // fetch one more than limit to check for "hasMore"
                            "offset" to offset,
                        )
                    )
                )
            }
        ) {
            totalNumberOfItems = it.intOrNull(COLUMN_LABEL_TOTAL) ?: -1
            extractor(it)
        }
        return Page(
            items = items.take(limit),
            total = totalNumberOfItems,
        )
    }
}

fun <A> ResultQueryActionBuilder<A>.asPage(limit: Int, offset: Int): PageResultQueryAction<A> =
    PageResultQueryAction(query, extractor, limit, offset)

fun <A> Session.run(action: PageResultQueryAction<A>): Page<A> = action.runWithSession(this)
