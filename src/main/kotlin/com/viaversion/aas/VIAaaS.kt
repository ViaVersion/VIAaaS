package com.viaversion.aas

import com.viaversion.aas.command.VIAaaSConsole
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.platform.AspirinAprilFools
import com.viaversion.aas.platform.AspirinBackwards
import com.viaversion.aas.platform.AspirinLegacy
import com.viaversion.aas.platform.AspirinPlatform
import com.viaversion.aas.platform.AspirinRewind
import com.viaversion.aas.protocol.registerAspirinProtocols
import com.viaversion.aas.web.ViaWebApp
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.io.IoBuilder


fun main(args: Array<String>) {
    try {
        setupSystem()
        printSplash()
        CoroutineScope(Job()).launch { viaaasLogger.info("{}", AspirinServer.updaterCheckMessage()) }
        AspirinServer.generateCert()
        initVia()
        AspirinServer.listenPorts(args)

        AspirinServer.mainStartSignal()
        AspirinServer.addShutdownHook()

        Thread { VIAaaSConsole.start() }.start()

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

private fun printSplash() {
    println("VIAaaS ${AspirinServer.version}")
}

val autoProtocolId = -2

private fun initVia() {
    AspirinPlatform.initVia {
        AspirinRewind().init()
        AspirinBackwards().init()
        AspirinAprilFools().init()
        AspirinLegacy().init()
        Via.getManager().configurationProvider.register(VIAaaSConfig)
    }

    ProtocolVersion.register(autoProtocolId, "AUTO")
    registerAspirinProtocols()
}

fun Application.mainWeb() {
    ViaWebApp(AspirinServer.viaWebServer).apply { main() }
}
