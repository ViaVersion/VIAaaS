package com.viaversion.aas.web

import com.google.common.net.HostAndPort
import com.google.common.primitives.Ints
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.viaversion.aas.*
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.future.await
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import kotlin.math.absoluteValue

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.sendSerialized(JsonObject().also {
            it.addProperty("action", "ad_login_methods")
        })
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        val obj = JsonParser.parseString(msg) as JsonObject

        when (obj["action"].asString) {
            "offline_login" -> handleOfflineLogin(webClient, msg, obj)
            "minecraft_id_login" -> handleMcIdLogin(webClient, obj)
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
        webClient.listenedIds.forEach { webClient.unlistenId(it) }
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

    private suspend fun handleOfflineLogin(webClient: WebClient, msg: String, obj: JsonObject) {
        if (!sha512Hex(msg.toByteArray(Charsets.UTF_8)).startsWith("00000")) throw StacklessException("PoW failed")
        if ((obj["date"].asLong - System.currentTimeMillis())
                .absoluteValue > Duration.ofSeconds(20).toMillis()
        ) {
            throw StacklessException("Invalid PoW date")
        }
        val username = obj["username"].asString.trim()
        val uuid = generateOfflinePlayerUuid(username)

        val token = webClient.server.generateToken(uuid, username)
        webClient.ws.sendSerialized(loginSuccessJson(username, uuid, token))

        webLogger.info("Token gen: {}: offline {} {}", webClient.id, username, uuid)
    }

    private suspend fun handleMcIdLogin(webClient: WebClient, obj: JsonObject) {
        val username = obj["username"].asString
        val code = obj["code"].asString

        val check = AspirinServer.httpClient.submitForm(
            "https://api.minecraft.id/gateway/verify/${URLEncoder.encode(username, Charsets.UTF_8)}",
            formParameters = parametersOf("code", code),
        ).body<JsonObject>()

        if (check["valid"].asBoolean) {
            val mcIdUser = check["username"].asString
            val uuid = check["uuid"]?.asString?.let { parseUndashedId(it.replace("-", "")) }
                ?: webClient.server.usernameToIdCache[mcIdUser].await()
                ?: throw StacklessException("Failed to get UUID from minecraft.id")

            val token = webClient.server.generateToken(uuid, mcIdUser)
            webClient.ws.sendSerialized(loginSuccessJson(mcIdUser, uuid, token))

            webLogger.info("Token gen: {}: {} {}", webClient.id, mcIdUser, uuid)
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
        webClient.server.sessionHashCallbacks.getIfPresent(hash)?.complete(Unit)
    }

    private fun handleParametersResponse(webClient: WebClient, obj: JsonObject) {
        val callback = UUID.fromString(obj["callback"].asString)
        webClient.server.addressCallbacks[callback].complete(
            AddressInfo(
                backVersion = obj["version"].asString.let {
                    Ints.tryParse(it)?.let { ProtocolVersion.getProtocol(it) }
                        ?: ProtocolVersion.getClosest(it) ?: AUTO
                },
                backHostAndPort = HostAndPort.fromParts(obj["host"].asString, obj["port"].asInt),
                frontOnline = obj["frontOnline"].asString.toBooleanStrictOrNull(),
                backName = obj["backName"]?.asString
            )
        )
    }

    private suspend fun handleSaveAccessToken(webClient: WebClient, obj: JsonObject) {
        val accessToken = obj["mc_access_token"].asString
        val profile = AspirinServer.httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
            header("Authorization", "Bearer $accessToken")
        }.body<JsonObject>()
        val uuid = parseUndashedId(profile["id"].asString)
        webClient.server.minecraftAccessTokens.put(uuid, accessToken)
        webLogger.info("Received token: {} {}", webClient.id, uuid)
    }
}
