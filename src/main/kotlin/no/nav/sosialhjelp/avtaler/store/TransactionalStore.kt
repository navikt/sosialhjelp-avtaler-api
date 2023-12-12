package no.nav.sosialhjelp.avtaler.store

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.using
import no.nav.sosialhjelp.avtaler.db.SessionFactory
import no.nav.sosialhjelp.avtaler.db.TransactionalSessionFactory

abstract class TransactionalStore(private val sessionFactory: SessionFactory) : Store {
    protected fun <T> session(block: (Session) -> T): T =
        when (sessionFactory) {
            // closing should be handled in top-level transaction function in this case
            is TransactionalSessionFactory -> block(sessionFactory())
            else -> using(sessionFactory(), block)
        }

    protected fun <T> transaction(block: (TransactionalSession) -> T): T =
        when (sessionFactory) {
            // closing should be handled in top-level transaction function in this case
            is TransactionalSessionFactory -> block(sessionFactory() as TransactionalSession)
            else ->
                using(sessionFactory()) { session ->
                    session.transaction(block)
                }
        }
}
