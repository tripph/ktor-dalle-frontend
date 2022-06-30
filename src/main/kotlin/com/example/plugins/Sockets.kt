package com.example.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    val handler = Handler()

    routing {
        webSocket("/prompt") {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val request: Handler.FeedEntry = Json.decodeFromString(text)
                        handler.handle(request)
                    }
                    else -> {
                        println("Frame: " + frame.frameType)
                    }
                }
            }
        }
        webSocket("/feed") {
            handler.subscribeToFeed(this)
        }
    }
}
class Handler {

    private val persistenceFile = File("state.json")
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = Collections.synchronizedList<DefaultWebSocketSession>(mutableListOf())
    private val feed = mutableListOf<FeedEntry>()
    init {
        restorePersistedState()
    }
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
        }
    }
    data class Piece(val text: String, var results: List<String> = emptyList(), val ts: LocalDateTime = LocalDateTime.now())

    suspend fun handle(req: FeedEntry) {
        logger.info("handle: $req")
        val piece = Piece(req.prompt!!)
        val images = try { work(piece) } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        logger.info("got ${images.size} images for $req")
        req.images = images
        val response = Json.encodeToString(req)
        feed.add(req)
        persistState()
        sessions.forEach { session ->
            session.outgoing.trySend(Frame.Text(response))
        }
    }
    fun persistState() {
        val state = Json.encodeToString(feed)
        logger.info("persistState()")
        persistenceFile.writeText(state)
    }
    fun restorePersistedState() {
        if(persistenceFile.length() == 0L) {
            logger.info("state is empty")
            return
        }
        val state = Json.decodeFromString<List<FeedEntry>>(persistenceFile.readText())
        state.filter{!it.images.isNullOrEmpty()}.forEach {
            feed.add(it)
        }
    }

    suspend fun work(piece: Piece): List<String> {
        val response: String = client.post("https://backend.craiyon.com/generate") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(GenerateReq(piece.text)))
        }.bodyAsText()
        val r: GenerateResponse = Json.decodeFromString(response)
        return r.images
    }

    suspend fun subscribeToFeed(session: DefaultWebSocketServerSession) {
//        session.closeReason.invokeOnCompletion {
//            this.sessions.remove(session)
//        }
        logger.info("subscribeToFeed(), {}", session)
        this.feed
            .filter{!it.images.isNullOrEmpty()}
            .forEach{ feedEntry ->
            val response = Json.encodeToString(feedEntry)
            logger.info("subscribeToFeed() sending feed entry from {}", feedEntry.ts)
            session.outgoing.send(Frame.Text(response))
        }
        this.sessions.add(session)
        try {
            for(frame in session.incoming) {
                logger.info("received message from feed, ignoring")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }finally {
            logger.info("Closing session")
        }
    }

    @kotlinx.serialization.Serializable
    data class GenerateReq(
        val prompt: String? = null
    )
    @kotlinx.serialization.Serializable
    data class GenerateResponse(
        val version: String? = null,
        var images: List<String>
    )


    @kotlinx.serialization.Serializable
    data class FeedEntry(
        val username: String? = null,
        val prompt: String? = null,
        var images: List<String>? = null, // if null or empty, request errored
        var ts: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    )
}
const val LOADING_IMG = """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"""
