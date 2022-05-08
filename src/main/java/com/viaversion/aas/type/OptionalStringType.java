package com.viaversion.aas.type;

import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;

public class OptionalStringType extends Type<String> {
	protected OptionalStringType() {
		super(String.class);
	}

	@Override
	public String read(ByteBuf buffer) throws Exception {
		return buffer.readBoolean() ? Type.STRING.read(buffer) : null;
	}

	@Override
	public void write(ByteBuf buffer, String object) throws Exception {
		if (object == null) {
			buffer.writeBoolean(false);
		} else {
			buffer.writeBoolean(true);
			Type.STRING.write(buffer, object);
		}
	}
}
