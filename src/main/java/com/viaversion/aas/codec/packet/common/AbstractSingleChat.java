package com.viaversion.aas.codec.packet.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.Tag;
import com.viaversion.viaversion.util.ComponentUtil;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class AbstractSingleChat implements Packet {
	private JsonElement msg;
	private Tag msgTag;

	public JsonElement getMsg() {
		return msg;
	}

	public void setMsg(JsonElement msg) {
		this.msg = msg;
	}

	public Tag getMsgTag() {
		return msgTag;
	}

	public void setMsgTag(Tag msgTag) {
		this.msgTag = msgTag;
	}

	public JsonElement getMsgAsJson() {
		if (msg != null) return msg;
		if (msgTag != null) return JsonParser.parseString(ComponentUtil.tagToJson(this.msgTag).toString());
		return null;
	}

	public void setMsgForVersion(JsonElement msg, ProtocolVersion protocolVersion) {
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_3)) {
			this.msgTag = ComponentUtil.jsonToTag(com.viaversion.viaversion.libs.gson.JsonParser.parseString(msg.toString()));
		} else {
			this.msg = msg;
		}
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		if (protocolVersion.olderThan(ProtocolVersion.v1_20_3)) {
			msg = JsonParser.parseString(Type.STRING.read(byteBuf));
		} else {
			msgTag = Type.TAG.read(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		if (protocolVersion.olderThan(ProtocolVersion.v1_20_3)) {
			Type.STRING.write(byteBuf, msg.toString());
		}
	}


}
