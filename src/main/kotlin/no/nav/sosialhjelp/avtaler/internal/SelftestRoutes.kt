package no.nav.sosialhjelp.avtaler.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.internalRoutes() {
    route("/internal") {
        get("/isAlive") {
            call.respondText("Application is alive!", status = HttpStatusCode.OK)
        }
        get("/isReady") {
            call.respondText("Application is ready!", status = HttpStatusCode.OK)
        }
    }
}
