package com.github.creeper123123321.viaaas.command

import com.github.creeper123123321.viaaas.handler.CloudMinecraftHandler
import com.github.creeper123123321.viaaas.runningServer
import com.github.creeper123123321.viaaas.viaaasLogger
import com.github.creeper123123321.viaaas.viaaasVer
import net.minecrell.terminalconsole.SimpleTerminalConsole
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import java.util.*

class VIAaaSConsole : SimpleTerminalConsole(), ViaCommandSender {
    val commands = hashMapOf<String, (MutableList<String>?, String, Array<String>) -> Unit>()
    override fun isRunning(): Boolean = runningServer

    init {
        commands["stop"] = { suggestion, _, _ -> if (suggestion == null) this.shutdown() }
        commands["end"] = commands["stop"]!!
        commands["viaversion"] = { suggestion, _, args ->
            if (suggestion == null) {
                Via.getManager().commandHandler.onCommand(this, args)
            } else {
                suggestion.addAll(Via.getManager().commandHandler.onTabComplete(this, args))
            }
        }
        commands["viaver"] = commands["viaversion"]!!
        commands["vvcloud"] = commands["viaversion"]!!
        commands["help"] = { suggestion, _, _ ->
            if (suggestion == null) sendMessage(commands.entries.groupBy { it.value }.entries.joinToString(", ") {
                it.value.joinToString("/") { it.key }
            })
        }
        commands["?"] = commands["help"]!!
        commands["ver"] = { suggestion, _, _ ->
            if (suggestion == null) sendMessage(viaaasVer)
        }
        commands["list"] = { suggestion, _, _ ->
            if (suggestion == null) {
                sendMessage("List of player connections: ")
                Via.getPlatform().connectionManager.connections.forEach {
                    val backAddr = it.channel?.remoteAddress()
                    val pVer = it.protocolInfo?.protocolVersion?.let {
                        ProtocolVersion.getProtocol(it)
                    }
                    val backName = it.protocolInfo?.username
                    val backVer = it.protocolInfo?.serverProtocolVersion?.let {
                        ProtocolVersion.getProtocol(it)
                    }
                    val pAddr =
                        it.channel?.pipeline()?.get(CloudMinecraftHandler::class.java)?.other?.remoteAddress()
                    val pName = it.channel?.pipeline()?.get(CloudMinecraftHandler::class.java)?.data?.frontName
                    sendMessage("$pAddr $pVer ($pName) -> $backVer ($backName) $backAddr")
                }
            }
        }
    }

    override fun buildReader(builder: LineReaderBuilder): LineReader {
        // Stolen from Velocity
        return super.buildReader(builder.appName("VIAaaS").completer { _, line, candidates ->
            try {
                val cmdArgs = line.line().substring(0, line.cursor()).split(" ")
                val alias = cmdArgs[0]
                val args = cmdArgs.filterIndexed { i, _ -> i > 0 }
                if (cmdArgs.size == 1) {
                    candidates.addAll(commands.keys.filter { it.startsWith(alias, ignoreCase = true) }
                        .map { Candidate(it) })
                } else {
                    val cmd = commands[alias.toLowerCase()]
                    if (cmd != null) {
                        val suggestions = mutableListOf<String>()
                        cmd(suggestions, alias, args.toTypedArray())
                        candidates.addAll(suggestions.map(::Candidate))
                    }
                }
            } catch (e: Exception) {
                sendMessage("Error completing command: $e")
            }
        })
    }

    override fun runCommand(command: String) {
        val cmd = command.split(" ")
        try {
            val alias = cmd[0].toLowerCase()
            val args = cmd.subList(1, cmd.size).toTypedArray()
            val runnable = commands[alias]
            if (runnable == null) {
                sendMessage("unknown command, try 'help'")
            } else {
                runnable(null, alias, args)
            }
        } catch (e: Exception) {
            sendMessage("Error running command: $e")
        }
    }

    override fun shutdown() {
        viaaasLogger.info("Shutting down...")
        runningServer = false
    }


    override fun sendMessage(p0: String) {
        LoggerFactory.getLogger(this.name).info(p0)
    }

    override fun hasPermission(p0: String): Boolean = true
    override fun getUUID(): UUID = UUID.fromString(name)
    override fun getName(): String = "VIAaaS Console"
}