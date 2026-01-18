package com.viaversion.aas.command

import com.viaversion.aas.AspirinServer
import com.viaversion.viaversion.api.command.ViaCommandSender
import net.minecrell.terminalconsole.SimpleTerminalConsole
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.slf4j.LoggerFactory
import java.util.*

class VIAaaSConsole(val cmdManager: CommandManager) : SimpleTerminalConsole(), ViaCommandSender {
    override fun isRunning(): Boolean = !AspirinServer.wasStopSignalFired()

    override fun buildReader(builder: LineReaderBuilder): LineReader {
        // Stolen from Velocity
        return super.buildReader(builder.appName("VIAaaS").completer { _, line, candidates ->
            try {
                val cmdArgs = line.line().substring(0, line.cursor()).split(" ")
                val alias = cmdArgs[0]
                val args = cmdArgs.filterIndexed { i, _ -> i > 0 }
                if (cmdArgs.size == 1) {
                    candidates.addAll(cmdManager.commandNames
                        .filter { it.startsWith(alias, ignoreCase = true) }
                        .map { Candidate(it) })
                } else {
                    val command = cmdManager.getCommand(alias.lowercase())
                    if (command != null) {
                        candidates.addAll(command.suggest(this, alias, args).map(::Candidate))
                    }
                }
            } catch (e: Exception) {
                sendMessage("Error completing command: $e")
            }
        })
    }

    override fun runCommand(commandLine: String) {
        val cmd = commandLine.split(" ")
        try {
            val alias = cmd[0].lowercase()
            val args = cmd.subList(1, cmd.size)
            val command = cmdManager.getCommand(alias)
            if (command == null) {
                sendMessage("Unknown command, try 'help'")
            } else {
                command.execute(this, alias, args)
            }
        } catch (e: Exception) {
            sendMessage("Error running command: $e")
        }
    }

    public override fun shutdown() {
        cmdManager.getCommand("end")!!.execute(this, "end", listOf())
    }

    override fun sendMessage(p0: String) {
        LoggerFactory.getLogger(this.name).info(p0)
    }

    override fun hasPermission(p0: String): Boolean = true
    override fun getUUID(): UUID = UUID.nameUUIDFromBytes(name.toByteArray())
    override fun getName(): String = "VIAaaS Console"
}
