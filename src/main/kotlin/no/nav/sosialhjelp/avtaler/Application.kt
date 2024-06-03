package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import no.nav.security.token.support.client.core.ClientAuthenticationProperties
import no.nav.sosialhjelp.avtaler.HttpClientConfig.httpClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.avtalemaler.AvtalemalerService
import no.nav.sosialhjelp.avtaler.avtalemaler.avtalemalerApi
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.DefaultDatabaseContext
import no.nav.sosialhjelp.avtaler.digipost.DigipostClient
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.documentjob.DocumentJobService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket
import no.nav.sosialhjelp.avtaler.internal.internalRoutes
import no.nav.sosialhjelp.avtaler.kommune.kommuneApi
import no.nav.sosialhjelp.avtaler.pdl.PdlClient
import no.nav.sosialhjelp.avtaler.pdl.PersonNavnService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    val host = environment.config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"

    log.info("sosialhjelp-avtaler-api starting up on $host:$port...")

    configure()

    setupKoin()
    setupJobs()

    setupRoutes()
}

private fun Application.setupKoin() {
    install(Koin) {
        slf4jLogger()
        val avtaleModule =
            module {
                single<DatabaseContext>(createdAtStart = true) {
                    DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties).dataSource())
                }
                single { defaultHttpClient() }
                single {
                    ClientAuthenticationProperties.builder()
                        .clientId(Configuration.tokenXProperties.clientId)
                        .clientJwk(Configuration.tokenXProperties.privateJwk)
                        .clientAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                        .build()
                }
                single { Oauth2Client(get(), get(), Configuration.tokenXProperties) }
                single { AltinnService(AltinnClient(Configuration.altinnProperties, get())) }
                single {
                    DigipostClient(
                        Configuration.digipostProperties,
                        Configuration.virksomhetssertifikatProperties,
                        Configuration.profile,
                    )
                }
                single { GcpBucket(Configuration.gcpProperties.bucketName) }
                single { EregClient(Configuration.eregProperties) }
                single { PdlClient(Configuration.pdlProperties, get()) }
                singleOf(::DigipostService)
                singleOf(::DocumentJobService)
                singleOf(::PersonNavnService)
                singleOf(::AvtaleService)
                singleOf(::AvtalemalerService)
            }
        modules(avtaleModule)
    }
}

private fun Application.setupJobs() {
    val avtaleService by inject<AvtaleService>()
    val documentJobService by inject<DocumentJobService>()
    log.info("Setter opp oppryddingsjobb")
    val scope = CoroutineScope(Dispatchers.IO)
    val flow =
        flow {
            while (true) {
                delay(10.minutes)
                log.info("Sender oppryddingsignal")
                emit(Unit)
            }
        }

    flow.onEach {
        log.info("Tar imot oppryddingsignal")
        val avtalerUtenDokument = avtaleService.hentAvtalerUtenSignertDokument()
        log.info("Fant ${avtalerUtenDokument.size} avtaler uten nedlastet dokument")
        avtalerUtenDokument.forEach { digipostJobbData ->
            val resultat =
                documentJobService.lastNedOgLagreAvtale(
                    digipostJobbData,
                    avtaleService.hentAvtale(digipostJobbData.uuid) ?: return@forEach,
                )
            resultat.fold({
                log.info("Fikk lagret avtale med uuid ${digipostJobbData.uuid} i batch-jobb")
            }, {
                log.error("Fikk ikke lagret dokument i databasen", it)
            })
        }
    }.catch { log.error("Fikk feil i signalmottak", it) }.launchIn(scope)
}

fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(IgnoreTrailingSlash)
}

fun Application.setupRoutes() {
    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    routing {
        route("/sosialhjelp/avtaler-api") {
            internalRoutes()
            route("/api") {
                authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                    avtaleApi()
                    kommuneApi()
                }
                authenticate(if (Configuration.local) "local" else AZURE_AUTH) {
                    avtalemalerApi()
                }
            }
        }
    }
}
