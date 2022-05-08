package com.viaversion.aas.util;

import javax.annotation.Nullable;

public class SignableProperty {
	private String key;
	private String value;
	@Nullable
	private String signature;

	public SignableProperty(String key, String value, @Nullable String signature) {
		this.key = key;
		this.value = value;
		this.signature = signature;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Nullable
	public String getSignature() {
		return signature;
	}

	public void setSignature(@Nullable String signature) {
		this.signature = signature;
	}
}
