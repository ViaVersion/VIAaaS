package com.viaversion.aas.command;

import com.viaversion.viaversion.api.command.ViaCommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum VersionCommand implements Command {
	INSTANCE;

	@NotNull
	@Override
	public String getInfo() {
		return "Alias of 'viaver viaaas'";
	}

	@NotNull
	@Override
	public List<String> suggest(@NotNull ViaCommandSender sender, @NotNull String alias, @NotNull List<String> args) {
		return List.of();
	}

	@Override
	public void execute(@NotNull ViaCommandSender sender, @NotNull String alias, @NotNull List<String> args) {
		ViaAspirinCommand.INSTANCE.execute(sender, alias, List.of("viaaas"));
	}
}
