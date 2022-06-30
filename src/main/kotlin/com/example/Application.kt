package com.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.server.application.*

import kotlinx.serialization.json.Json


fun main() {
    embeddedServer(Netty, port = 8088, host = "0.0.0.0") {
        configureRouting()
        configureTemplating()
        configureSockets()

    }.start(wait = true)
}
