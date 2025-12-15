package com.example.coupgame

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val baseUrl = "http://k.scip.su:8000"

    suspend fun createGame(player: NetworkPlayer): NetworkGame {
        return client.post("$baseUrl/matchmaking/create") {
            contentType(ContentType.Application.Json)
            setBody(player)
        }.body<NetworkGame>()
    }

    suspend fun joinGame(gameId: String, player: NetworkPlayer): NetworkGame {
        return client.post("$baseUrl/matchmaking/join/$gameId") {
            contentType(ContentType.Application.Json)
            setBody(player)
        }.body<NetworkGame>()
    }

    suspend fun getGameStatus(gameId: String): NetworkGame {
        return client.get("$baseUrl/matchmaking/status/$gameId").body<NetworkGame>()
    }

    suspend fun startGame(gameId: String): NetworkGame {
        return client.post("$baseUrl/matchmaking/start/$gameId").body<NetworkGame>()
    }

    suspend fun performAction(gameId: String, action: NetworkGameAction): NetworkGame {
        return client.post("$baseUrl/game/$gameId/action") {
            contentType(ContentType.Application.Json)
            setBody(action)
        }.body<NetworkGame>()
    }

    suspend fun challenge(gameId: String, body: ChallengeBody): NetworkGame {
        return client.post("$baseUrl/game/$gameId/challenge") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<NetworkGame>()
    }

    suspend fun pass(gameId: String, body: PassBody): NetworkGame {
        return client.post("$baseUrl/game/$gameId/pass") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<NetworkGame>()
    }

    suspend fun loseInfluence(gameId: String, body: LoseInfluenceBody): NetworkGame {
        return client.post("$baseUrl/game/$gameId/lose_influence") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<NetworkGame>()
    }

    suspend fun exchangeCards(gameId: String, body: ExchangeBody): NetworkGame {
        return client.post("$baseUrl/game/$gameId/exchange") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<NetworkGame>()
    }
}
