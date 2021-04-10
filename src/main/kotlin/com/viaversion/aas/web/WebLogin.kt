package com.viaversion.aas.web

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.viaversion.aas.generateOfflinePlayerUuid
import com.viaversion.aas.httpClient
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.util.StacklessException
import com.viaversion.aas.webLogger
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import java.net.URLEncoder
import java.util.*

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.send("""{"action": "ad_minecraft_id_login"}""")
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        val obj = Gson().fromJson(msg, JsonObject::class.java)

        when (obj.getAsJsonPrimitive("action").asString) {
            "offline_login" -> {
                // todo add some spam check
                val username = obj.get("username").asString.trim()
                val uuid = generateOfflinePlayerUuid(username)

                val token = webClient.server.generateToken(uuid)
                webClient.ws.send(JsonObject().also {
                    it.addProperty("login_result", true)
                    it.addProperty("success", true)
                    it.addProperty("username", username)
                    it.addProperty("uuid", uuid.toString())
                    it.addProperty("token", token)
                }.toString())

                webLogger.info("Token gen: ${webClient.id}: offline $username $uuid")
            }
            "minecraft_id_login" -> {
                val username = obj.get("username").asString
                val code = obj.get("code").asString

                val check = httpClient.submitForm<JsonObject>(
                    "https://api.minecraft.id/gateway/verify/${URLEncoder.encode(username, Charsets.UTF_8)}",
                    formParameters = parametersOf("code", code),
                )

                if (check.getAsJsonPrimitive("valid").asBoolean) {
                    val mcIdUser = check.get("username").asString
                    val uuid = check.get("uuid")?.asString?.let { parseUndashedId(it.replace("-", "")) }
                        ?: webClient.server.usernameIdCache.get(mcIdUser)

                    val token = webClient.server.generateToken(uuid)
                    webClient.ws.send(JsonObject().also {
                        it.addProperty("action", "login_result")
                        it.addProperty("success", true)
                        it.addProperty("username", mcIdUser)
                        it.addProperty("uuid", uuid.toString())
                        it.addProperty("token", token)
                    }.toString())

                    webLogger.info("Token gen: ${webClient.id}: $mcIdUser $uuid")
                } else {
                    webClient.ws.send(JsonObject().also {
                        it.addProperty("action", "login_result")
                        it.addProperty("success", false)
                    }.toString())
                    webLogger.info("Token gen fail: ${webClient.id}: $username")
                }
            }
            "listen_login_requests" -> {
                val token = obj.getAsJsonPrimitive("token").asString
                val user = webClient.server.checkToken(token)
                val response = JsonObject().also {
                    it.addProperty("action", "listen_login_requests_result")
                    it.addProperty("token", token)
                }
                if (user != null && webClient.listenId(user)) {
                    response.addProperty("success", true)
                    response.addProperty("user", user.toString())
                    webLogger.info("Listen: ${webClient.id}: $user")
                } else {
                    response.addProperty("success", false)
                    webLogger.info("Token fail: ${webClient.id}")
                }
                webClient.ws.send(response.toString())
            }
            "unlisten_login_requests" -> {
                val uuid = UUID.fromString(obj.getAsJsonPrimitive("uuid").asString)
                webClient.unlistenId(uuid)
            }
            "session_hash_response" -> {
                val hash = obj.get("session_hash").asString
                webClient.server.sessionHashCallbacks.getIfPresent(hash)?.complete(null)
            }
            else -> throw StacklessException("invalid action!")
        }

        webClient.ws.flush()
    }

    override suspend fun disconnected(webClient: WebClient) {
        webClient.listenedIds.forEach { webClient.unlistenId(it) }
    }

    override suspend fun onException(webClient: WebClient, exception: java.lang.Exception) {
    }
}
