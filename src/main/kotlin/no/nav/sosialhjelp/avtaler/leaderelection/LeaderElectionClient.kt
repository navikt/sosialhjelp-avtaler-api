package no.nav.sosialhjelp.avtaler.leaderelection

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.CacheControl
import io.ktor.http.headers
import io.ktor.server.response.cacheControl
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import java.net.InetAddress

interface LeaderElectionClient {
    suspend fun isLeader(): Boolean
}

private val log = KotlinLogging.logger { }

class LeaderElectionClientImpl(
    private val httpClient: HttpClient,
) : LeaderElectionClient {
    override suspend fun isLeader(): Boolean {
        val electorUrl = Configuration.leaderElectionProperties.url
        val leaderJson =
            httpClient
                .get(electorUrl) {
                    headers {
                        cacheControl(CacheControl.MaxAge(10))
                    }
                }.body<JsonObject>()
        val leader: String? = leaderJson["name"]?.jsonPrimitive?.content
        if (leader == null) {
            log.warn { "Fikk null fra leader election" }
        }
        val hostname: String = InetAddress.getLocalHost().hostName

        return hostname == leader
    }
}

class LeaderElectionClientLocal : LeaderElectionClient {
    override suspend fun isLeader(): Boolean = true
}
