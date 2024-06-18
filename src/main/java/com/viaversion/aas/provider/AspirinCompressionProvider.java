package com.viaversion.aas.provider;

import com.viaversion.aas.handler.HandlerUtilKt;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;

import java.util.Objects;

public class AspirinCompressionProvider extends CompressionProvider {
	@Override
	public void handlePlayCompression(UserConnection user, int threshold) {
		HandlerUtilKt.setCompression(Objects.requireNonNull(user.getChannel()), threshold);
	}
}
