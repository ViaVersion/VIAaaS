package com.viaversion.aas.platform

import com.viaversion.viaversion.api.platform.PlatformTask
import java.util.concurrent.Future

class AspirinTask(val obj: Future<*>) : PlatformTask<Any> {
    override fun getObject(): Any = obj

    override fun cancel() {
        obj.cancel(false);
    }
}