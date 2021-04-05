package com.viaversion.aas.web

import com.viaversion.aas.generateOfflinePlayerUuid
import com.viaversion.aas.httpClient
import com.viaversion.aas.webLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.viaversion.aas.util.StacklessException
import io.ktor.client.request.forms.*
import io.ktor.features.*
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
                webClient.ws.send(
                    """{"action": "login_result", "success": true,
                        | "username": "$username", "uuid": "$uuid", "token": "$token"}""".trimMargin()
                )

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
                    val uuid = webClient.server.usernameIdCache.get(mcIdUser)

                    val token = webClient.server.generateToken(uuid)
                    webClient.ws.send(
                        """{"action": "login_result", "success": true,
                        | "username": "$mcIdUser", "uuid": "$uuid", "token": "$token"}""".trimMargin()
                    )

                    webLogger.info("Token gen: ${webClient.id}: $mcIdUser $uuid")
                } else {
                    webClient.ws.send("""{"action": "login_result", "success": false}""")
                    webLogger.info("Token gen fail: ${webClient.id}: $username")
                }
            }
            "listen_login_requests" -> {
                val token = UUID.fromString(obj.getAsJsonPrimitive("token").asString)
                val user = webClient.server.loginTokens.getIfPresent(token)
                if (user != null && webClient.listenId(user)) {
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": true, "user": "$user"}""")
                    webLogger.info("Listen: ${webClient.id}: $user")
                } else {
                    webClient.server.loginTokens.invalidate(token)
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": false}""")
                    webLogger.info("Token fail: ${webClient.id}")
                }
            }
            "unlisten_login_requests" -> {
                val uuid = UUID.fromString(obj.getAsJsonPrimitive("uuid").asString)
                webClient.unlistenId(uuid)
            }
            "invalidate_token" -> {
                val token = UUID.fromString(obj.getAsJsonPrimitive("token").asString)
                webClient.server.loginTokens.invalidate(token)
            }
            "session_hash_response" -> {
                val hash = obj.get("session_hash").asString
                webClient.server.pendingSessionHashes.getIfPresent(hash)?.complete(null)
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
