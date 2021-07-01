package com.viaversion.aas

import com.viaversion.aas.command.VIAaaSConsole
import com.viaversion.aas.command.ViaAspirinCommand
import com.viaversion.aas.platform.*
import com.viaversion.aas.protocol.registerAspirinProtocols
import com.viaversion.aas.web.ViaWebApp
import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.data.MappingDataLoader
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.gerrygames.viarewind.api.ViaRewindConfigImpl
import io.ktor.application.*
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.io.IoBuilder
import java.io.File


fun main(args: Array<String>) {
    try {
        setupSystem()
        printSplash()
        AspirinServer.generateCert()
        initVia()
        AspirinServer.listenPorts(args)

        AspirinServer.mainStartSigal()
        AspirinServer.addShutdownHook()

        Thread { VIAaaSConsole.start() }.start()

        AspirinServer.waitStopSignal()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        AspirinServer.finish()
    }
}

private fun setupSystem() {
    // Stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/Velocity.java
    if (System.getProperty("io.netty.allocator.maxOrder") == null) {
        System.setProperty("io.netty.allocator.maxOrder", "9")
    }
    // https://logging.apache.org/log4j/2.x/log4j-jul/index.html
    if (System.getProperty("java.util.logging.manager") == null) {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
    }
    // Also stolen from Velocity
    System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream())
    System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream())
}

private fun printSplash() {
    println(
        """\\        // // //\\  =>     //||     //||   /=====/ PROXY
              | \\      // // //  \\       // ||    // ||  //
              |  \\    // // //====\\     //==||   //==||  \====\   ${AspirinServer.version}
              |   \\  // // //      \\   //   ||  //   ||      //
              |<=  \\// // //        \\ //    || //    || /====/""".trimMargin()
    )
}


private fun initVia() {
    Via.init(
        ViaManagerImpl.builder()
            .injector(AspirinInjector())
            .loader(AspirinLoader())
            .commandHandler(ViaAspirinCommand)
            .platform(AspirinPlatform).build()
    )
    MappingDataLoader.enableMappingsCache()
    (Via.getManager() as ViaManagerImpl).init()
    AspirinRewind().init(ViaRewindConfigImpl(File("config/viarewind.yml")))
    AspirinBackwards().init(File("config/viabackwards"))

    ProtocolVersion.register(-2, "AUTO")
    registerAspirinProtocols()
}

fun Application.mainWeb() {
    ViaWebApp(AspirinServer.viaWebServer).apply { main() }
}
