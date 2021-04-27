package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

interface Command {
    val info: String
    fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String>
    fun execute(sender: ViaCommandSender, alias: String, args: List<String>)
}