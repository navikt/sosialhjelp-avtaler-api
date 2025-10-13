package no.nav.sosialhjelp.avtaler.db

import kotliquery.Session
import kotliquery.TransactionalSession

typealias SessionFactory = () -> Session

/**
 * SessionFactory implementation that always returns the same TransactionalSession instance.
 */
class TransactionalSessionFactory(private val session: TransactionalSession) : () -> TransactionalSession {
    override fun invoke(): TransactionalSession = session
}
