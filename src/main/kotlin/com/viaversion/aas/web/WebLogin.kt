package com.viaversion.aas.web

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.viaversion.aas.*
import com.viaversion.aas.util.StacklessException
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.future.await
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import kotlin.math.absoluteValue

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.send("""{"action": "ad_minecraft_id_login"}""")
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        val obj = Gson().fromJson(msg, JsonObject::class.java)

        when (obj.getAsJsonPrimitive("action").asString) {
            "offline_login" -> {
                if (!sha512Hex(msg.toByteArray(Charsets.UTF_8)).startsWith("00000")) throw StacklessException("PoW failed")
                if ((obj.getAsJsonPrimitive("date").asLong - System.currentTimeMillis()).absoluteValue
                    > Duration.ofMinutes(2).toMillis()) {
                    throw StacklessException("Invalid PoW date")
                }
                val username = obj.get("username").asString.trim()
                val uuid = generateOfflinePlayerUuid(username)

                val token = webClient.server.generateToken(uuid)
                webClient.ws.send(JsonObject().also {
                    it.addProperty("action", "login_result")
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
                        ?: webClient.server.usernameIdCache.get(mcIdUser).await()
                        ?: throw StacklessException("Failed to get UUID from minecraft.id")

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
                    webLogger.info("Listen fail: ${webClient.id}")
                }
                webClient.ws.send(response.toString())
            }
            "unlisten_login_requests" -> {
                val uuid = UUID.fromString(obj.getAsJsonPrimitive("uuid").asString)
                webLogger.info("Unlisten: ${webClient.id}: $uuid")
                val response = JsonObject().also {
                    it.addProperty("action", "unlisten_login_requests_result")
                    it.addProperty("uuid", uuid.toString())
                    it.addProperty("success", webClient.unlistenId(uuid))
                }
                webClient.ws.send(response.toString())
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
