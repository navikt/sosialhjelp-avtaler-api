package no.nav.sosialhjelp.avtaler.db

import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.DatabaseConfiguration
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleStore
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleStorePostgres
import javax.sql.DataSource

interface DatabaseContext {
    val dataSource: DataSource

    fun createSessionContext(sessionFactory: SessionFactory): DatabaseSessionContext
}

class DefaultDatabaseContext(override val dataSource: DataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()) :
    DatabaseContext {
    override fun createSessionContext(sessionFactory: SessionFactory): DatabaseSessionContext =
        DefaultDatabaseSessionContext(sessionFactory)
}

interface DatabaseSessionContext {
    val avtaleStore: AvtaleStore
}

class DefaultDatabaseSessionContext(sessionFactory: SessionFactory) : DatabaseSessionContext {
    override val avtaleStore = AvtaleStorePostgres(sessionFactory)
}