package com.viaversion.aas.handler.state;

import com.google.gson.JsonPrimitive;
import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.aas.codec.packet.configuration.ConfigurationDisconnect;
import com.viaversion.aas.codec.packet.configuration.FinishConfig;
import com.viaversion.aas.handler.HandlerUtilKt;
import com.viaversion.aas.handler.MinecraftHandler;
import com.viaversion.viaversion.api.protocol.packet.State;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.jetbrains.annotations.NotNull;

public class ConfigurationState implements ConnectionState {
	private boolean kickedByServer = false;
	@NotNull
	@Override
	public State getState() {
		return State.CONFIGURATION;
	}

	@Override
	public void handlePacket(@NotNull MinecraftHandler handler, @NotNull ChannelHandlerContext ctx, @NotNull Packet packet) {
		if (packet instanceof FinishConfig) handleFinish(handler, (FinishConfig) packet);
		if (packet instanceof ConfigurationDisconnect) handleDisconnect(handler, (ConfigurationDisconnect) packet);
		HandlerUtilKt.forward(handler, ReferenceCountUtil.retain(packet), false);
	}

	private void handleDisconnect(MinecraftHandler handler, ConfigurationDisconnect packet) {
		kickedByServer = true;
		UtilKt.getMcLogger().debug(
				"{} disconnected on config: {}",
				handler.endRemoteAddress.toString(),
				packet.getMsg()
		);
	}

	private void handleFinish(MinecraftHandler handler, FinishConfig packet) {
		handler.getData().setState(new PlayState());
	}

	@Override
	public boolean getLogDcInfo() {
		return true;
	}

	@Override
	public boolean getKickedByServer() {
		return kickedByServer;
	}

	@Override
	public void disconnect(@NotNull MinecraftHandler handler, @NotNull String msg) {
		ConnectionState.DefaultImpls.disconnect(this, handler, msg);
		var packet = new ConfigurationDisconnect();
		packet.setMsg(new JsonPrimitive("[VIAaaS] §c$msg"));
		UtilKt.writeFlushClose(handler.getData().getFrontChannel(), packet, false);
	}

	@Override
	public void onInactivated(@NotNull MinecraftHandler handler) {
		ConnectionState.DefaultImpls.onInactivated(this, handler);
	}

	@Override
	public void start(@NotNull MinecraftHandler handler) {
	}
}
