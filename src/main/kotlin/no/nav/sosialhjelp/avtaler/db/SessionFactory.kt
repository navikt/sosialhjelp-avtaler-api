package no.nav.sosialhjelp.avtaler.db

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

typealias SessionFactory = () -> Session

/**
 * SessionFactory implementation that always creates a new, non-transactional session from a DataSource instance.
 */
class DataSourceSessionFactory(private val dataSource: DataSource) : SessionFactory {
    override fun invoke(): Session = sessionOf(dataSource)
}

/**
 * SessionFactory implementation that always returns the same TransactionalSession instance.
 */
class TransactionalSessionFactory(private val session: TransactionalSession) : () -> TransactionalSession {
    override fun invoke(): TransactionalSession = session
}
