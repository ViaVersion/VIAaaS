package com.viaversion.aas.provider;

import com.viaversion.aas.handler.MinecraftHandler;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.base.BaseVersionProvider;

public class AspirinVersionProvider extends BaseVersionProvider {
	@Override
	public int getClosestServerProtocol(UserConnection connection) throws Exception {
		var ver = connection.getChannel().pipeline().get(MinecraftHandler.class).getData().getBackServerVer();
		if (ver != null) return ver;
		return super.getClosestServerProtocol(connection);
	}
}
