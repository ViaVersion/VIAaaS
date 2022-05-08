package com.viaversion.aas.type;

import com.viaversion.aas.util.SignableProperty;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;

public class SignablePropertyType extends Type<SignableProperty> {
	protected SignablePropertyType() {
		super(SignableProperty.class);
	}

	@Override
	public SignableProperty read(ByteBuf buffer) throws Exception {
		String key = Type.STRING.read(buffer);
		String value = Type.STRING.read(buffer);
		String signature = AspirinTypes.OPTIONAL_STRING.read(buffer);
		return new SignableProperty(key, value, signature);
	}

	@Override
	public void write(ByteBuf buffer, SignableProperty object) throws Exception {
		Type.STRING.write(buffer, object.getKey());
		Type.STRING.write(buffer, object.getValue());
		AspirinTypes.OPTIONAL_STRING.write(buffer, object.getSignature());
	}
}
