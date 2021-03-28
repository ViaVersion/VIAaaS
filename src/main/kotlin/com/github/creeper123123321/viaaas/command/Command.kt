package com.github.creeper123123321.viaaas.command

import us.myles.ViaVersion.api.command.ViaCommandSender

interface Command {
    val info: String
    fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String>
    fun execute(sender: ViaCommandSender, alias: String, args: List<String>)
}