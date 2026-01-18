package com.viaversion.aas.web

import com.auth0.jwt.JWT
import com.google.common.net.HostAndPort
import com.google.common.primitives.Ints
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.viaversion.aas.*
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.websocket.*
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.abs

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.sendSerialized(JsonObject().also {
            it.addProperty("action", "ad_login_methods")
            it.addProperty("challenge", webClient.challenge)
        })
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        val obj = JsonParser.parseString(msg) as JsonObject

        when (obj["action"].asString) {
            "offline_login" -> handleOfflineLogin(webClient, msg, obj)
            "temp_code_login" -> handleTempCodeLogin(webClient, obj)
            "listen_login_requests" -> handleListenLogins(webClient, obj)
            "unlisten_login_requests" -> handleUnlisten(webClient, obj)
            "session_hash_response" -> handleSessionResponse(webClient, obj)
            "parameters_response" -> handleParametersResponse(webClient, obj)
            "save_access_token" -> handleSaveAccessToken(webClient, obj)
            else -> throw StacklessException("invalid action!")
        }

        webClient.ws.flush()
    }

    override suspend fun disconnected(webClient: WebClient) {
        webClient.unlistenAll()
    }

    override suspend fun onException(webClient: WebClient, exception: Throwable) {
    }

    private fun loginSuccessJson(username: String, uuid: UUID, token: String): JsonObject {
        return JsonObject().also {
            it.addProperty("action", "login_result")
            it.addProperty("success", true)
            it.addProperty("username", username)
            it.addProperty("uuid", uuid.toString())
            it.addProperty("token", token)
        }
    }

    private fun loginNotSuccess(): JsonObject {
        return JsonObject().also {
            it.addProperty("action", "login_result")
            it.addProperty("success", false)
        }
    }

    private fun validateUsername(name: String) {
        if (name.length > 16) throw StacklessException("Username is too long")
    }

    private suspend fun handleOfflineLogin(webClient: WebClient, msg: String, obj: JsonObject) {
        if (!sha512Hex(msg.encodeToByteArray()).startsWith("00000")) throw StacklessException("PoW failed")
        if (abs(obj["date"].asLong - System.currentTimeMillis()) > Duration.ofSeconds(20).toMillis()) {
            throw StacklessException("Invalid PoW date")
        }
        if (obj["challenge"].asString != webClient.challenge) {
            throw StacklessException("Invalid challenge")
        }
        val username = obj["username"].asString
        validateUsername(username)
        val uuid = generateOfflinePlayerUuid(username)

        val token = webClient.server.generateToken(uuid, username)
        webClient.ws.sendSerialized(loginSuccessJson(username, uuid, token))

        webLogger.info("Token gen: {}: offline {} {}", webClient.id, username, uuid)
    }

    private suspend fun handleTempCodeLogin(webClient: WebClient, obj: JsonObject) {
        val username = obj["username"].asString
        validateUsername(username)
        val code = obj["code"].asString

        val check = webClient.server.checkTempCode(username, code)

        if (check != null) {
            webClient.server.invalidateTempCode(username)
            val verifiedUsername = check.username
            val uuid = check.id

            val token = webClient.server.generateToken(uuid, verifiedUsername)
            webClient.ws.sendSerialized(loginSuccessJson(verifiedUsername, uuid, token))

            webLogger.info("Token gen: {}: temp code {} {}", webClient.id, verifiedUsername, uuid)
        } else {
            webClient.ws.sendSerialized(loginNotSuccess())
            webLogger.info("Token gen fail: {}: {}", webClient.id, username)
        }
    }

    private suspend fun handleListenLogins(webClient: WebClient, obj: JsonObject) {
        val token = obj["token"].asString
        val user = webClient.server.parseToken(token)
        val response = JsonObject().also {
            it.addProperty("action", "listen_login_requests_result")
            it.addProperty("token", token)
        }
        if (user != null && webClient.listenId(user.id)) {
            response.addProperty("success", true)
            response.addProperty("user", user.id.toString())
            response.addProperty("username", user.name)
            webLogger.info("Listen: {}: {}", webClient.id, user)
        } else {
            response.addProperty("success", false)
            webLogger.info("Listen fail: {}", webClient.id)
        }
        webClient.ws.sendSerialized(response)
    }

    private suspend fun handleUnlisten(webClient: WebClient, obj: JsonObject) {
        val uuid = UUID.fromString(obj["uuid"].asString)
        webLogger.info("Unlisten: {}: {}", webClient.id, uuid)
        val response = JsonObject().also {
            it.addProperty("action", "unlisten_login_requests_result")
            it.addProperty("uuid", uuid.toString())
            it.addProperty("success", webClient.unlistenId(uuid))
        }
        webClient.ws.sendSerialized(response)
    }

    private fun handleSessionResponse(webClient: WebClient, obj: JsonObject) {
        val hash = obj["session_hash"].asString
        webClient.server.completeSessionHashCallback(hash)
    }

    private fun parseVersion(versionString: String): ProtocolVersion {
        if (versionString.length > 20) throw IllegalArgumentException("Version is too long")
        return Ints.tryParse(versionString)?.let { ProtocolVersion.getProtocol(it) }
            ?: ProtocolVersion.getClosest(versionString) ?: AUTO
    }

    private fun parseHostAndPort(host: String, port: Int): HostAndPort {
        if (host.length > 255) {
            throw IllegalArgumentException("Host too long")
        }
        return HostAndPort.fromParts(host, port)
    }

    private fun handleParametersResponse(webClient: WebClient, obj: JsonObject) {
        val callback = UUID.fromString(obj["callback"].asString)
        val backName = obj["backName"]?.asString
        if (backName != null) validateUsername(backName)

        webClient.server.completeAddressCallback(
            callback, AddressInfo(
                backVersion = parseVersion(obj["version"].asString),
                backHostAndPort = parseHostAndPort(obj["host"].asString, obj["port"].asInt),
                frontOnline = obj["frontOnline"].asString.toBooleanStrictOrNull(),
                backName = backName
            )
        )
    }

    private fun verifyTokenTime(notBefore: Instant, expiration: Instant) {
        val now = Instant.now()
        if (now > expiration) {
            throw IllegalArgumentException("mc access token has expired")
        }
        if (now < notBefore) {
            throw IllegalArgumentException("mc access token notBefore is in the future")
        }
    }

    private suspend fun handleSaveAccessToken(webClient: WebClient, obj: JsonObject) {
        val accessToken = obj["mc_access_token"].asString
        val decodedToken = JWT.decode(accessToken)
        verifyTokenTime(decodedToken.notBeforeAsInstant, decodedToken.expiresAtAsInstant)
        val expectedId = UUID.fromString(decodedToken.getClaim("profiles").asMap()["mc"].toString())

        val profile = AspirinServer.httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
            header("Authorization", "Bearer $accessToken")
        }.body<JsonObject>()
        val uuid = parseUndashedUuid(profile["id"].asString)
        if (uuid != expectedId) {
            throw IllegalStateException("expected $expectedId == $uuid")
        }
        val username = profile["name"].asString

        webClient.server.addAccessToken(uuid, accessToken)
        webLogger.info("Received token: {} {} ({})", webClient.id, uuid, username)

        val response = JsonObject().also {
            it.addProperty("action", "save_access_token_result")
            it.addProperty("username", username)
            it.addProperty("uuid", uuid.toString())
            it.addProperty("success", true)
        }
        webClient.ws.sendSerialized(response)
    }
}
