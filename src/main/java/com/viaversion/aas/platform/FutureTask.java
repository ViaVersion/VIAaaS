package com.viaversion.aas.platform;

import com.viaversion.viaversion.api.platform.PlatformTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

public class FutureTask implements PlatformTask<Future<?>> {
	private final Future<?> object;

	public FutureTask(@NotNull Future<?> object) {
		this.object = object;
	}

	@Override
	public void cancel() {
		object.cancel(false);
	}
}
