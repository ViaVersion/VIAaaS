package com.viaversion.aas.handler.state;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.aas.codec.packet.status.StatusPing;
import com.viaversion.aas.codec.packet.status.StatusPong;
import com.viaversion.aas.handler.MinecraftHandler;
import com.viaversion.viaversion.api.protocol.packet.State;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

public class StatusKicked implements ConnectionState {
	@NotNull
	@Override
	public State getState() {
		return State.STATUS;
	}

	@Override
	public void handlePacket(@NotNull MinecraftHandler handler, @NotNull ChannelHandlerContext ctx, @NotNull Packet packet) {
		if (packet instanceof StatusPing) handlePing(handler, (StatusPing) packet);
	}

	public void handlePing(@NotNull MinecraftHandler handler, @NotNull StatusPing packet) {
		var pong = new StatusPong();
		pong.setNumber(packet.getNumber());
		UtilKt.writeFlushClose(handler.getData().getFrontChannel(), pong, false);
	}

	@Override
	public boolean getLogDcInfo() {
		return false;
	}

	@Override
	public void disconnect(@NotNull MinecraftHandler handler, @NotNull String msg) {
		ConnectionState.DefaultImpls.disconnect(this, handler, msg);
	}

	@Override
	public void onInactivated(@NotNull MinecraftHandler handler) {
		ConnectionState.DefaultImpls.onInactivated(this, handler);
	}

	@Override
	public void start(@NotNull MinecraftHandler handler) {
		UtilKt.setAutoRead(handler.getData().getFrontChannel(), true);
	}

	@Override
	public boolean getKickedByServer() {
		return ConnectionState.DefaultImpls.getKickedByServer(this);
	}
}
