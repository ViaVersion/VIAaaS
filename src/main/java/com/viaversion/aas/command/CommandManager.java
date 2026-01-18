package com.viaversion.aas.command;

import com.google.common.collect.HashMultimap;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;

public class CommandManager {
	private final HashMap<String, Command> aliasToCommand = new HashMap<>();
	private final HashMultimap<Command, String> commandToAlias = HashMultimap.create();

	{
		registerCommand(new HelpCommand(this), "help", "?");
		registerCommand(new EndCommand(), "end", "stop");
		registerCommand(new ReloadCommand(this), "reload");
		registerCommand(new ListCommand(), "list");
		registerCommand(new VersionCommand(), "version", "ver");
	}

	public void registerCommand(Command cmd, String... aliases) {
		for (String alias : aliases) {
			aliasToCommand.put(alias, cmd);
			commandToAlias.put(cmd, alias);
		}
	}

	@Nullable
	public Command getCommand(String name) {
		return aliasToCommand.get(name);
	}

	public Set<Command> getCommands() {
		return Set.copyOf(commandToAlias.keySet());
	}

	public Set<String> getCommandNames() {
		return Set.copyOf(aliasToCommand.keySet());
	}

	public Set<String> getAliases(Command cmd) {
		return Set.copyOf(commandToAlias.get(cmd));
	}
}
