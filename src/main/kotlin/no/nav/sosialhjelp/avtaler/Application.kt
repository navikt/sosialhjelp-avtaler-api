package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
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
import no.nav.sosialhjelp.avtaler.altinn.AltinnClientImpl
import no.nav.sosialhjelp.avtaler.altinn.AltinnClientLocal
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.auth.Oauth2ClientImpl
import no.nav.sosialhjelp.avtaler.auth.Oauth2ClientLocal
import no.nav.sosialhjelp.avtaler.avtalemaler.AvtalemalerService
import no.nav.sosialhjelp.avtaler.avtalemaler.InjectionService
import no.nav.sosialhjelp.avtaler.avtalemaler.avtalemalerApi
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.DefaultDatabaseContext
import no.nav.sosialhjelp.avtaler.digipost.DigipostClient
import no.nav.sosialhjelp.avtaler.digipost.DigipostClientImpl
import no.nav.sosialhjelp.avtaler.digipost.DigipostClientLocal
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.documentjob.DocumentJobService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.ereg.EregClientImpl
import no.nav.sosialhjelp.avtaler.ereg.EregClientLocal
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket
import no.nav.sosialhjelp.avtaler.gotenberg.GotenbergClient
import no.nav.sosialhjelp.avtaler.internal.internalRoutes
import no.nav.sosialhjelp.avtaler.kommune.KommuneService
import no.nav.sosialhjelp.avtaler.kommune.kommuneApi
import no.nav.sosialhjelp.avtaler.leaderelection.LeaderElectionClient
import no.nav.sosialhjelp.avtaler.leaderelection.LeaderElectionClientImpl
import no.nav.sosialhjelp.avtaler.leaderelection.LeaderElectionClientLocal
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
    io.ktor.server.cio.EngineMain
        .main(args)
}

fun Application.module() {
    val host = environment.config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"

    log.info("sosialhjelp-avtaler-api starting up on $host:$port...")

    setupKoin()
    configure()
    setupJobs()
    setupRoutes()
}

private fun Application.setupKoin() {
    install(Koin) {
        slf4jLogger()
        val externalServices =
            if (Configuration.local) {
                module {
                    single<DigipostClient> { DigipostClientLocal() }
                    single<Oauth2Client> { Oauth2ClientLocal() }
                    single<AltinnClient> { AltinnClientLocal() }
                    single<EregClient> { EregClientLocal(get()) }
                    single<LeaderElectionClient> { LeaderElectionClientLocal() }
                }
            } else {
                module {
                    single<DigipostClient> {
                        DigipostClientImpl(
                            Configuration.digipostProperties,
                            Configuration.virksomhetssertifikatProperties,
                            Configuration.profile,
                        )
                    }
                    single<Oauth2Client> { Oauth2ClientImpl(get(), get(), Configuration.tokenXProperties) }
                    single<AltinnClient> { AltinnClientImpl(Configuration.altinnProperties, get()) }
                    single<EregClient> { EregClientImpl(get(), Configuration.eregProperties) }
                    single<LeaderElectionClient> { LeaderElectionClientImpl(get()) }
                }
            }
        val avtaleModule =
            module {
                single<DatabaseContext>(createdAtStart = true) {
                    DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties).dataSource())
                }
                single { defaultHttpClient() }
                single {
                    ClientAuthenticationProperties
                        .builder()
                        .clientId(Configuration.tokenXProperties.clientId)
                        .clientJwk(Configuration.tokenXProperties.privateJwk)
                        .clientAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
                        .build()
                }

                single { GcpBucket(Configuration.gcpProperties.bucketName) }
                single { PdlClient(Configuration.pdlProperties, get()) }
                single { GotenbergClient(get(), Configuration.gotenbergProperties.url) }
                single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }

                singleOf(::AltinnService)
                singleOf(::KommuneService)
                singleOf(::InjectionService)
                singleOf(::DigipostService)
                singleOf(::DocumentJobService)
                singleOf(::PersonNavnService)
                singleOf(::AvtaleService)
                singleOf(::AvtalemalerService)
            }
        modules(externalServices, avtaleModule)
    }
}

private fun Application.setupJobs() {
    setupOppryddingsjobb()
    setupPubliseringRetry()
}

@WithSpan
private suspend fun receivePublishSignal(avtalemalerService: AvtalemalerService) {
    val feilede = avtalemalerService.hentFeiledePubliseringer()
    if (feilede.isNotEmpty()) {
        avtalemalerService.initiatePublisering(feilede)
    }
}

private fun Application.setupPubliseringRetry() {
    val avtalemalerService by inject<AvtalemalerService>()
    val leaderElectionClient by inject<LeaderElectionClient>()
    val scope = CoroutineScope(Dispatchers.IO)
    val flow =
        flow {
            while (true) {
                delay(10.minutes)
                if (leaderElectionClient.isLeader()) {
                    log.info("Sender retry-signal for publisering")
                    emit(Unit)
                }
                delay(10.minutes)
            }
        }

    flow
        .onEach {
            receivePublishSignal(avtalemalerService)
        }.catch { log.error("Fikk feil i signalmottak", it) }
        .launchIn(scope)
}

@WithSpan
private suspend fun receiveCleaningSignal(
    avtaleService: AvtaleService,
    documentJobService: DocumentJobService,
) {
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
}

private fun Application.setupOppryddingsjobb() {
    if (Configuration.profile == Configuration.Profile.LOCAL) {
        return
    }
    val avtaleService by inject<AvtaleService>()
    val documentJobService by inject<DocumentJobService>()
    val leaderElectionClient by inject<LeaderElectionClient>()
    log.info("Setter opp oppryddingsjobb")
    val scope = CoroutineScope(Dispatchers.IO)
    val flow =
        flow {
            while (true) {
                delay(10.minutes)
                if (leaderElectionClient.isLeader()) {
                    log.info("Sender oppryddingsignal")
                    emit(Unit)
                }
            }
        }

    flow
        .onEach {
            receiveCleaningSignal(avtaleService, documentJobService)
        }.catch { log.error("Fikk feil i signalmottak", it) }
        .launchIn(scope)
}

private fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    val appMicrometerRegistry by inject<PrometheusMeterRegistry>()
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            registerKotlinModule()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(IgnoreTrailingSlash)
    install(CallLogging) {
        filter {
            !it.request.path().contains("/internal")
        }
    }
}

fun Application.setupRoutes() {
    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    val prometheusMeterRegistry by inject<PrometheusMeterRegistry>()
    routing {
        route("/sosialhjelp/avtaler-api") {
            internalRoutes(prometheusMeterRegistry)
            route("/api") {
                kommuneApi()
                authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                    avtaleApi()
                }
                authenticate(if (Configuration.local) "local" else AZURE_AUTH) {
                    avtalemalerApi()
                }
            }
        }
    }
}
