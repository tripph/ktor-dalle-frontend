package com.example.plugins

import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*

fun Application.configureRouting() {


    routing {
        get("/") {
            call.respondHtml(HttpStatusCode.OK) {
                body {
                    h1 {
                        +"test"
                    }
                }
            }
        }
        // Static plugin. Try to access `/static/index.html`
        static("/static") {
            resources("static")
        }
    }
}
