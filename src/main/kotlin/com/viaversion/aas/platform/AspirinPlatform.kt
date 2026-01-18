package com.viaversion.aas.platform

import com.viaversion.aas.AspirinServer
import com.viaversion.aas.command.CommandManager
import com.viaversion.aas.command.ViaAspirinCommand
import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.libs.gson.JsonObject
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform
import io.ktor.server.application.*
import java.io.File
import java.util.logging.Logger

class AspirinPlatform(val cmdManager: CommandManager) : UserConnectionViaVersionPlatform(File("config/viaversion")) {

    fun initVia(enableListener: Runnable) {
        val viaCommand = ViaAspirinCommand()
        cmdManager.registerCommand(viaCommand, "viaversion", "viaver", "vvcloud", "vvaas", "vvaspirin", "viaaas")

        ViaManagerImpl.initAndLoad(
            this,
            AspirinInjector(),
            viaCommand,
            AspirinPlatformLoader(),
            enableListener
        )
    }

    override fun getDump(): JsonObject {
        return JsonObject().also {
            it.add("versions", JsonObject().also {
                it.addProperty("jvm", System.getProperty("java.version"))
                it.addProperty("ktor", Application::class.java.`package`.implementationVersion)
            })
        }
    }

    override fun createLogger(name: String): Logger {
        return Logger.getLogger(name)
    }

    override fun getPlatformName() = "VIAaaS"
    override fun getPlatformVersion(): String = AspirinServer.version

}
