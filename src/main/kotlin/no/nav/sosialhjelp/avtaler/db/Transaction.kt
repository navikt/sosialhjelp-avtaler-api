package no.nav.sosialhjelp.avtaler.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotliquery.sessionOf
import kotliquery.using

suspend fun <R> transaction(
    context: DatabaseContext,
    block: (DatabaseSessionContext) -> R,
) =
    withContext(Dispatchers.IO) {
        using(sessionOf(context.dataSource)) { session ->
            session.transaction { tx ->
                block(context.createSessionContext(TransactionalSessionFactory(tx)))
            }
        }
    }
