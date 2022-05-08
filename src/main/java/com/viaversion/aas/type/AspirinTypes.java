package com.viaversion.aas.type;

import com.viaversion.aas.util.SignableProperty;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.ArrayType;

public enum AspirinTypes {;
	public static Type<String> OPTIONAL_STRING = new OptionalStringType();
	public static Type<SignableProperty> SIGNABLE_PROPERTY = new SignablePropertyType();
	public static Type<SignableProperty[]> SIGNABLE_PROPERTY_ARRAY = new ArrayType<>(SIGNABLE_PROPERTY);
}
