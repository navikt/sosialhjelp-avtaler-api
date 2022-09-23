package no.nav.sosialhjelp.avtaler.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.internalRoutes() {
    route("/sosialhjelp/avtaler-api/internal") {
        get("/is-alive") {
            call.respondText("Application is alive!", status = HttpStatusCode.OK)
        }
        get("/is-ready") {
            call.respondText("Application is ready!", status = HttpStatusCode.OK)
        }
    }
}
