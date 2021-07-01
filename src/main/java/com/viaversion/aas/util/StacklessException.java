package com.viaversion.aas.util;

public class StacklessException extends RuntimeException {
	public StacklessException() {
		super();
	}

	public StacklessException(String message) {
		super(message);
	}

	public StacklessException(String message, Throwable cause) {
		super(message, cause);
	}

	public StacklessException(Throwable cause) {
		super(cause);
	}

	protected StacklessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	@Override
	public String toString() {
		return "StacklessException: " + getMessage();
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}
