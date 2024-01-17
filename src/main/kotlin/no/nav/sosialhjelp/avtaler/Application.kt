package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import no.nav.security.token.support.client.core.ClientAuthenticationProperties
import no.nav.sosialhjelp.avtaler.HttpClientConfig.httpClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
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
import java.net.InetAddress
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
    val databaseContext = DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties, Configuration.profile).dataSource())

    configure()

    val authProperties =
        ClientAuthenticationProperties.builder()
            .clientId(Configuration.tokenXProperties.clientId)
            .clientJwk(Configuration.tokenXProperties.privateJwk)
            .clientAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .build()

    val defaultHttpClient = defaultHttpClient()

    val tokenExchangeClient = Oauth2Client(defaultHttpClient, authProperties, Configuration.tokenXProperties)
    val altinnService = AltinnService(AltinnClient(Configuration.altinnProperties, tokenExchangeClient))
    val digipostService =
        DigipostService(
            DigipostClient(Configuration.digipostProperties, Configuration.virksomhetssertifikatProperties, Configuration.profile),
            databaseContext,
        )
    val gcpBucket = GcpBucket(Configuration.gcpProperties.bucketName)
    val eregClient = EregClient(Configuration.eregProperties)
    val documentJobService = DocumentJobService(digipostService, gcpBucket, eregClient)
    val avtaleService = AvtaleService(altinnService, digipostService, gcpBucket, documentJobService, databaseContext, eregClient)
    val personNavnService = PersonNavnService(PdlClient(Configuration.pdlProperties, tokenExchangeClient))
    launch {
        val isLeader = isLeader(defaultHttpClient)
        if (isLeader) {
            setupJobs(avtaleService, documentJobService)
        }
    }
    setupRoutes(avtaleService, personNavnService)
}

suspend fun isLeader(defaultHttpClient: HttpClient): Boolean =
    defaultHttpClient.get(System.getenv("ELECTOR_PATH"))
        .body<JsonObject>()["name"]
        ?.jsonPrimitive
        ?.content == InetAddress.getLocalHost().hostName

private fun Application.setupJobs(
    avtaleService: AvtaleService,
    documentJobService: DocumentJobService,
) {
    log.info("Setter opp oppryddingsjobb")
    val scope = CoroutineScope(Dispatchers.IO)
    val flow =
        flow {
            while (true) {
                log.info("Sender oppryddingsignal")
                delay(10.minutes)
                emit(Unit)
            }
        }

    flow.onEach {
        log.info("Tar imot oppryddingsignal")
        val avtalerUtenDokument = avtaleService.hentAvtalerUtenSignertDokument()
        avtalerUtenDokument.forEach { digipostJobbData ->
            val resultat =
                documentJobService.lastNedOgLagreAvtale(
                    digipostJobbData,
                    avtaleService.hentAvtale(digipostJobbData.orgnr) ?: return@forEach,
                )
            resultat.fold({
                log.info("Fikk lagret avtale for orgnr: ${digipostJobbData.orgnr} i batch-jobb")
            }, {
                log.error("Fikk ikke lagret dokument i databasen", it)
            })
        }
    }.launchIn(scope)
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

fun Application.setupRoutes(
    avtaleService: AvtaleService,
    personNavnService: PersonNavnService,
) {
    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    routing {
        route("/sosialhjelp/avtaler-api") {
            internalRoutes()
            route("/api") {
                authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                    avtaleApi(avtaleService, personNavnService)
                    kommuneApi(avtaleService)
                }
            }
        }
    }
}
