package com.viaversion.aas

import com.viaversion.aas.command.CommandManager
import com.viaversion.aas.command.VIAaaSConsole
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.platform.AspirinPlatform
import com.viaversion.aas.web.ViaWebApp
import com.viaversion.viaaprilfools.ViaAprilFoolsPlatformImpl
import com.viaversion.viabackwards.ViaBackwardsPlatformImpl
import com.viaversion.viarewind.ViaRewindPlatformImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.protocol.version.VersionType
import io.ktor.server.application.*
import net.raphimc.vialegacy.ViaLegacyPlatformImpl
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.io.IoBuilder


fun main(args: Array<String>) {
    try {
        setupSystem()
        printStartingSplash()
        AspirinServer.checkForUpdatesStart()
        AspirinServer.generateCert()
        val commandManager = CommandManager()
        initVia(commandManager)
        AspirinServer.logNativesInfo()
        AspirinServer.listenPorts(args)
        AspirinServer.logStartupSeconds()

        AspirinServer.mainStartSignal()
        AspirinServer.addShutdownHook()

        val console = VIAaaSConsole(commandManager)
        Thread { console.start() }.start()

        AspirinServer.waitStopSignal()
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {
        AspirinServer.finish()
    }
}

private fun setupSystem() {
    // https://logging.apache.org/log4j/2.x/log4j-jul/index.html
    if (System.getProperty("java.util.logging.manager") == null) {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
    }
    // Also stolen from Velocity
    System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream())
    System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream())
}

private fun printStartingSplash() {
    println("VIAaaS ${AspirinServer.version} is starting")
}

val AUTO = ProtocolVersion(VersionType.SPECIAL, -2, -1, "AUTO", null)

private fun initVia(cmdManager: CommandManager) {
    val platform = AspirinPlatform(cmdManager)

    platform.initVia {
        ViaBackwardsPlatformImpl()
        ViaRewindPlatformImpl()
        ViaAprilFoolsPlatformImpl()
        ViaLegacyPlatformImpl()
        Via.getManager().configurationProvider.register(VIAaaSConfig)
    }

    ProtocolVersion.register(AUTO)
}

fun Application.mainWeb() {
    ViaWebApp(AspirinServer.viaWebServer).apply { main() }
}
